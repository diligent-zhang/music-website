package com.example.yin.constant;
/**
 ● 排行榜相关的 Redis Key 常量
 */
public class RankRedisKey { // ========== 排序 ZSet（每日/每周/每月不带时间戳的 Key） ==========
    public static final String DAILY_RANK_KEY   = "rank:daily";
    public static final String WEEKLY_RANK_KEY  = "rank:weekly";
    public static final String MONTHLY_RANK_KEY = "rank:monthly"; // ========== 重置时间戳（防止重复重置） ==========
    public static final String DAILY_RESET_TS   = "rank:daily:ts";
    public static final String WEEKLY_RESET_TS  = "rank:weekly:ts";
    public static final String MONTHLY_RESET_TS = "rank:monthly:ts"; // ========== 结果缓存（5分钟 TTL） ==========
    public static final String CACHE_DAILY      = "rank:cache:day";
    public static final String CACHE_WEEKLY     = "rank:cache:week";
    public static final String CACHE_MONTHLY    = "rank:cache:month"; // ========== 单曲总播放计数器 ==========
    public static final String PLAY_COUNT_PREFIX = "rank:play:"; // ========== 缓存过期时间 ==========
    public static final long CACHE_TTL_SECONDS = 300;  // 5分钟
}