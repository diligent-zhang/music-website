package com.example.yin.config;

import com.example.yin.constant.RankRedisKey;
import com.example.yin.mapper.SongMapper;
import com.example.yin.model.domain.Song;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 排行榜数据初始化器
 * 仅在 Redis 完全没有数据时，用 DB 中的 play_count 恢复各时段 ZSet
 */
@Slf4j
@Component
public class RankDataInitializer implements CommandLineRunner {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SongMapper songMapper;

    @Override
    public void run(String... args) {
        Long size = redisTemplate.opsForZSet().size(RankRedisKey.DAILY_RANK_KEY);
        if (size != null && size > 0) {
            return;
        }

        List<Song> songs = songMapper.selectList(null);
        if (songs == null || songs.isEmpty()) {
            return;
        }

        for (Song song : songs) {
            Integer playCount = song.getPlayCount();
            if (playCount == null || playCount <= 0) {
                continue;
            }

            double score = playCount.doubleValue();

            // 日用 1/10 折算，周用 1/3 折算，月用全量
            redisTemplate.opsForZSet().add(RankRedisKey.DAILY_RANK_KEY, song.getId(), score * 0.1);
            redisTemplate.opsForZSet().add(RankRedisKey.WEEKLY_RANK_KEY, song.getId(), score * 0.3);
            redisTemplate.opsForZSet().add(RankRedisKey.MONTHLY_RANK_KEY, song.getId(), score);

            redisTemplate.opsForValue().set(
                    RankRedisKey.PLAY_COUNT_PREFIX + song.getId(), playCount);
        }

        log.info("排行榜数据从数据库初始化完成，共 {} 首歌曲", songs.size());
    }
}
