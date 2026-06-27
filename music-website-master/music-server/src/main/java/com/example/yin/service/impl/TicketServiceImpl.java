package com.example.yin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.yin.common.R;
import com.example.yin.constant.TicketRedisKey;
import com.example.yin.mapper.ConcertMapper;
import com.example.yin.mapper.ConsumerMapper;
import com.example.yin.mapper.TicketOrderMapper;
import com.example.yin.mapper.TicketTierMapper;
import com.example.yin.model.domain.Concert;
import com.example.yin.model.domain.Consumer;
import com.example.yin.model.domain.TicketOrder;
import com.example.yin.model.domain.TicketTier;
import com.example.yin.model.request.TicketBuyRequest;
import com.example.yin.service.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class TicketServiceImpl implements TicketService {
    private static final Logger log = LoggerFactory.getLogger(TicketServiceImpl.class);
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    // StringRedisTemplate 存的是纯字符串，Lua 脚本才认
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ConsumerMapper consumerMapper;
    @Autowired
    private ConcertMapper concertMapper;
    @Autowired
    private TicketTierMapper ticketTierMapper;
    @Autowired
    private TicketOrderMapper ticketOrderMapper;
    // Jackson 序列化工具，用于将订单对象与 JSON 互转存入 Redis
    @Autowired
    private ObjectMapper  objectMapper;
    // ==================== 购票核心流程 ====================
    /**
     * 购票（高并发抢票核心入口）
     *
     * 整体流程 7 步：
     *   ① 参数校验（演唱会是否存在、是否在售票中、票档是否合法）
     *   ② SETNX 用户去重（同一用户同一票档只能买一次）
     *   ③ Lua 脚本原子扣减 Redis 库存
     *   ④ 生成订单号 + 二维码 token
     *   ⑤ 订单信息写入 Redis 缓存
     *   ⑥ @Async 异步写入 MySQL（不阻塞用户响应）
     *   ⑦ 返回订单号、二维码 token、价格
     *
     * 防超卖机制（三位一体）：
     *   第一道：Lua 脚本保证 GET + DECR 原子执行（本步骤 ③）
     *   第二道：MySQL uk_user_tier 唯一索引兜底（异步写入时生效）
     *   第三道：定时任务每 5 分钟对账校正 Redis 库存
     */
    @Override
    public R buy(TicketBuyRequest request) {
//        log.info("===== 购票开始: userId={}, concertId={}, tierId={} =====",
//                request.getUserId(), request.getConcertId(), request.getTierId());

        // ===== 步骤 ①：参数校验 =====
        if (request.getUserId() == null || request.getConcertId() == null
                || request.getTierId() == null) {
           // log.warn("购票失败: 参数不完整, request={}", request);
            return R.error("参数不完整");
        }

        Concert concert = concertMapper.selectById(request.getConcertId());
        if (concert == null) {
           // log.warn("购票失败: 演唱会不存在, concertId={}", request.getConcertId());
            return R.error("演唱会不存在");
        }
        if (concert.getStatus() != 2) {
//            log.warn("购票失败: 演唱会状态不允许购买, concertId={}, status={}",
//                    request.getConcertId(), concert.getStatus());
            return R.error("当前不可购票");
        }
       // log.info("步骤①通过: concert={}, status={}", concert.getTitle(), concert.getStatus());

        TicketTier tier = ticketTierMapper.selectById(request.getTierId());
        if (tier == null || !tier.getConcertId().equals(request.getConcertId())) {
//            log.warn("购票失败: 票档不存在或归属不对, tierId={}, tierConcertId={}",
//                    request.getTierId(), tier != null ? tier.getConcertId() : "null");
            return R.error("票档不存在");
        }
       // log.info("步骤①通过: tierName={}, price={}, totalStock={}",
         //       tier.getTierName(), tier.getPrice(), tier.getTotalStock());
        // ===== 步骤 ②：SETNX 用户去重（先于扣款，防止重复扣音符）=====
        String purchasedKey = TicketRedisKey.purchasedKey(
                request.getUserId(), request.getTierId());
        Boolean isFirst = stringRedisTemplate.opsForValue()
                .setIfAbsent(purchasedKey, "1",
                        TicketRedisKey.PURCHASED_TTL_SECONDS, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(isFirst)) {
            return R.error("您已购买过该票档");
        }

        // ===== 音符支付：SETNX 通过后再扣款 =====
        if ("yinbi".equals(request.getPayMethod())) {
            int rows = consumerMapper.debitYinbi(request.getUserId(), tier.getPrice());
            if (rows == 0) {
                // 扣款失败，释放去重标记让用户可重试
                stringRedisTemplate.delete(purchasedKey);
                return R.error("音符余额不足");
            }
        }
        // 扫码支付 "qrcode"：不扣余额，直接走后续下单流程
        // ===== 步骤 ③：Lua 脚本原子扣库存 =====
        String stockKey = TicketRedisKey.stockKey(request.getTierId());
        //log.info("步骤③: 库存 key={}", stockKey);

        // 打印 Redis 中当前库存值
        Object currentStock = stringRedisTemplate.opsForValue().get(stockKey);
        //log.info("步骤③: Redis 当前库存原始值 = {}", currentStock);

        // 兜底：如果 Redis 库存 key 不存在或值无效，从 DB 初始化
        if (currentStock == null) {
            if (tier.getTotalStock() != null) {
                //log.warn("步骤③: key 不存在，从 DB 初始化 stock={}", tier.getTotalStock());
                stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(tier.getTotalStock()));
            } else {
                //log.error("步骤③: key 不存在且 DB totalStock 也为 null, tierId={}", request.getTierId());
            }
        } else {
            // 检查值是否可转为数字
            try {
                Long.parseLong(currentStock.toString());
            } catch (NumberFormatException e) {
                if (tier.getTotalStock() != null) {
                    stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(tier.getTotalStock()));
                   // log.info("步骤③: 已用 DB 值覆盖无效 Redis 值, newValue={}", tier.getTotalStock());
                }
            }
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(TicketRedisKey.LUA_DECR_STOCK);
        script.setResultType(Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(stockKey));
       // log.info("步骤③: Lua 脚本执行结果 result={} (0=库存不足, 1=扣减成功, null=异常)", result);

        if (result == null || result == 0) {
            Object stockAfter = stringRedisTemplate.opsForValue().get(stockKey);
            //log.warn("购票失败: 库存扣减失败, result={}, stockAfter={}, key={}",
              //      result, stockAfter, stockKey);
            stringRedisTemplate.delete(purchasedKey);
            //回滚音符(如果之前扣了)
            if ("yinbi".equals(request.getPayMethod())) {
                consumerMapper.rechargeYinbi(request.getUserId(),tier.getPrice());
            }
            return R.error("已售罄");
        }

        // ===== 步骤 ④~⑦：生成订单、缓存、异步写库、返回 =====
        String orderNo = generateOrderNo();
        String qrCodeToken = UUID.randomUUID().toString().replace("-", "");

        // ===== 步骤 ⑤：订单信息写入 Redis 缓存 =====
        // 为什么先写 Redis 再异步写 MySQL？
        //   - Redis 写入约 1ms，不阻塞用户响应
        //   - 用户支付完立刻跳转订单页，直接读 Redis 缓存，速度极快
        //   - 即使异步写 MySQL 失败，Redis 里有完整数据，可后续修复
        // 注意：这里没有设 TTL 过期，实际应设 7 天（演唱会结束后订单仍需可查）
        try {
            Map<String, Object> orderCache = new HashMap<>();
            orderCache.put("orderNo", orderNo);
            orderCache.put("userId", request.getUserId());
            orderCache.put("concertId", request.getConcertId());
            orderCache.put("concertTitle", concert.getTitle());
            orderCache.put("tierId", request.getTierId());
            orderCache.put("tierName", tier.getTierName());
            orderCache.put("price", tier.getPrice());
            orderCache.put("idCard", request.getIdCard());
            orderCache.put("phone", request.getPhone());
            orderCache.put("qrCodeToken", qrCodeToken);
            orderCache.put("payStatus", 1);       // 模拟支付：直接标记已支付
            orderCache.put("verifyStatus", 0);    // 0=未核验
            orderCache.put("venue", concert.getVenue());
            orderCache.put("showTime", concert.getShowTime());

            String json = objectMapper.writeValueAsString(orderCache);
            stringRedisTemplate.opsForValue().set(
                    TicketRedisKey.orderKey(orderNo), json,
                    TicketRedisKey.ORDER_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Redis 缓存写入失败不影响主流程（订单数据仍在后续异步写入 MySQL）
            log.error("写入订单缓存失败", e);
        }

        // ===== 步骤 ⑥：异步写入 MySQL =====
        // @Async 方法在独立线程池中执行，不阻塞当前请求线程
        asyncInsertOrder(request, orderNo, qrCodeToken, tier);

        // ===== 步骤 ⑦：返回购票结果 =====
        Map<String, Object> response = new HashMap<>();
        response.put("orderNo", orderNo);
        response.put("qrCodeToken", qrCodeToken);
        response.put("price", tier.getPrice());
        log.info("===== 购票成功: orderNo={}, userId={}, tierId={}, price={} =====",
                orderNo, request.getUserId(), request.getTierId(), tier.getPrice());
        return R.success("购票成功", response);
    }
    /**
     * 异步写入 MySQL 订单记录
     * 用 @Async 注解，Spring 会将此方法提交到线程池异步执行
     * 主线程不等待 DB IO 完成，直接返回购票成功给用户（响应时间 ~5ms）
     *
     * 失败回滚策略：
     *   如果 MySQL INSERT 失败，必须：
     *   1. INCR 归还 Redis 库存 → 避免库存"凭空消失"
     *   2. DEL 释放已购标记 → 让用户可以重新购买
     *   （不抛异常给用户，因为用户已经拿到"购票成功"的响应了）
     */
    @Async("ticketOrderExecutor")  //指定线程池异步执行
    public void asyncInsertOrder(TicketBuyRequest request, String orderNo,
                                 String qrCodeToken, TicketTier tier) {
        try {
            TicketOrder order = new TicketOrder();
            order.setOrderNo(orderNo);
            order.setUserId(request.getUserId());
            order.setConcertId(request.getConcertId());
            order.setTierId(request.getTierId());
            order.setTierName(tier.getTierName());
            order.setPrice(tier.getPrice());
            order.setIdCard(request.getIdCard());
            order.setPhone(request.getPhone());
            order.setQrCodeToken(qrCodeToken);
            order.setPayStatus(1);       // 模拟已支付
            order.setVerifyStatus(0);    // 未核验
            ticketOrderMapper.insert(order);
        } catch (Exception e) {
            // MySQL 写入失败是小概率事件，但必须补偿
            log.error("异步插入订单失败, orderNo={}", orderNo, e);
            // 回滚 1：INCR 归还库存（+1 恢复扣减的那一张）
            String stockKey = TicketRedisKey.stockKey(request.getTierId());
            stringRedisTemplate.opsForValue().increment(stockKey);
            // 回滚 2：DEL 释放已购标记（用户可以重试购买）
            String purchasedKey = TicketRedisKey.purchasedKey(
                    request.getUserId(), request.getTierId());
            stringRedisTemplate.delete(purchasedKey);
            // 注意：Redis 里的订单缓存没删 — 保留作为问题排查线索，
            // 定时对账任务会根据 DB 数据校正最终状态
        }
    }
    // ==================== 订单查询 ====================

    /**
     * 查询订单详情（Cache-Aside 模式）
     * 优先从 Redis 读取（内存级速度，~1ms），未命中才查 MySQL（~10ms）
     * 适用于：用户下单后跳转、核验扫码查询
     *
     * 为什么缓存未命中后不把 DB 数据回写到 Redis？
     *   - 新创建的订单在步骤 ⑤ 已经写了缓存
     *   - 这里查 DB 只发生在缓存已过期/丢失的场景（低概率）
     *   - 不重复写缓存避免引入缓存淘汰策略的复杂度
     */

    @Override
    public R getOrder(String orderNo) {
        //第一层：redis缓存
        String cacheKey = TicketRedisKey.orderKey(orderNo);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                //将json字符串反序列化为Map
                Map<String,Object> orderInfo = objectMapper.readValue(
                        cached,
                        objectMapper.getTypeFactory()
                                .constructMapType(Map.class, String.class, Object.class));
                return R.success("成功", orderInfo);
            } catch (Exception e) {
                log.warn("订单缓存反序列化失败: {}", e.getMessage());
                // 反序列化失败不 return，继续走 DB 查询
            }
        }
        // 第二层：MySQL 数据库
        QueryWrapper<TicketOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("order_no", orderNo);  // 注意：DB 列名是下划线风格 order_no
        TicketOrder order = ticketOrderMapper.selectOne(wrapper);
        if (order == null) {
            return R.error("订单不存在");
        }

        // 手动组装返回数据，只暴露前端需要的字段
        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("userId", order.getUserId());
        result.put("concertId", order.getConcertId());
        result.put("tierName", order.getTierName());
        result.put("price", order.getPrice());
        result.put("idCard", order.getIdCard());
        result.put("phone", order.getPhone());
        result.put("qrCodeToken", order.getQrCodeToken());
        result.put("payStatus", order.getPayStatus());
        result.put("verifyStatus", order.getVerifyStatus());
        return R.success("成功", result);
    }

    /**
     * 查询用户所有购票订单
     * 调用 Mapper 的联表查询（关联 consumer 表获取用户名），返回订单列表
     */
    @Override
    public R getMyOrders(Integer userId) {
        List<Map<String, Object>> orders = ticketOrderMapper.selectOrdersByUser(userId);
        return R.success("成功", orders);
    }

    // ==================== 工具方法 ====================

    /**
     * 生成订单号
     * 格式：TK + 13位毫秒时间戳 + 4位随机数
     * 示例：TK16847412345670042
     * 碰撞概率：同一毫秒内最多 10000 种可能，实际业务足够
     */
    private String generateOrderNo() {
        return "TK" + System.currentTimeMillis()
                + String.format("%04d", (int) (Math.random() * 10000));
    }
    /**
     * 管理员删除订单 — 全量清理
     *
     * 清理步骤（顺序有讲究）：
     *   ① 先查 MySQL 获取订单信息（tierId, userId, orderNo, verifyStatus）
     *   ② 校验订单状态：已核验的票不能删（防止恶意退款后白嫖入场）
     *   ③ DELETE MySQL 记录
     *   ④ 归还 Redis 库存 INCR（放在 DELETE 之后，库存"多不退"总比"少卖了"好）
     *   ⑤ 释放 Redis 已购标记 DEL（让用户可以重新购买该票档）
     *   ⑥ 删除 Redis 订单缓存 DEL
     *
     * 并发安全分析：
     *   - 步骤 ③~⑥ 不是原子的，但后果可控：
     *       * 如果 ③ 成功但 ④ 失败 → 库存少 1，定时对账任务 5 分钟内修复
     *       * 如果 ③④⑤ 成功但 ⑥ 失败 → 残留一个订单缓存，无业务影响
     *   - 不需要分布式锁：库存"少 1"的影响面远小于"多 1（超卖）"
     */
    @Override
    public R cancelOrder(Integer orderId) {
        // ① 查询订单
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        if (order == null) {
            return R.error("订单不存在");
        }
        // ② 已核验的票不允许删除（防止白嫖）
        if (order.getVerifyStatus() != null && order.getVerifyStatus() == 1) {
            return R.error("该票已核验入场，无法取消");
        }
        // ③ 删除 MySQL 订单记录
        int deleted = ticketOrderMapper.deleteById(orderId);
        if (deleted == 0) {
            return R.error("删除失败");
        }
        // ④ 归还 Redis 库存
        try {
            String stockKey = TicketRedisKey.stockKey(order.getTierId());
            stringRedisTemplate.opsForValue().increment(stockKey);
        } catch (Exception e) {
            log.error("归还库存失败: orderId={}, tierId={}", orderId, order.getTierId(), e);
            // 不阻断流程 —— 定时对账任务会修复
        }
        // ⑤ 释放用户已购标记
        try {
            String purchasedKey = TicketRedisKey.purchasedKey(
                    order.getUserId(), order.getTierId());
            stringRedisTemplate.delete(purchasedKey);
        } catch (Exception e) {
            log.error("释放已购标记失败: orderId={}, userId={}, tierId={}",
                    orderId, order.getUserId(), order.getTierId(), e);
        }
        // ⑥ 删除订单缓存
        try {
            String orderKey = TicketRedisKey.orderKey(order.getOrderNo());
            stringRedisTemplate.delete(orderKey);
        } catch (Exception e) {
            log.error("删除订单缓存失败: orderNo={}", order.getOrderNo(), e);
        }
        log.info("订单删除成功: orderNo={}, tierId={}, stock+1", order.getOrderNo(), order.getTierId());
        return R.success("删除成功，库存已归还");
    }
    /**
     * 用户自行取消订单
     * 比管理员删除多一步归属校验
     */
    @Override
    public R cancelOrderByUser(Integer orderId, Integer userId) {
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        if (order == null) {
            return R.error("订单不存在");
        }
        // 归属校验：用户只能取消自己的订单
        if (!order.getUserId().equals(userId)) {
            return R.error("无权操作");
        }
        return cancelOrder(orderId);
    }
}
