package com.example.yin.util;

import com.example.yin.model.domain.Concert;

import java.util.Date;

/**
 * 演唱会"有效状态"推导器 —— 以真实时钟为准,校正 DB 中可能陈旧的 status。
 *
 * 因为演出状态本应由时间自动推进,而定时任务只能低频(分钟级)落库,
 * 所以读取/购票时用本方法按"当下时刻"推导该显示的终态,保证到点立即生效。
 * 状态语义:0=下架,1=预告,2=售票中,3=售罄,4=已结束
 *
 * 规则(顺序即优先级):
 *   1. DB 已是 0(管理员手动下架)→ 保持 0,不因时间"复活"
 *   2. now >= showTime → 0(演出时间已到/已过 → 下架,不再售票)
 *   3. now <  saleStartTime → 1(未到开售 → 预告)
 *   4. 其余情况 → 按 DB 原值(售票中/售罄/已结束)
 */
public final class ConcertStatusResolver {

    private ConcertStatusResolver() {
    }

    public static Integer resolve(Concert concert, Date now) {
        if (concert == null) {
            return null;
        }
        return resolve(concert.getStatus(), concert.getShowTime(),
                concert.getSaleStartTime(), now);
    }

    public static Integer resolve(Integer dbStatus, Date showTime, Date saleStartTime, Date now) {
        if (dbStatus == null || now == null) {
            return dbStatus;
        }
        if (dbStatus == 0) {
            return 0;
        }
        if (showTime != null && now.compareTo(showTime) >= 0) {
            return 0;
        }
        if (saleStartTime != null && now.compareTo(saleStartTime) < 0) {
            return 1;
        }
        return dbStatus;
    }
}
