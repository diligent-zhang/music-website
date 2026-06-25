package com.example.yin.task;

import com.example.yin.constant.RankRedisKey;
import com.example.yin.mapper.PlayLogMapper;
import com.example.yin.mapper.RankSnapshotMapper;
import com.example.yin.mapper.SongMapper;
import com.example.yin.model.domain.RankSnapshot;
import com.example.yin.model.domain.Song;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@EnableScheduling
public class RankScheduledTask {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PlayLogMapper playLogMapper;

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private RankSnapshotMapper rankSnapshotMapper;

    /**
     * 每日 00:03 重置日榜
     * 先保存昨日快照到 DB，再清空 Redis ZSet
     */
    @Scheduled(cron = "0 3 0 * * ?")
    public void resetDailyRank() {
        String today = LocalDate.now().toString();
        String lastReset = (String) redisTemplate.opsForValue().get(RankRedisKey.DAILY_RESET_TS);

        if (today.equals(lastReset)) {
            log.info("日榜今日已重置，跳过");
            return;
        }

        saveSnapshot(RankRedisKey.DAILY_RANK_KEY, "day");

        redisTemplate.delete(RankRedisKey.DAILY_RANK_KEY);
        redisTemplate.delete(RankRedisKey.CACHE_DAILY);
        redisTemplate.opsForValue().set(RankRedisKey.DAILY_RESET_TS, today);

        log.info("日榜重置完成, date={}", today);
    }

    /**
     * 每周一 00:07 重置周榜
     */
    @Scheduled(cron = "0 7 0 ? * MON")
    public void resetWeeklyRank() {
        String today = LocalDate.now().toString();
        String lastReset = (String) redisTemplate.opsForValue()
                .get(RankRedisKey.WEEKLY_RESET_TS);

        if (today.equals(lastReset)) {
            log.info("周榜今日已重置，跳过");
            return;
        }

        saveSnapshot(RankRedisKey.WEEKLY_RANK_KEY, "week");

        redisTemplate.delete(RankRedisKey.WEEKLY_RANK_KEY);
        redisTemplate.delete(RankRedisKey.CACHE_WEEKLY);
        redisTemplate.opsForValue().set(RankRedisKey.WEEKLY_RESET_TS, today);

        log.info("周榜重置完成, date={}", today);
    }

    /**
     * 每月 1 号 00:10 重置月榜
     */
    @Scheduled(cron = "0 10 0 1 * ?")
    public void resetMonthlyRank() {
        String today = LocalDate.now().toString();
        String lastReset = (String) redisTemplate.opsForValue()
                .get(RankRedisKey.MONTHLY_RESET_TS);

        if (today.equals(lastReset)) {
            log.info("月榜今日已重置，跳过");
            return;
        }

        saveSnapshot(RankRedisKey.MONTHLY_RANK_KEY, "month");

        redisTemplate.delete(RankRedisKey.MONTHLY_RANK_KEY);
        redisTemplate.delete(RankRedisKey.CACHE_MONTHLY);
        redisTemplate.opsForValue().set(RankRedisKey.MONTHLY_RESET_TS, today);

        log.info("月榜重置完成, date={}", today);
    }

    /**
     * 每小时同步一次：将 Redis 中的歌曲总播放量回写到 MySQL song.play_count
     * 确保 Redis 挂了之后，DB 数据不会太旧
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void syncPlayCountsToDatabase() {
        log.info("开始同步播放量到数据库...");

        List<Song> songs = songMapper.selectList(null);
        int updated = 0;
        for (Song song : songs) {
            String key = RankRedisKey.PLAY_COUNT_PREFIX + song.getId();
            Object countObj = redisTemplate.opsForValue().get(key);
            if (countObj != null) {
                long redisCount = Long.parseLong(countObj.toString());
                Integer dbCount = song.getPlayCount();
                if (dbCount == null || redisCount > dbCount) {
                    song.setPlayCount((int) redisCount);
                    songMapper.updateById(song);
                    updated++;
                }
            }
        }

        log.info("播放量同步完成，共更新 {} 首歌曲", updated);
    }

    /**
     * 保存当前排行榜快照到 rank_snapshot 表
     */
    @SuppressWarnings("unchecked")
    private void saveSnapshot(String zsetKey, String periodType) {
        Set<TypedTuple<Object>> topSet = redisTemplate.opsForZSet()
                .reverseRangeWithScores(zsetKey, 0, 49);

        if (topSet == null || topSet.isEmpty()) {
            log.info("排行榜无数据，跳过快照保存");
            return;
        }

        String today = LocalDate.now().toString();

        List<RankSnapshot> snapshots = new ArrayList<>();
        int rank = 1;
        for (TypedTuple<Object> tuple : topSet) {
            RankSnapshot snapshot = new RankSnapshot();
            snapshot.setSnapshotDate(today);
            snapshot.setPeriodType(periodType);
            snapshot.setSongId((Integer) tuple.getValue());
            snapshot.setRankPosition(rank);
            snapshot.setPlayCount(tuple.getScore() != null ? tuple.getScore().intValue() : 0);
            snapshots.add(snapshot);
            rank++;
        }

        for (RankSnapshot snapshot : snapshots) {
            rankSnapshotMapper.insert(snapshot);
        }

        log.info("排行榜快照保存完成, key={}, date={}, 条数={}",
                zsetKey, today, snapshots.size());
    }
}
