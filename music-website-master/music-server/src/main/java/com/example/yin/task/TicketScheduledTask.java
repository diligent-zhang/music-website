package com.example.yin.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.yin.constant.TicketRedisKey;
import com.example.yin.mapper.ConcertMapper;
import com.example.yin.mapper.TicketOrderMapper;
import com.example.yin.mapper.TicketTierMapper;
import com.example.yin.model.domain.Concert;
import com.example.yin.model.domain.TicketOrder;
import com.example.yin.model.domain.TicketTier;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;
/**
 * 购票系统定时任务
 * 负责状态刷新、售罄检测、库存对账三大周期性任务
 */
@Slf4j
@Component
@EnableScheduling
public class TicketScheduledTask {
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;
    @Autowired
    private ConcertMapper concertMapper;
    @Autowired
    private TicketTierMapper ticketTierMapper;
    @Autowired
    private TicketOrderMapper ticketOrderMapper;
    /**
     * 状态刷新 + 售罄检测（每 1 分钟执行）
     *
     * 刷新逻辑：
     *   - 预告中(status=1) 且 sale_start_time <= now → 自动切换为售票中(status=2)
     *   - 同时写入 Redis 开售标记，前端据此展示"立即购买"按钮
     *
     * 售罄检测：
     *   - 售票中(status=2) 的演唱会，所有票档 Redis 库存都为 0 → 标记售罄(status=3)
     */
    @Scheduled(cron = "0 * * * * ?")
    public void refreshConcertStatus(){
        List<Concert> concerts = concertMapper.selectList(null);
        for(Concert c : concerts){
            //预告--售票中：开票时间已到
            if (c.getStatus()==1 && c.getSaleStartTime()!=null
                    && c.getSaleStartTime().getTime()<=System.currentTimeMillis()){
                c.setStatus(2);
                concertMapper.updateById(c);
                //写入Redis开售标记，前端getDetail时可据此判断是否显示购买按钮
                redisTemplate.opsForValue().set(TicketRedisKey.onSaleKey(c.getId()),"1");
                log.info("演唱会[{}]自动开售",c.getId());
            }
            //售票中--售罄 :所有票档库存归零
            if (c.getStatus()==2){
                List<TicketTier> tiers = ticketTierMapper.selectList(
                        new LambdaQueryWrapper<TicketTier>()
                                .eq(TicketTier::getConcertId,c.getId()));
                        if (tiers.isEmpty()) continue;
                        boolean allSoldOut = true;
                for (TicketTier tier : tiers) {
                    String stockKey = TicketRedisKey.stockKey(tier.getId());
                    Object stock = redisTemplate.opsForValue().get(stockKey);
                    if (stock != null && Integer.parseInt(stock.toString()) > 0) {
                        allSoldOut = false;
                        break;
                    }
                }
                if (allSoldOut) {
                    c.setStatus(3);
                    concertMapper.updateById(c);
                    log.info("演唱会 [{}] 已售罄", c.getTitle());
                }
            }
        }
    }
    /**
     * Redis-MySQL 库存对账（每 5 分钟执行）
     *
     * 为什么需要对账：
     * 抢票流程是 Redis 扣库存 → 异步写 MySQL，极端情况下异步写入失败会触发回滚(INCR归还)，
     * 但仍可能出现 Redis 与 MySQL 不一致的情况。定时对账作为最终保障：
     *
     *   correct = tier.total_stock - COUNT(ticket_order WHERE tier_id = ?)
     *
     * 用 MySQL 的真实数据校正 Redis，保证最终一致性。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void reconcileStock() {
        log.info("库存对账开始...");
        List<TicketTier> tiers = ticketTierMapper.selectList(null);
        int corrected = 0;  // 记录被校正的票档数
        for (TicketTier tier : tiers) {
            // 从 MySQL 统计实际已售数量
            Long sold = ticketOrderMapper.selectCount(
                    new LambdaQueryWrapper<TicketOrder>()
                            .eq(TicketOrder::getTierId, tier.getId()));
            // 理论库存 = 总库存 - 已售
            int expectedStock = tier.getTotalStock() - sold.intValue();
            if (expectedStock < 0) expectedStock = 0;

            // 对比 Redis 当前库存
            String stockKey = TicketRedisKey.stockKey(tier.getId());
            Object redisStock = redisTemplate.opsForValue().get(stockKey);
            int currentRedis = redisStock != null ? Integer.parseInt(redisStock.toString()) : 0;

            // 不一致则校正
            if (currentRedis != expectedStock) {
                redisTemplate.opsForValue().set(stockKey, String.valueOf(expectedStock));
                corrected++;
                log.warn("库存校正: tierId={}, tierName={}, 期望={}, Redis原值={}",
                        tier.getId(), tier.getTierName(), expectedStock, currentRedis);
            }
        }
        log.info("库存对账完成, 校正 {} 个票档", corrected);
    }
}
