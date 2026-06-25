package com.example.yin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.yin.common.R;
import com.example.yin.constant.TicketRedisKey;
import com.example.yin.mapper.ConcertMapper;
import com.example.yin.mapper.TicketTierMapper;
import com.example.yin.model.domain.Concert;
import com.example.yin.model.domain.TicketTier;
import com.example.yin.model.request.ConcertRequest;
import com.example.yin.service.ConcertService;
import com.example.yin.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
@Slf4j
@Service
public class ConcertServiceImpl extends ServiceImpl<ConcertMapper, Concert> implements ConcertService {
@Autowired
private TicketTierMapper ticketTierMapper;   //购票
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    /**
     * 演唱会列表（分页 + 状态筛选）
     * 排序规则：开售时间倒序（即将开售的在前），同开售时间则按创建时间升序
     */
    @Override
    public R listConcerts(Integer page, Integer size, Integer status,boolean excludeExpired) {

        LambdaQueryWrapper<Concert> wrapper = new LambdaQueryWrapper<>();
        ///status为null时表示全部，不添加条件
        if (status != null) {
            wrapper.eq(Concert::getStatus, status);
        }
        if (excludeExpired){
            //售票时间已过，且状态不是"已结束（4）"的，视为过期，不再展示
            wrapper.and(w -> w.isNull(Concert::getSaleEndTime)
                    .or().gt(Concert::getSaleEndTime, new Date())
                    .or().eq(Concert::getStatus,4));

        }
        wrapper.orderByDesc(Concert::getSaleStartTime);
        wrapper.orderByAsc(Concert::getCreateTime);
        Page<Concert> result = baseMapper.selectPage(new Page<>(page, size), wrapper);
        Map<String, Object> data = new HashMap<>();
        data.put("records",result.getRecords());
        data.put("total",result.getTotal());

        return R.success("成功",data);
    }
    /**
     * 演唱会详情（含票档 + 实时剩余库存）
     * 库存数据从 Redis 读取而非 MySQL 计算，因为：
     * - 抢票高峰期库存扣减发生在 Redis，MySQL 订单是异步写入的
     * - Redis 的值才是"此刻还剩多少张"的实时真相，DB 数据有延迟
     * - 查 DB 需要 COUNT(order)，高并发下压力大
     * 兜底：Redis key 不存在时（刚启动/缓存过期），回退取 MySQL total_stock
     */
    @Override
    public R getDetail(Integer concertId) {

        Concert concert = baseMapper.selectById(concertId);
        if (concert == null) {
            return R.error("演唱会不存在");

        }
        //查询该演唱会下的所有票档
        List<TicketTier> tiers = ticketTierMapper.selectList(
                new LambdaQueryWrapper<TicketTier>().eq(TicketTier::getConcertId, concertId)
        );
        //组装票档列表，从redis补充实时库存
        List<Map<String, Object>> tierList = new ArrayList<>();
        for (TicketTier tier : tiers) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", tier.getId());
            item.put("tierName", tier.getTierName());
            item.put("price", tier.getPrice());
            item.put("totalStock", tier.getTotalStock());
            //从redis读取剩余库存（key 格式：ticket:stock:{tierId}）
            String stockKey = TicketRedisKey.stockKey(tier.getId());
            String stockStr = stringRedisTemplate.opsForValue().get(stockKey);
            Integer remaining = parseStock(stockStr, tier.getTotalStock());
            item.put("remainingStock", remaining != null ? remaining : 0);
            tierList.add(item);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("concert", concert);
        data.put("tiers", tierList);
        return R.success("成功",data);
    }


// ==================== 管理端接口 ====================

