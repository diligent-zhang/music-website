package com.example.yin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.yin.common.R;
import com.example.yin.constant.TicketRedisKey;
import com.example.yin.mapper.ConcertMapper;
import com.example.yin.mapper.TicketOrderMapper;
import com.example.yin.mapper.TicketTierMapper;
import com.example.yin.mapper.TicketVerificationMapper;
import com.example.yin.model.domain.Concert;
import com.example.yin.model.domain.TicketOrder;
import com.example.yin.model.domain.TicketTier;
import com.example.yin.model.domain.TicketVerification;
import com.example.yin.service.VerifyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;

/**
 * 核验服务实现
 * 现场工作人员扫描用户入场二维码 → 查询订单信息 → 确认核验
 */
@Slf4j
@Service
public class VerifyServiceImpl implements VerifyService {
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;
    @Autowired
    private TicketOrderMapper ticketOrderMapper;
    @Autowired
    private TicketVerificationMapper verificationMapper;
    @Autowired
    private ConcertMapper concertMapper;
    @Autowired
    private TicketTierMapper ticketTierMapper;
    @Autowired
    private ObjectMapper objectMapper;
    /**
     * 查询核验信息
     * 通过 qrCodeToken 反查订单，展示关键信息供工作人员人工核对
     * 已核验的票不可重复查询，防止一票多用
     */
    @Override
    public R getVerifyInfo(String qrCodeToken) {
        //通过token查看订单（直接在DB中查，保证数据实时性）
        TicketOrder order = findByQrToken(qrCodeToken);
        if (order == null) {
            return R.error("无效的二维码");
        }
        //已核验的票直接拦截
        if (order.getVerifyStatus()==1){
            return R.error("该票已核验入场");
        }
        Concert concert = concertMapper.selectById(order.getConcertId());
        TicketTier tier = ticketTierMapper.selectById(order.getTierId());
        Map<String,Object> data = new HashedMap<>();
        data.put("oderNo",order.getOrderNo());
        data.put("concertTitle", concert != null ? concert.getTitle() : "未知");
        data.put("tierName", order.getTierName());
        // 脱敏展示，保护用户隐私
        data.put("idCard", maskIdCard(order.getIdCard()));
        data.put("phone", maskPhone(order.getPhone()));
        data.put("qrCodeToken", qrCodeToken);
        return R.success("成功", data);
    }
    /**
     * 确认核验入场
     * @Transactional 保证两个操作原子执行：
     *   1. 更新 ticket_order.verify_status = 1
     *   2. 插入 ticket_verification 核验记录
     * 同步更新 Redis 缓存中的订单状态
     */
    @Override
    @Transactional
    public R confirm(String qrCodeToken, Integer operatorId) {
        TicketOrder order = findByQrToken(qrCodeToken);
        if (order == null) {
            return R.error("无效的二维码");
        }
        //二次校验：防止并发重复校验(数据库兜底)
        if (order.getVerifyStatus()==1){
            return R.error("该票已核验，请勿重复操作");
        }
        // 更新订单核验状态
        order.setVerifyStatus(1);
        order.setVerifyTime(new Date());
        ticketOrderMapper.updateById(order);

        // 写入核验记录，用于审计追溯
        TicketVerification tv = new TicketVerification();
        tv.setOrderId(order.getId());
        tv.setOperatorId(operatorId);
        tv.setVerifyTime(new Date());
        verificationMapper.insert(tv);

        // 同步更新 Redis 缓存中的订单状态
        // 如果缓存不存在则跳过（不影响核验结果，仅影响后续查询性能）
        try {
            String cacheKey = TicketRedisKey.orderKey(order.getOrderNo());
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                        //Redis 里存的是JSON 字符串，现在把它转成 Map，方便修改字段。
                        Map<String, Object> map = objectMapper.readValue(
                                cached.toString(),
                        objectMapper.getTypeFactory()
                                .constructMapType(Map.class,String.class,Object.class));

                                map.put("verifyStatus",1);
                                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(map));

            }
        } catch (Exception e) {
            //缓存更新失败不影响核验结果，仅记录日志
            log.error("更新核验缓存失败",e);
        }
        return R.success("核验通过，请入场");
    }
    /**
     * 通过二维码 token 查询订单
     * 利用 MySQL 的 uk_qr_token 唯一索引快速定位
     */
    private TicketOrder findByQrToken(String qrCodeToken) {
        QueryWrapper<TicketOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("qr_code_token", qrCodeToken);
        return ticketOrderMapper.selectOne(wrapper);
    }

    /**
     * 身份证号脱敏：保留前 4 位和后 4 位，中间用 * 替代
     * 例：320102199001011234 → 3201**********1234
     */
    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) return "***";
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 手机号脱敏：保留前 3 位和后 4 位，中间用 * 替代
     * 例：13812345678 → 138****5678
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
