package com.example.yin.constant;

import java.time.Duration;

/**
 * 缓存常量：统一管理缓存Key、过期时间、分布式锁配置
 */
public class CacheConstant {

    // ======================== 缓存命名空间 ========================
    public static final String CACHE_SONG = "cache:song";
    public static final String CACHE_SINGER = "cache:singer";
    public static final String CACHE_SONGLIST = "cache:songList";
    public static final String CACHE_CONSUMER = "cache:consumer";
    public static final String CACHE_COMMENT = "cache:comment";
    public static final String CACHE_COLLECT = "cache:collect";
    public static final String CACHE_BANNER = "cache:banner";

    // ======================== 基础TTL ========================
    /**
     * 歌曲缓存：30分钟
     */
    public static final Duration TTL_SONG = Duration.ofMinutes(30);
    /**
     * 歌手缓存：1小时
     */
    public static final Duration TTL_SINGER = Duration.ofHours(1);
    /**
     * 歌单缓存：30分钟
     */
    public static final Duration TTL_SONGLIST = Duration.ofMinutes(30);
    /**
     * 评论缓存：5分钟
     */
    public static final Duration TTL_COMMENT = Duration.ofMinutes(5);
    /**
     * 用户缓存：1小时
     */
    public static final Duration TTL_CONSUMER = Duration.ofHours(1);
    /**
     * 收藏缓存：10分钟
     */
    public static final Duration TTL_COLLECT = Duration.ofMinutes(10);
    /**
     * Banner缓存：10分钟
     */
    public static final Duration TTL_BANNER = Duration.ofMinutes(10);

    // ======================== 缓存穿透防护 ========================
    /**
     * 空值占位符缓存TTL（短过期，防止缓存穿透）
     */
    public static final Duration TTL_NULL = Duration.ofMinutes(2);

    // ======================== 缓存击穿防护 ========================
    /**
     * 分布式锁超时时间（秒）
     */
    public static final long LOCK_TIMEOUT_SECONDS = 30;
    /**
     * 获取锁失败后重试等待（毫秒）
     */
    public static final long RETRY_SLEEP_MS = 100;
    /**
     * 最大重试次数
     */
    public static final int MAX_RETRY = 10;
    /**
     * 锁Key后缀
     */
    public static final String LOCK_SUFFIX = ":mutex";

    // ======================== 空值标记 ========================
    /**
     * 缓存穿透空值占位符
     */
    public static final String NULL_PLACEHOLDER = "##NULL##";

    // ======================== 缓存雪崩防护 ========================
    /**
     * TTL随机浮动比例下限
     */
    public static final double TTL_JITTER_MIN = 0.8;
    /**
     * TTL随机浮动比例上限
     */
    public static final double TTL_JITTER_MAX = 1.2;

    // ======================== Key构建方法 ========================

    // --- Song ---
    public static String songAllKey() {
        return CACHE_SONG + "::all";
    }

    public static String songByIdKey(Integer id) {
        return CACHE_SONG + "::id::" + id;
    }

    public static String songBySingerIdKey(Integer singerId) {
        return CACHE_SONG + "::singerId::" + singerId;
    }

    public static String songByNameKey(String name) {
        return CACHE_SONG + "::name::" + name;
    }

    public static String songByIdsKey(String ids) {
        return CACHE_SONG + "::ids::" + ids;
    }

    public static String songRankKey(String type) {
        return CACHE_SONG + "::rank::" + type;
    }

    public static String songBySongListIdKey(Integer songListId) {
        return CACHE_SONG + "::songListId::" + songListId;
    }

    // --- Singer ---
    public static String singerAllKey() {
        return CACHE_SINGER + "::all";
    }

    public static String singerByNameKey(String name) {
        return CACHE_SINGER + "::name::" + name;
    }

    public static String singerBySexKey(Integer sex) {
        return CACHE_SINGER + "::sex::" + sex;
    }

    // --- SongList ---
    public static String songListAllKey() {
        return CACHE_SONGLIST + "::all";
    }

    public static String songListByTitleKey(String title) {
        return CACHE_SONGLIST + "::title::" + title;
    }

    public static String songListByStyleKey(String style) {
        return CACHE_SONGLIST + "::style::" + style;
    }

    // --- Comment ---
    public static String commentBySongIdKey(Integer songId) {
        return CACHE_COMMENT + "::songId::" + songId;
    }

    public static String commentBySongListIdKey(Integer songListId) {
        return CACHE_COMMENT + "::songListId::" + songListId;
    }

    // --- Collect ---
    public static String collectByUserIdKey(Integer userId) {
        return CACHE_COLLECT + "::userId::" + userId;
    }

    // --- Consumer ---
    public static String consumerAllKey() {
        return CACHE_CONSUMER + "::all";
    }

    public static String consumerByIdKey(Integer id) {
        return CACHE_CONSUMER + "::id::" + id;
    }

    // --- Banner ---
    public static String bannerAllKey() {
        return CACHE_BANNER + "::list";
    }

    // --- Song 分页 ---
    public static String songBySongListIdPageKey(Integer songListId,
                                                 int page, int pageSize) {
        return CACHE_SONG + "::songListId::" + songListId + "::page::"
                + page + "::size::" + pageSize;

    }
}
