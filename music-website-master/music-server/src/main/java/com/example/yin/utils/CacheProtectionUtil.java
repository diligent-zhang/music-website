package com.example.yin.utils;

import com.example.yin.constant.CacheConstant;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存保护工具类
 *
 * 同时解决三大缓存问题：
 * 1. 缓存穿透 — 缓存空值（短TTL），防止不存在的数据反复查DB
 * 2. 缓存雪崩 — 每个key的TTL加入随机因子(0.8~1.2)，避免集中过期
 * 3. 缓存击穿 — 热点key过期时用Redis SETNX分布式锁，只让一个线程重建缓存
 */
@Component
public class CacheProtectionUtil {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    // ==================== 缓存雪崩防护：随机TTL ====================

    /**
     * 在基础TTL上叠加 ±20% 随机浮动，防止大量key同时过期
     */
    //Duration 是 Java 8 引入的时间类，用来表示两个时间点之间的时间间隔（时长），精确到秒 + 纳秒。
    private Duration randomTtl(Duration baseTtl){
        long millis = baseTtl.toMillis();
        double ratio = CacheConstant.TTL_JITTER_MIN+Math.random()*(CacheConstant.TTL_JITTER_MAX-CacheConstant.TTL_JITTER_MIN);
        return Duration.ofMillis((long)(millis*ratio));
        // ==================== 缓存穿透防护：缓存空值 ====================

    }
    /**
     * 将null值写入缓存（短TTL），下次相同查询直接返回null而不穿透到DB
     */
    public void cacheNull(String key){
        redisTemplate.opsForValue().set(key,CacheConstant.NULL_PLACEHOLDER,CacheConstant.TTL_NULL);

    }
    // ==================== 缓存击穿防护：分布式锁 ====================

    /**
     * 尝试获取互斥锁（SETNX），防止热点key过期时大量请求同时打到DB
     */

    public boolean tryLock(String lockKey){
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey,"1",CacheConstant.LOCK_TIMEOUT_SECONDS,TimeUnit.SECONDS);
        return Boolean.TRUE.equals(locked);
    }
    private void unlock(String lockKey){
        redisTemplate.delete(lockKey);
    }

    // ==================== 综合方法：一次解决三大问题 ====================

    /**
     * 带完整保护的缓存读取
     *
     * @param key      缓存Key
     * @param clazz    返回值类型
     * @param dbLoader 数据库查询逻辑（Lambda/Supplier）
     * @param baseTtl  基础过期时间（会自动叠加随机因子防止雪崩）
     * @return 缓存中的值或DB查询结果
     */

    @SuppressWarnings("unchecked") //这是 Java 的注解，用来压制【unchecked 未检查类型转换】的黄色警告，不影响代码运行，只是让编译器不报错、不弹黄。
    public <T> T getWithProtection(String key,Class<T> clazz,Supplier<T> dbLoader,Duration baseTtl) {
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        //1、先查缓存
        Object cached = ops.get(key);
        if (cached != null) {   //cached不是null，但可能是空值，所以下面才要返回NULl
            //命中空值占位符--缓存穿透保护生效，返回NULL
            if (CacheConstant.NULL_PLACEHOLDER.equals(cached)) {
                return null;
            }
            return (T) cached;
        }
        //2.缓存未命中，尝试获取分布式锁(击穿保护)
        String lockKey = key + CacheConstant.LOCK_SUFFIX;
        int retryCount = 0;
        while (!tryLock(lockKey) && retryCount < CacheConstant.MAX_RETRY) {
            try {
                Thread.sleep(CacheConstant.RETRY_SLEEP_MS);  //没有抢到锁，就睡眠一会
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  //阻断当前线程
                break;
            }
            retryCount++;

            //重试期间再次检查缓存（可能其他线程已经重建完成）
            cached = ops.get(key);
            if (cached != null) {
                if (CacheConstant.NULL_PLACEHOLDER.equals(cached)) {
                    return null;
                }
                return (T) cached;
            }
        }
        //3.获取锁成功（或重试耗尽），负责重建缓存
        try {
            //Double-check:再次检查缓存
            cached = ops.get(key);
            if (cached != null) {
                if (CacheConstant.NULL_PLACEHOLDER.equals(cached)) {
                    return null;
                }
                return (T) cached;
            }
            //4.查数据库
            T data = dbLoader.get();
            //5.写入缓存
            if (data==null || (data instanceof List&&((List<?>)data).isEmpty())){
                // 缓存穿透保护：空值也缓存，短TTL
                ops.set(key, CacheConstant.NULL_PLACEHOLDER, randomTtl(CacheConstant.TTL_NULL));
            }
            else {
                //缓存雪崩保护：随机TTL
                ops.set(key,data,randomTtl(baseTtl));
            }
            return data;
        }finally {
            unlock(lockKey);  //释放锁
        }
    }
    // ==================== 便捷方法 ====================

    /**
     * 缓存普通对象（带随机TTL防雪崩）
     */
    public void set(String key, Object value, Duration baseTtl) {
        redisTemplate.opsForValue().set(key, value, randomTtl(baseTtl));
    }

    /**
     * 从缓存获取对象
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null || CacheConstant.NULL_PLACEHOLDER.equals(value)) {
            return null;
        }
        return (T) value;
    }

    /**
     * 删除缓存（用于写操作后的缓存清理）
     */
    public void evict(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 按前缀批量删除缓存
     */
    public void evictByPrefix(String prefix) {
        var keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }



}
