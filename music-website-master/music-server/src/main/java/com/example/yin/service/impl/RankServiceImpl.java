package com.example.yin.service.impl;
import com.example.yin.constant.RankRedisKey;
import com.example.yin.mapper.PlayLogMapper;
import com.example.yin.mapper.SingerMapper;
import com.example.yin.mapper.SongMapper;
import com.example.yin.model.domain.PlayLog;
import com.example.yin.model.domain.Singer;
import com.example.yin.model.domain.Song;
import com.example.yin.service.RankService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
@Slf4j
@Service
public class
RankServiceImpl implements RankService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private SingerMapper singerMapper;

    @Autowired
    private PlayLogMapper playLogMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private static final int DEFAULT_LIMIT = 20;

    // ==================== 查询排行榜 ====================

    @Override
    public List<Map<String, Object>> getRankList(String type) {
        return getRankList(type, DEFAULT_LIMIT);
    }

    @Override
    public List<Map<String, Object>> getRankList(String type, int limit) {
        // 第1步：尝试从缓存读取（5分钟有效期）
        String cacheKey = getCacheKey(type);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(
                        cached.toString(),
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, Map.class)
                );
            } catch (Exception e) {
                log.warn("排行榜缓存反序列化失败，回退查询: {}", e.getMessage());
            }
        }

        // 第2步：从 Redis ZSet 取 Top-N
        String zsetKey = getZSetKey(type);
        Set<Object> topSet = redisTemplate.opsForZSet()
                .reverseRange(zsetKey, 0, limit - 1);

        List<Map<String, Object>> result;

        if (topSet != null && !topSet.isEmpty()) {
            result = buildRankListFromRedis(topSet, zsetKey, limit);
        } else {
            // 第3步：Redis 为空时回退到 DB（通过 play_log 表按时间段统计）
            result = getRankFromDatabase(type, limit);
        }

        // 第4步：写入缓存
        if (!result.isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(result);
                redisTemplate.opsForValue()
                        .set(cacheKey, json, RankRedisKey.CACHE_TTL_SECONDS,
                                java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("写入排行榜缓存失败: {}", e.getMessage());
            }
        }

        return result;
    }

    // ==================== 记录播放 ====================

    @Override
    @Async  // 异步执行，不阻塞主线程
    public void recordPlay(Integer songId, Integer userId) {
        if (songId == null) return;

        // 1) Redis ZSet：三个时段分数各 +1
        redisTemplate.opsForZSet().incrementScore(RankRedisKey.DAILY_RANK_KEY, songId, 1);
        redisTemplate.opsForZSet().incrementScore(RankRedisKey.WEEKLY_RANK_KEY, songId, 1);
        redisTemplate.opsForZSet().incrementScore(RankRedisKey.MONTHLY_RANK_KEY, songId, 1);

        // 2) Redis 单曲总计数器 +1
        String counterKey = RankRedisKey.PLAY_COUNT_PREFIX + songId;
        redisTemplate.opsForValue().increment(counterKey);

        // 3) MySQL：song.play_count +1
        songMapper.updatePlayCount(songId);

        // 4) MySQL：写入播放日志
        PlayLog log = new PlayLog();
        log.setSongId(songId);
        log.setUserId(userId);
        log.setPlayTime(new Date());
        playLogMapper.insert(log);

        // 5) 失效三个缓存 Key，让下次查询强制刷新
        redisTemplate.delete(RankRedisKey.CACHE_DAILY);
        redisTemplate.delete(RankRedisKey.CACHE_WEEKLY);
        redisTemplate.delete(RankRedisKey.CACHE_MONTHLY);
    }

    // ==================== 歌曲排名详情 ====================

    @Override
    public Map<String, Object> getSongRankDetail(Integer songId) {
        Map<String, Object> detail = new HashMap<>();

        Long dailyRank = redisTemplate.opsForZSet()
                .reverseRank(RankRedisKey.DAILY_RANK_KEY, songId);
        Long weeklyRank = redisTemplate.opsForZSet()
                .reverseRank(RankRedisKey.WEEKLY_RANK_KEY, songId);
        Long monthlyRank = redisTemplate.opsForZSet()
                .reverseRank(RankRedisKey.MONTHLY_RANK_KEY, songId);

        detail.put("songId", songId);
        detail.put("dailyRank",   dailyRank   != null ? dailyRank + 1   : null);  // rank从0开始，+1转为从1开始
        detail.put("weeklyRank",  weeklyRank  != null ? weeklyRank + 1  : null);
        detail.put("monthlyRank", monthlyRank != null ? monthlyRank + 1 : null);

        // 总播放量（优先 Redis，没有则用 DB）
        String counterKey = RankRedisKey.PLAY_COUNT_PREFIX + songId;
        Object counter = redisTemplate.opsForValue().get(counterKey);
        if (counter != null) {
            detail.put("totalPlays", Long.parseLong(counter.toString()));
        } else {
            Song song = songMapper.selectById(songId);
            detail.put("totalPlays", song != null ? song.getPlayCount() : 0);
        }

        return detail;
    }

    // ==================== 内部方法 ====================

    private List<Map<String, Object>> buildRankListFromRedis(
            Set<Object> topSet, String zsetKey, int limit) {
        List<Integer> songIds = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (Object songIdObj : topSet) {
            Integer songId = parseSongId(songIdObj);
            if (songId == null) continue;
            Double score = redisTemplate.opsForZSet().score(zsetKey, songId);
            songIds.add(songId);
            scores.add(score);
        }
        return batchBuildResults(songIds, scores);
    }

    /**
     * DB 回退：从 play_log 表中按时间段 GROUP BY 统计
     */
    private List<Map<String, Object>> getRankFromDatabase(String type, int limit) {
        LocalDateTime now = LocalDateTime.now();
        String startTime;
        String endTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        switch (type.toLowerCase()) {
            case "week":
                startTime = now.minusWeeks(1)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                break;
            case "month":
                startTime = now.minusMonths(1)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                break;
            case "day":
            default:
                startTime = LocalDate.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 00:00:00";
                break;
        }

        List<Map<String, Object>> rawList = playLogMapper
                .selectRankByTimeRange(startTime, endTime, limit);

        List<Integer> songIds = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (Map<String, Object> row : rawList) {
            Integer songId = (Integer) row.get("songId");
            Object playCountObj = row.get("playCount");
            if (songId == null) continue;
            songIds.add(songId);
            double score = 0;
            if (playCountObj instanceof Number) {
                score = ((Number) playCountObj).doubleValue();
            }
            scores.add(score);
        }
        return batchBuildResults(songIds, scores);
    }

    // 批量查询歌曲和歌手，一次构建所有结果
    private List<Map<String, Object>> batchBuildResults(List<Integer> songIds, List<Double> scores) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (songIds.isEmpty()) return result;

        // 一次批量查所有歌曲
        List<Song> songs = songMapper.selectBatchIds(songIds);
        // 收集所有歌手ID
        Set<Integer> singerIds = new HashSet<>();
        for (Song song : songs) {
            if (song.getSingerId() != null) {
                singerIds.add(song.getSingerId());
            }
        }
        // 一次批量查所有歌手
        Map<Integer, String> singerNameMap = new HashMap<>();
        if (!singerIds.isEmpty()) {
            List<Singer> singers = singerMapper.selectBatchIds(singerIds);
            for (Singer singer : singers) {
                singerNameMap.put(singer.getId(), singer.getName());
            }
        }
        // 建立 songId → Song 映射，按原始顺序构建结果
        Map<Integer, Song> songMap = new HashMap<>();
        for (Song song : songs) {
            songMap.put(song.getId(), song);
        }
        for (int i = 0; i < songIds.size(); i++) {
            Integer songId = songIds.get(i);
            Song song = songMap.get(songId);
            if (song == null) continue;

            Map<String, Object> item = new HashMap<>();
            String singerName = singerNameMap.getOrDefault(song.getSingerId(), "未知歌手");
            item.put("id", song.getId());
            item.put("title", song.getName());
            item.put("name", song.getName());
            item.put("singerName", singerName);
            item.put("singer", singerName);
            long count = i < scores.size() && scores.get(i) != null ? scores.get(i).longValue() : 0;
            item.put("playCount", count);
            item.put("play_count", count);
            item.put("url", song.getUrl());
            item.put("pic", song.getPic());
            result.add(item);
        }
        return result;
    }

    private Integer parseSongId(Object obj) {
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Number) return ((Number) obj).intValue();
        if (obj instanceof String) {
            try { return Integer.parseInt((String) obj); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private String getZSetKey(String type) {
        switch (type.toLowerCase()) {
            case "week":  return RankRedisKey.WEEKLY_RANK_KEY;
            case "month": return RankRedisKey.MONTHLY_RANK_KEY;
            case "day":
            default:      return RankRedisKey.DAILY_RANK_KEY;
        }
    }

    private String getCacheKey(String type) {
        switch (type.toLowerCase()) {
            case "week":  return RankRedisKey.CACHE_WEEKLY;
            case "month": return RankRedisKey.CACHE_MONTHLY;
            case "day":
            default:      return RankRedisKey.CACHE_DAILY;
        }
    }

    // ==================== 管理员操作 ====================

    @Override
    public void updatePlayCount(Integer songId, String type, Long playCount) {
        String zsetKey = getZSetKey(type);
        redisTemplate.opsForZSet().add(zsetKey, songId, playCount.doubleValue());
        redisTemplate.delete(getCacheKey(type));
        updateSongPlayCount(songId);
    }

    private void updateSongPlayCount(Integer songId) {
        Double dailyScore = redisTemplate.opsForZSet()
                .score(RankRedisKey.DAILY_RANK_KEY, songId);
        Double weeklyScore = redisTemplate.opsForZSet()
                .score(RankRedisKey.WEEKLY_RANK_KEY, songId);
        Double monthlyScore = redisTemplate.opsForZSet()
                .score(RankRedisKey.MONTHLY_RANK_KEY, songId);
        long max = Math.max(
                Math.max(dailyScore != null ? dailyScore.longValue() : 0,
                        weeklyScore != null ? weeklyScore.longValue() : 0),
                monthlyScore != null ? monthlyScore.longValue() : 0
        );
        songMapper.updatePlayCountByValue(songId, (int) max);
    }

    @Override
    public void resetRank(String type) {
        String zsetKey = getZSetKey(type);
        redisTemplate.delete(zsetKey);
        redisTemplate.delete(getCacheKey(type));
        String tsKey;
        switch (type.toLowerCase()) {
            case "week":  tsKey = RankRedisKey.WEEKLY_RESET_TS; break;
            case "month": tsKey = RankRedisKey.MONTHLY_RESET_TS; break;
            default:      tsKey = RankRedisKey.DAILY_RESET_TS; break;
        }
        redisTemplate.opsForValue().set(tsKey,
                String.valueOf(System.currentTimeMillis()));
    }

    @Override
    public List<Map<String, Object>> exportRank(String type) {
        return getRankList(type, 200);
    }
}