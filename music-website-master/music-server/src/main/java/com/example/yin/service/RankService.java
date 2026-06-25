package com.example.yin.service;
import java.util.Map;
import java.util.List;
public interface RankService {
    /**
     * 获取排行榜列表（优先缓存 → Redis ZSet → DB）
     *
     * @param type  day / week / month
     * @param limit 返回条数，默认 20
     */
    List<Map<String, Object>> getRankList(String type, int limit);

    /**
     * 重载，默认返回 20 条
     */
    List<Map<String, Object>> getRankList(String type);

    /**
     * 记录一次播放（异步写入 Redis + DB + 日志 + 缓存失效）
     *
     * @param songId 歌曲ID
     * @param userId 用户ID（未登录可为 null）
     */
    void recordPlay(Integer songId, Integer userId);

    /**
     * 获取歌曲在三个时段榜单中的排名
     *
     * @param songId 歌曲ID
     * @return { dailyRank: 5, weeklyRank: 12, monthlyRank: 8 }
     */
    Map<String, Object> getSongRankDetail(Integer songId);

    void updatePlayCount(Integer songId, String type, Long playCount);

    void resetRank(String type);

    List<Map<String, Object>> exportRank(String type);
}