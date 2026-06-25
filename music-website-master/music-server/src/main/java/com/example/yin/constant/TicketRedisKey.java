package com.example.yin.constant;

public class TicketRedisKey {

    // ========== Key 前缀 ==========

    // 库存计数器（String），key: ticket:stock:{tierId}
    public static final String STOCK_PREFIX = "ticket:stock:";

    // 用户已购标记，key: ticket:purchased:{userId}:{tierId}
    public static final String PURCHASED_PREFIX = "ticket:purchased:";

    // 订单缓存（String JSON），key: ticket:order:{orderNo}，TTL = 7天
    public static final String ORDER_PREFIX = "ticket:order:";
    public static final long ORDER_TTL_SECONDS = 7 * 24 * 3600;

    // 开售标记，key: ticket:on_sale:{concertId}
    public static final String ON_SALE_PREFIX = "ticket:on_sale:";

    // 库存扣减 Lua 脚本
    public static final String LUA_DECR_STOCK =
            "local stock = redis.call('GET', KEYS[1])\n" +
                    "local num = tonumber(stock)\n" +
                    "if not num or num <= 0 then\n" +
                    "    return 0\n" +
                    "end\n" +
                    "redis.call('DECR', KEYS[1])\n" +
                    "return 1";

    // ========== Key 生成方法 ==========

    public static String stockKey(Integer tierId) {
        return STOCK_PREFIX + tierId;
    }

    public static String purchasedKey(Integer userId, Integer tierId) {
        return PURCHASED_PREFIX + userId + ":" + tierId;
    }

    public static String orderKey(String orderNo) {
        return ORDER_PREFIX + orderNo;
    }

    public static String onSaleKey(Integer concertId) {
        return ON_SALE_PREFIX + concertId;
    }
}