    /**
     * 发布演唱会（含票档 + 初始化 Redis 库存）
     * 事务性：concert 表和 ticket_tier 表同时写入
     * 库存预热：每个票档创建后立即在 Redis 中初始化库存计数器
     * 用 setIfAbsent（SETNX）防止覆盖运行中数据
     */
    @Override
    @Transactional  //保证concert和ticket_tier两张表的写入原子性
    public R addConcert(ConcertRequest request) {

        Concert concert = new Concert();
        BeanUtils.copyProperties(request, concert);  //把一个对象里的字段值，自动复制到另一个对象里，不用一个个写 set 方法
        baseMapper.insert(concert);
        // MyBatis-Plus 自动将数据库生成的自增 ID 回填到 concert.id
        Integer concertId = concert.getId();

        if (request.getTiers() != null) {
            for (ConcertRequest.TierRequest tr : request.getTiers()) {
                TicketTier tier = new TicketTier();
                tier.setConcertId(concertId);
                tier.setTierName(tr.getTierName());
                tier.setPrice(tr.getPrice());
                tier.setTotalStock(tr.getTotalStock());
                ticketTierMapper.insert(tier);

                // 初始化 Redis 库存（SETNX 防覆盖）
                // 为什么用 setIfAbsent 而不是 set：
                // 如果 Redis 里已有该 key（比如之前发布过的旧数据），直接覆盖会把运行中的库存清零
                String stockKey = TicketRedisKey.stockKey(tier.getId());
                if (tr.getTotalStock() != null) {
                    stringRedisTemplate.opsForValue()
                            .setIfAbsent(stockKey, String.valueOf(tr.getTotalStock()));
                }
            }
        }
            return R.success("演唱会发布成功");
    }

    /**
     * 修改演唱会信息
     * 只更新 concert 表，不动票档和库存（票档一旦开售不应随意修改）
     */
    @Override
    @Transactional
    public R updateConcert(ConcertRequest request) {
        Concert concert = baseMapper.selectById(request.getId());
        if (concert == null) {
            return R.error("演唱会不存在");
        }
        BeanUtils.copyProperties(request, concert);
        baseMapper.updateById(concert);
        return R.success("演唱会信息已更新");
    }

    /**
     * 演唱会状态变更（上架/下架/预售/售票中/售罄/结束）
     * 状态为 2（售票中）时同步写入 Redis 开售标记，供其他模块快速判断
     * 其他状态时删除标记
     */
    @Override
    public R updateStatus(Integer concertId, Integer status) {
        Concert concert = baseMapper.selectById(concertId);
        if (concert == null) {
            return R.success("演唱会不存在");
        }
        concert.setStatus(status);
        baseMapper.updateById(concert);
        //同步到 Redis 开售标记（key: ticket:on_sale:{concertId}）
        if (status == 2){
            // 状态=2 表示"售票中"，写入标记供抢票接口和前端倒计时判断
            stringRedisTemplate.opsForValue()
                    .set(TicketRedisKey.onSaleKey(concertId),"1");
        }else {
            //其他状态下（下架/预告/售罄/结束）删除标记
            stringRedisTemplate.delete(TicketRedisKey.onSaleKey(concertId));
        }
        return R.success("状态已更新");
    }

    @Override
    public R uploadCover(MultipartFile file, Integer concertId) {
        Concert concert = baseMapper.selectById(concertId);
        if (concert == null) {
            return R.error("演唱会不存在");
        }
        try {
            String picPath = FileUtils.saveLocally(file, "img/concertPic");
            concert.setCoverPic(picPath);
            baseMapper.updateById(concert);
            return R.success("封面上传成功", picPath);
        } catch (Exception e) {
            log.error("封面上传失败", e);
            return R.error("封面上传失败: " + e.getMessage());
        }
    }

    /**
     * 安全解析 Redis 库存值
     * 处理三种异常情况：
     * 1. key 不存在 → 返回 DB 的 totalStock
     * 2. 值被 Java 序列化污染（旧数据） → 返回 DB 的 totalStock
     * 3. DB 也是 null → 返回 0
     */
    private Integer parseStock(String stockStr, Integer dbStock) {
        if (stockStr == null) {
            return dbStock;
        }
        try {
            int num = Integer.parseInt(stockStr);
            return Math.max(num, 0); // 库存不为负
        } catch (NumberFormatException e) {
            log.warn("Redis 库存值不是有效数字，可能被旧序列化数据污染，回退到 DB: {}",
                    stockStr != null ? stockStr.substring(0, Math.min(20, stockStr.length())) : "null");
            return dbStock != null ? dbStock : 0;
        }
    }
}
