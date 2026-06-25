package com.example.yin.config;

import com.example.yin.constant.TicketRedisKey;
import com.example.yin.mapper.TicketTierMapper;
import com.example.yin.model.domain.TicketTier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class TicketDataInitializer implements CommandLineRunner {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private TicketTierMapper ticketTierMapper;
    @Override
    public void run(String... args) {
        List<TicketTier> tiers = ticketTierMapper.selectList(null);
        int initialized = 0;
        for (TicketTier tier : tiers) {
            if (tier.getTotalStock() == null) {
                log.warn("票档 id={} totalStock 为 null，跳过 Redis 初始化", tier.getId());
                continue;
            }
            String stockKey = TicketRedisKey.stockKey(tier.getId());
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(stockKey, String.valueOf(tier.getTotalStock()));
            if (Boolean.TRUE.equals(ok)) {
                initialized++;
            }
        }
        log.info("票档库存预热完成, 共 {} 个票档, 新初始化 {} 个 key", tiers.size(), initialized);
    }
}