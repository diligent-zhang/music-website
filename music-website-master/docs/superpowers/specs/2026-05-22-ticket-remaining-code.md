# 购票功能 - 剩余全部代码 (第 2~4 阶段)

---

## 第 2 阶段：Service 接口 + 实现

### 2.1 ConcertService.java
```java
package com.example.yin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.yin.common.R;
import com.example.yin.model.domain.Concert;
import com.example.yin.model.request.ConcertRequest;

public interface ConcertService extends IService<Concert> {

    R listConcerts(Integer page, Integer size, Integer status);

    R getDetail(Integer concertId);

    R addConcert(ConcertRequest request);

    R updateConcert(ConcertRequest request);

    R updateStatus(Integer concertId, Integer status);
}
```

### 2.2 ConcertServiceImpl.java
```java
package com.example.yin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.yin.common.R;
import com.example.yin.constant.TicketRedisKey;
import com.example.yin.mapper.ConcertMapper;
import com.example.yin.mapper.TicketTierMapper;
import com.example.yin.model.domain.Concert;
import com.example.yin.model.domain.TicketTier;
import com.example.yin.model.request.ConcertRequest;
import com.example.yin.service.ConcertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ConcertServiceImpl extends ServiceImpl<ConcertMapper, Concert>
        implements ConcertService {

    @Autowired
    private TicketTierMapper ticketTierMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public R listConcerts(Integer page, Integer size, Integer status) {
        LambdaQueryWrapper<Concert> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Concert::getStatus, status);
        }
        wrapper.orderByDesc(Concert::getSaleStartTime);
        wrapper.orderByAsc(Concert::getCreateTime);

        Page<Concert> result = baseMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        return R.success(data);
    }

    @Override
    public R getDetail(Integer concertId) {
        Concert concert = baseMapper.selectById(concertId);
        if (concert == null) {
            return R.error("演唱会不存在");
        }

        List<TicketTier> tiers = ticketTierMapper.selectList(
                new LambdaQueryWrapper<TicketTier>()
                        .eq(TicketTier::getConcertId, concertId)
        );

        // 补充实时库存
        List<Map<String, Object>> tierList = new ArrayList<>();
        for (TicketTier tier : tiers) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", tier.getId());
            item.put("tierName", tier.getTierName());
            item.put("price", tier.getPrice());
            item.put("totalStock", tier.getTotalStock());

            // 从 Redis 读取剩余库存
            String stockKey = TicketRedisKey.stockKey(tier.getId());
            Object stock = redisTemplate.opsForValue().get(stockKey);
            int remaining = stock != null ? Integer.parseInt(stock.toString()) : tier.getTotalStock();
            item.put("remainingStock", remaining);
            tierList.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("concert", concert);
        data.put("tiers", tierList);
        return R.success(data);
    }

    @Override
    @Transactional
    public R addConcert(ConcertRequest request) {
        Concert concert = new Concert();
        BeanUtils.copyProperties(request, concert);
        baseMapper.insert(concert);
        Integer concertId = concert.getId();

        // 批量插入票档 + 初始化 Redis 库存
        if (request.getTiers() != null) {
            for (ConcertRequest.TierRequest tr : request.getTiers()) {
                TicketTier tier = new TicketTier();
                tier.setConcertId(concertId);
                tier.setTierName(tr.getTierName());
                tier.setPrice(tr.getPrice());
                tier.setTotalStock(tr.getTotalStock());
                ticketTierMapper.insert(tier);

                // 初始化 Redis 库存（SETNX，防止覆盖已有数据）
                String stockKey = TicketRedisKey.stockKey(tier.getId());
                redisTemplate.opsForValue()
                        .setIfAbsent(stockKey, String.valueOf(tr.getTotalStock()));
            }
        }

        return R.success("演唱会发布成功");
    }

    @Override
    @Transactional
    public R updateConcert(ConcertRequest request) {
        Concert concert = baseMapper.selectById(request.getId());
        if (concert == null) {
            return R.error("演唱会不存在");
        }
        BeanUtils.copyProperties(request, concert);
        baseMapper.updateById(concert);
        return R.success("演唱会信息已更新");
    }

    @Override
    public R updateStatus(Integer concertId, Integer status) {
        Concert concert = baseMapper.selectById(concertId);
        if (concert == null) {
            return R.error("演唱会不存在");
        }
        concert.setStatus(status);
        baseMapper.updateById(concert);

        // 同步开售标记到 Redis
        if (status == 2) {
            redisTemplate.opsForValue()
                    .set(TicketRedisKey.onSaleKey(concertId), "1");
        } else {
            redisTemplate.delete(TicketRedisKey.onSaleKey(concertId));
        }

        return R.success("状态已更新");
    }
}
```

### 2.3 TicketService.java
```java
package com.example.yin.service;

import com.example.yin.common.R;
import com.example.yin.model.request.TicketBuyRequest;

public interface TicketService {

    R buy(TicketBuyRequest request);

    R getOrder(String orderNo);

    R getMyOrders(Integer userId);
}
```

### 2.4 TicketServiceImpl.java
```java
package com.example.yin.service.impl;

import com.example.yin.common.R;
import com.example.yin.constant.TicketRedisKey;
import com.example.yin.mapper.ConcertMapper;
import com.example.yin.mapper.TicketOrderMapper;
import com.example.yin.mapper.TicketTierMapper;
import com.example.yin.model.domain.Concert;
import com.example.yin.model.domain.TicketOrder;
import com.example.yin.model.domain.TicketTier;
import com.example.yin.model.request.TicketBuyRequest;
import com.example.yin.service.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TicketServiceImpl implements TicketService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ConcertMapper concertMapper;

    @Autowired
    private TicketTierMapper ticketTierMapper;

    @Autowired
    private TicketOrderMapper ticketOrderMapper;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== 购票核心 ====================

    @Override
    public R buy(TicketBuyRequest request) {
        // 1. 参数校验
        if (request.getUserId() == null || request.getConcertId() == null
                || request.getTierId() == null) {
            return R.error("参数不完整");
        }

        Concert concert = concertMapper.selectById(request.getConcertId());
        if (concert == null) {
            return R.error("演唱会不存在");
        }
        if (concert.getStatus() != 2) {
            return R.error("当前不可购票");
        }

        TicketTier tier = ticketTierMapper.selectById(request.getTierId());
        if (tier == null || !tier.getConcertId().equals(request.getConcertId())) {
            return R.error("票档不存在");
        }

        // 2. 用户去重（SETNX）
        String purchasedKey = TicketRedisKey.purchasedKey(
                request.getUserId(), request.getTierId());
        Boolean isFirst = redisTemplate.opsForValue()
                .setIfAbsent(purchasedKey, "1");
        if (Boolean.FALSE.equals(isFirst)) {
            return R.error("您已购买过该票档");
        }

        // 3. Lua 脚本原子扣库存
        String stockKey = TicketRedisKey.stockKey(request.getTierId());
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(TicketRedisKey.LUA_DECR_STOCK);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script, Collections.singletonList(stockKey));

        if (result == null || result == 0) {
            // 扣减失败，释放已购标记
            redisTemplate.delete(purchasedKey);
            return R.error("已售罄");
        }

        // 4. 生成订单
        String orderNo = generateOrderNo();
        String qrCodeToken = UUID.randomUUID().toString().replace("-", "");

        // 5. 写入 Redis 缓存
        try {
            Map<String, Object> orderCache = new HashMap<>();
            orderCache.put("orderNo", orderNo);
            orderCache.put("userId", request.getUserId());
            orderCache.put("concertId", request.getConcertId());
            orderCache.put("concertTitle", concert.getTitle());
            orderCache.put("tierId", request.getTierId());
            orderCache.put("tierName", tier.getTierName());
            orderCache.put("price", tier.getPrice());
            orderCache.put("idCard", request.getIdCard());
            orderCache.put("phone", request.getPhone());
            orderCache.put("qrCodeToken", qrCodeToken);
            orderCache.put("payStatus", 1);
            orderCache.put("verifyStatus", 0);
            orderCache.put("venue", concert.getVenue());
            orderCache.put("showTime", concert.getShowTime());

            String json = objectMapper.writeValueAsString(orderCache);
            redisTemplate.opsForValue().set(
                    TicketRedisKey.orderKey(orderNo), json,
                    TicketRedisKey.ORDER_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("写入订单缓存失败", e);
        }

        // 6. 异步写入 MySQL
        asyncInsertOrder(request, orderNo, qrCodeToken, tier);

        // 7. 返回结果
        Map<String, Object> response = new HashMap<>();
        response.put("orderNo", orderNo);
        response.put("qrCodeToken", qrCodeToken);
        response.put("price", tier.getPrice());
        return R.success(response);
    }

    @Async
    public void asyncInsertOrder(TicketBuyRequest request, String orderNo,
                                  String qrCodeToken, TicketTier tier) {
        try {
            TicketOrder order = new TicketOrder();
            order.setOrderNo(orderNo);
            order.setUserId(request.getUserId());
            order.setConcertId(request.getConcertId());
            order.setTierId(request.getTierId());
            order.setTierName(tier.getTierName());
            order.setPrice(tier.getPrice());
            order.setIdCard(request.getIdCard());
            order.setPhone(request.getPhone());
            order.setQrCodeToken(qrCodeToken);
            order.setPayStatus(1);  // 模拟已支付
            order.setVerifyStatus(0);
            ticketOrderMapper.insert(order);
        } catch (Exception e) {
            log.error("异步插入订单失败, orderNo={}", orderNo, e);
            // 回滚: 归还库存 + 释放已购标记
            String stockKey = TicketRedisKey.stockKey(request.getTierId());
            redisTemplate.opsForValue().increment(stockKey);
            String purchasedKey = TicketRedisKey.purchasedKey(
                    request.getUserId(), request.getTierId());
            redisTemplate.delete(purchasedKey);
        }
    }

    // ==================== 订单查询 ====================

    @Override
    public R getOrder(String orderNo) {
        // 优先从 Redis 缓存读取
        String cacheKey = TicketRedisKey.orderKey(orderNo);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                Map<String, Object> orderInfo = objectMapper.readValue(
                        cached.toString(),
                        objectMapper.getTypeFactory()
                                .constructMapType(Map.class, String.class, Object.class));
                return R.success(orderInfo);
            } catch (Exception e) {
                log.warn("订单缓存反序列化失败: {}", e.getMessage());
            }
        }

        // Redis 未命中，查 DB
        QueryWrapper<TicketOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("order_no", orderNo);
        TicketOrder order = ticketOrderMapper.selectOne(wrapper);
        if (order == null) {
            return R.error("订单不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("userId", order.getUserId());
        result.put("concertId", order.getConcertId());
        result.put("tierName", order.getTierName());
        result.put("price", order.getPrice());
        result.put("idCard", order.getIdCard());
        result.put("phone", order.getPhone());
        result.put("qrCodeToken", order.getQrCodeToken());
        result.put("payStatus", order.getPayStatus());
        result.put("verifyStatus", order.getVerifyStatus());
        return R.success(result);
    }

    @Override
    public R getMyOrders(Integer userId) {
        List<Map<String, Object>> orders = ticketOrderMapper.selectOrdersByUser(userId);
        return R.success(orders);
    }

    // ==================== 工具方法 ====================

    private String generateOrderNo() {
        return "TK" + System.currentTimeMillis()
                + String.format("%04d", (int) (Math.random() * 10000));
    }
}
```

---

## 第 3 阶段：Controller + 管理端接口

### 3.1 ConcertController.java
```java
package com.example.yin.controller;

import com.example.yin.common.R;
import com.example.yin.model.request.ConcertRequest;
import com.example.yin.service.ConcertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class ConcertController {

    @Autowired
    private ConcertService concertService;

    @GetMapping("/concert/list")
    public R list(@RequestParam(defaultValue = "1") Integer page,
                  @RequestParam(defaultValue = "10") Integer size,
                  @RequestParam(required = false) Integer status) {
        return concertService.listConcerts(page, size, status);
    }

    @GetMapping("/concert/detail/{id}")
    public R detail(@PathVariable Integer id) {
        return concertService.getDetail(id);
    }
}
```

### 3.2 TicketController.java
```java
package com.example.yin.controller;

import com.example.yin.common.R;
import com.example.yin.model.request.TicketBuyRequest;
import com.example.yin.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ticket")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @PostMapping("/buy")
    public R buy(@RequestBody TicketBuyRequest request) {
        return ticketService.buy(request);
    }

    @GetMapping("/order/{orderNo}")
    public R orderDetail(@PathVariable String orderNo) {
        return ticketService.getOrder(orderNo);
    }

    @GetMapping("/my-orders")
    public R myOrders(@RequestParam Integer userId) {
        return ticketService.getMyOrders(userId);
    }
}
```

### 3.3 VerifyService.java
```java
package com.example.yin.service;

import com.example.yin.common.R;

public interface VerifyService {

    R getVerifyInfo(String qrCodeToken);

    R confirm(String qrCodeToken, Integer operatorId);
}
```

### 3.4 VerifyServiceImpl.java
```java
package com.example.yin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.yin.common.R;
import com.example.yin.constant.TicketRedisKey;
import com.example.yin.mapper.ConcertMapper;
import com.example.yin.mapper.TicketOrderMapper;
import com.example.yin.mapper.TicketTierMapper;
import com.example.yin.mapper.TicketVerificationMapper;
import com.example.yin.model.domain.Concert;
import com.example.yin.model.domain.TicketOrder;
import com.example.yin.model.domain.TicketTier;
import com.example.yin.model.domain.TicketVerification;
import com.example.yin.service.VerifyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class VerifyServiceImpl implements VerifyService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TicketOrderMapper ticketOrderMapper;

    @Autowired
    private TicketVerificationMapper verificationMapper;

    @Autowired
    private ConcertMapper concertMapper;

    @Autowired
    private TicketTierMapper ticketTierMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public R getVerifyInfo(String qrCodeToken) {
        // 先查 Redis 缓存
        TicketOrder order = findByQrToken(qrCodeToken);
        if (order == null) {
            return R.error("无效的二维码");
        }

        if (order.getVerifyStatus() == 1) {
            return R.error("该票已核验入场");
        }

        Concert concert = concertMapper.selectById(order.getConcertId());
        TicketTier tier = ticketTierMapper.selectById(order.getTierId());

        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", order.getOrderNo());
        data.put("concertTitle", concert != null ? concert.getTitle() : "未知");
        data.put("tierName", order.getTierName());
        data.put("idCard", maskIdCard(order.getIdCard()));
        data.put("phone", maskPhone(order.getPhone()));
        data.put("qrCodeToken", qrCodeToken);
        return R.success(data);
    }

    @Override
    @Transactional
    public R confirm(String qrCodeToken, Integer operatorId) {
        TicketOrder order = findByQrToken(qrCodeToken);
        if (order == null) {
            return R.error("无效的二维码");
        }
        if (order.getVerifyStatus() == 1) {
            return R.error("该票已核验，请勿重复操作");
        }

        // 更新订单核验状态
        order.setVerifyStatus(1);
        order.setVerifyTime(new Date());
        ticketOrderMapper.updateById(order);

        // 写入核验记录
        TicketVerification tv = new TicketVerification();
        tv.setOrderId(order.getId());
        tv.setOperatorId(operatorId);
        tv.setVerifyTime(new Date());
        verificationMapper.insert(tv);

        // 更新 Redis 缓存
        try {
            String cacheKey = TicketRedisKey.orderKey(order.getOrderNo());
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                Map<String, Object> map = objectMapper.readValue(
                        cached.toString(),
                        objectMapper.getTypeFactory()
                                .constructMapType(Map.class, String.class, Object.class));
                map.put("verifyStatus", 1);
                redisTemplate.opsForValue().set(cacheKey,
                        objectMapper.writeValueAsString(map));
            }
        } catch (Exception e) {
            log.error("更新核验缓存失败", e);
        }

        return R.success("核验通过，请入场");
    }

    private TicketOrder findByQrToken(String qrCodeToken) {
        // 先遍历 Redis 缓存查找
        QueryWrapper<TicketOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("qr_code_token", qrCodeToken);
        return ticketOrderMapper.selectOne(wrapper);
    }

    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) return "***";
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
```

### 3.5 VerifyController.java
```java
package com.example.yin.controller;

import com.example.yin.common.R;
import com.example.yin.model.request.VerifyRequest;
import com.example.yin.service.VerifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/verify")
public class VerifyController {

    @Autowired
    private VerifyService verifyService;

    @GetMapping("/{qrCodeToken}")
    public R getInfo(@PathVariable String qrCodeToken) {
        return verifyService.getVerifyInfo(qrCodeToken);
    }

    @PostMapping("/confirm")
    public R confirm(@RequestBody VerifyRequest request) {
        return verifyService.confirm(request.getQrCodeToken(), request.getOperatorId());
    }
}
```

### 3.6 AdminController 新增方法

在已有的 `AdminController.java` 中追加以下方法：

```java
// ==================== 演唱会管理 ====================

@Autowired
private ConcertService concertService;

@Autowired
private TicketOrderMapper ticketOrderMapper;

@PostMapping("/concert/add")
public R addConcert(@RequestBody ConcertRequest request) {
    return concertService.addConcert(request);
}

@PutMapping("/concert/update")
public R updateConcert(@RequestBody ConcertRequest request) {
    return concertService.updateConcert(request);
}

@PutMapping("/concert/status")
public R updateConcertStatus(@RequestParam Integer concertId,
                              @RequestParam Integer status) {
    return concertService.updateStatus(concertId, status);
}

@GetMapping("/concert/list")
public R adminConcertList(@RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "10") Integer size) {
    return concertService.listConcerts(page, size, null);
}

// ==================== 订单管理 ====================

@GetMapping("/ticket/orders")
public R ticketOrders(@RequestParam Integer concertId,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "20") Integer size) {
    int offset = (page - 1) * size;
    List<Map<String, Object>> records = ticketOrderMapper
            .selectOrdersByConcert(concertId, offset, size);
    long total = ticketOrderMapper.countOrdersByConcert(concertId);

    Map<String, Object> data = new HashMap<>();
    data.put("records", records);
    data.put("total", total);
    return R.success(data);
}

@GetMapping("/ticket/stats/{concertId}")
public R ticketStats(@PathVariable Integer concertId) {
    // 统计各票档销量
    List<TicketTier> tiers = ticketTierMapper.selectList(
            new LambdaQueryWrapper<TicketTier>().eq(TicketTier::getConcertId, concertId));

    List<Map<String, Object>> stats = new ArrayList<>();
    for (TicketTier tier : tiers) {
        QueryWrapper<TicketOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("concert_id", concertId).eq("tier_id", tier.getId());
        long sold = ticketOrderMapper.selectCount(wrapper);

        Map<String, Object> item = new HashMap<>();
        item.put("tierId", tier.getId());
        item.put("tierName", tier.getTierName());
        item.put("totalStock", tier.getTotalStock());
        item.put("sold", sold);
        item.put("remaining", tier.getTotalStock() - sold);
        stats.add(item);
    }

    return R.success(stats);
}
```

AdminController 额外需要的 import：
```java
import com.example.yin.model.request.ConcertRequest;
import com.example.yin.service.ConcertService;
import com.example.yin.mapper.TicketOrderMapper;
import com.example.yin.mapper.TicketTierMapper;
import com.example.yin.model.domain.TicketOrder;
import com.example.yin.model.domain.TicketTier;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.*;
```

---

## 第 4 阶段：定时任务 + 启动预热 + Admin 页面前端

### 4.1 定时任务 — TicketScheduledTask.java

```java
package com.example.yin.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.yin.constant.TicketRedisKey;
import com.example.yin.mapper.ConcertMapper;
import com.example.yin.mapper.TicketOrderMapper;
import com.example.yin.mapper.TicketTierMapper;
import com.example.yin.model.domain.Concert;
import com.example.yin.model.domain.TicketTier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@EnableScheduling
public class TicketScheduledTask {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ConcertMapper concertMapper;

    @Autowired
    private TicketTierMapper ticketTierMapper;

    @Autowired
    private TicketOrderMapper ticketOrderMapper;

    /**
     * 每 1 分钟刷新状态：预告→售票中，检测售罄
     */
    @Scheduled(cron = "0 * * * * ?")
    public void refreshConcertStatus() {
        List<Concert> concerts = concertMapper.selectList(null);
        for (Concert c : concerts) {
            // 预告→售票中
            if (c.getStatus() == 1 && c.getSaleStartTime() != null
                    && c.getSaleStartTime().getTime() <= System.currentTimeMillis()) {
                c.setStatus(2);
                concertMapper.updateById(c);
                redisTemplate.opsForValue().set(TicketRedisKey.onSaleKey(c.getId()), "1");
                log.info("演唱会 [{}] 开售", c.getTitle());
            }

            // 检测售罄
            if (c.getStatus() == 2) {
                List<TicketTier> tiers = ticketTierMapper.selectList(
                        new LambdaQueryWrapper<TicketTier>()
                                .eq(TicketTier::getConcertId, c.getId()));
                boolean allSoldOut = true;
                for (TicketTier tier : tiers) {
                    String stockKey = TicketRedisKey.stockKey(tier.getId());
                    Object stock = redisTemplate.opsForValue().get(stockKey);
                    if (stock != null && Integer.parseInt(stock.toString()) > 0) {
                        allSoldOut = false;
                        break;
                    }
                }
                if (allSoldOut && !tiers.isEmpty()) {
                    c.setStatus(3);
                    concertMapper.updateById(c);
                    log.info("演唱会 [{}] 售罄", c.getTitle());
                }
            }
        }
    }

    /**
     * 每 5 分钟库存对账：用 DB 校正 Redis 库存
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void reconcileStock() {
        log.info("开始库存对账...");
        List<TicketTier> tiers = ticketTierMapper.selectList(null);
        for (TicketTier tier : tiers) {
            long sold = ticketOrderMapper.selectCount(
                    new LambdaQueryWrapper<com.example.yin.model.domain.TicketOrder>()
                            .eq(com.example.yin.model.domain.TicketOrder::getTierId, tier.getId()));
            int expectedStock = tier.getTotalStock() - (int) sold;
            if (expectedStock < 0) expectedStock = 0;

            String stockKey = TicketRedisKey.stockKey(tier.getId());
            Object redisStock = redisTemplate.opsForValue().get(stockKey);
            int currentRedis = redisStock != null ? Integer.parseInt(redisStock.toString()) : 0;

            if (currentRedis != expectedStock) {
                redisTemplate.opsForValue().set(stockKey, String.valueOf(expectedStock));
                log.warn("库存校正: tierId={}, 期望={}, Redis实际={}",
                        tier.getId(), expectedStock, currentRedis);
            }
        }
        log.info("库存对账完成");
    }
}
```

### 4.2 启动预热 — TicketDataInitializer.java

```java
package com.example.yin.config;

import com.example.yin.constant.TicketRedisKey;
import com.example.yin.mapper.TicketTierMapper;
import com.example.yin.model.domain.TicketTier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class TicketDataInitializer implements CommandLineRunner {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TicketTierMapper ticketTierMapper;

    @Override
    public void run(String... args) {
        List<TicketTier> tiers = ticketTierMapper.selectList(null);
        int initialized = 0;
        for (TicketTier tier : tiers) {
            String stockKey = TicketRedisKey.stockKey(tier.getId());
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(stockKey, String.valueOf(tier.getTotalStock()));
            if (Boolean.TRUE.equals(ok)) {
                initialized++;
            }
        }
        log.info("票档库存预热完成, 共初始化 {} 个新 key", initialized);
    }
}
```

---

## 第 5 阶段：管理端前端页面（music-manage）

### 5.1 `music-manage/src/views/TicketPage.vue`

```vue
<template>
  <div class="ticket-page">
    <!-- 顶部操作栏 -->
    <div class="page-header">
      <h2>演唱会管理</h2>
      <el-button type="primary" @click="showAddDialog">发布演唱会</el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="concerts" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" width="200" />
      <el-table-column prop="singerName" label="歌手" width="120" />
      <el-table-column prop="venue" label="场馆" width="150" />
      <el-table-column prop="showTime" label="演出时间" width="160" />
      <el-table-column prop="saleStartTime" label="开售时间" width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="250" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
          <el-button size="small" @click="showOrders(row)">订单</el-button>
          <el-button size="small" type="warning" @click="showStats(row)">统计</el-button>
          <el-button size="small" type="danger" v-if="row.status !== 0"
                     @click="toggleStatus(row, 0)">下架</el-button>
          <el-button size="small" type="success" v-if="row.status === 0"
                     @click="toggleStatus(row, 1)">上架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      style="margin-top: 20px"
      background layout="prev, pager, next, total"
      :total="total" :page-size="pageSize"
      v-model:current-page="currentPage"
      @current-change="fetchList" />

    <!-- 添加/编辑对话框 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="650px"
               @close="resetForm">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="歌手"><el-input v-model="form.singerName" /></el-form-item>
        <el-form-item label="场馆"><el-input v-model="form.venue" /></el-form-item>
        <el-form-item label="封面图 URL"><el-input v-model="form.coverPic" /></el-form-item>
        <el-form-item label="演出时间">
          <el-date-picker v-model="form.showTime" type="datetime"
                          format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="开售时间">
          <el-date-picker v-model="form.saleStartTime" type="datetime"
                          format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="介绍"><el-input v-model="form.introduction" type="textarea" /></el-form-item>

        <!-- 票档 -->
        <el-form-item label="票档设置">
          <div v-for="(tier, idx) in form.tiers" :key="idx"
               style="display:flex; gap:8px; margin-bottom:6px;">
            <el-input v-model="tier.tierName" placeholder="名称" style="width: 100px" />
            <el-input-number v-model="tier.price" :min="0" :precision="2"
                             placeholder="价格" style="width: 120px" />
            <el-input-number v-model="tier.totalStock" :min="1"
                             placeholder="库存" style="width: 100px" />
            <el-button type="danger" :icon="Delete" circle size="small"
                       @click="form.tiers.splice(idx, 1)" />
          </div>
          <el-button type="primary" size="small" @click="addTier">+ 添加票档</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>

    <!-- 订单查看对话框 -->
    <el-dialog title="订单列表" v-model="orderDialogVisible" width="900px">
      <el-table :data="orders" border stripe v-loading="orderLoading">
        <el-table-column prop="orderNo" label="订单号" width="200" />
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column prop="tierName" label="票档" width="80" />
        <el-table-column prop="price" label="价格" width="80" />
        <el-table-column prop="idCard" label="身份证" width="170" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="支付" width="70">
          <template #default="{ row }">
            <el-tag :type="row.payStatus === 1 ? 'success' : 'info'">
              {{ row.payStatus === 1 ? '已支付' : '未支付' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="核验" width="70">
          <template #default="{ row }">
            <el-tag :type="row.verifyStatus === 1 ? 'success' : 'warning'">
              {{ row.verifyStatus === 1 ? '已核验' : '未核验' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 统计对话框 -->
    <el-dialog title="售票统计" v-model="statsDialogVisible" width="500px">
      <el-table :data="stats" border stripe v-loading="statsLoading">
        <el-table-column prop="tierName" label="票档" />
        <el-table-column prop="totalStock" label="总票数" />
        <el-table-column prop="sold" label="已售" />
        <el-table-column prop="remaining" label="剩余" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Delete } from '@element-plus/icons-vue';
import { HttpManager } from '@/api/index';

const loading = ref(false);
const submitting = ref(false);
const concerts = ref<any[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);

const dialogVisible = ref(false);
const dialogTitle = ref('');
const form = reactive<any>({ tiers: [] });
const editId = ref<number | null>(null);

const orderDialogVisible = ref(false);
const orderLoading = ref(false);
const orders = ref<any[]>([]);
const currentConcertId = ref<number | null>(null);

const statsDialogVisible = ref(false);
const statsLoading = ref(false);
const stats = ref<any[]>([]);

onMounted(() => fetchList());

function fetchList() {
  loading.value = true;
  HttpManager.getAdminConcertList({ page: currentPage.value, size: pageSize.value })
    .then((res: any) => {
      concerts.value = res.data?.records || [];
      total.value = res.data?.total || 0;
    }).finally(() => loading.value = false);
}

function statusText(status: number) {
  const map: Record<number, string> = { 0: '下架', 1: '预告', 2: '售票中', 3: '售罄', 4: '结束' };
  return map[status] || '未知';
}

function statusTag(status: number) {
  const map: Record<number, string> = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: 'info' };
  return map[status] || 'info';
}

function showAddDialog() {
  dialogTitle.value = '发布演唱会';
  editId.value = null;
  Object.assign(form, { title: '', singerName: '', venue: '', coverPic: '',
    showTime: '', saleStartTime: '', introduction: '', tiers: [] });
  dialogVisible.value = true;
}

function showEditDialog(row: any) {
  dialogTitle.value = '编辑演唱会';
  editId.value = row.id;
  Object.assign(form, { ...row, tiers: [] });
  dialogVisible.value = true;
}

function addTier() {
  form.tiers.push({ tierName: '', price: 0, totalStock: 1 });
}

function resetForm() {
  form.tiers = [];
}

function submitForm() {
  submitting.value = true;
  const api = editId.value
    ? HttpManager.updateConcert({ ...form, id: editId.value })
    : HttpManager.addConcert(form);
  api.then(() => {
    ElMessage.success(editId.value ? '修改成功' : '发布成功');
    dialogVisible.value = false;
    fetchList();
  }).finally(() => submitting.value = false);
}

function toggleStatus(row: any, status: number) {
  ElMessageBox.confirm(`确定${status === 0 ? '下架' : '上架'}「${row.title}」？`)
    .then(() => HttpManager.updateConcertStatus({ concertId: row.id, status }))
    .then(() => { ElMessage.success('状态已更新'); fetchList(); });
}

function showOrders(row: any) {
  currentConcertId.value = row.id;
  orderDialogVisible.value = true;
  orderLoading.value = true;
  HttpManager.getTicketOrders({ concertId: row.id, page: 1, size: 100 })
    .then((res: any) => orders.value = res.data?.records || [])
    .finally(() => orderLoading.value = false);
}

function showStats(row: any) {
  statsDialogVisible.value = true;
  statsLoading.value = true;
  HttpManager.getTicketStats(row.id)
    .then((res: any) => stats.value = res.data || [])
    .finally(() => statsLoading.value = false);
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
</style>
```

### 5.2 管理端 API 扩展 — `music-manage/src/api/index.ts`

在 `HttpManager` 对象中追加：

```ts
// ==================== 演唱会管理 ====================
getAdminConcertList: (params: any) => get('concert/list', params),
addConcert: (data: any) => post('concert/add', data),
updateConcert: (data: any) => put('concert/update', data),
updateConcertStatus: (params: any) => put(`concert/status`, params),
getTicketOrders: (params: any) => get('ticket/orders', params),
getTicketStats: (concertId: number) => get(`ticket/stats/${concertId}`),
```

### 5.3 管理端路由扩展 — `music-manage/src/router/index.ts`

添加：
```ts
{
  path: '/ticket',
  name: 'ticket',
  component: () => import('@/views/TicketPage.vue'),
}
```

### 5.4 管理端侧边栏 — `music-manage/src/components/layouts/YinAside.vue`

在侧边栏菜单中添加：
```html
<el-menu-item index="/ticket">
  <el-icon><Ticket /></el-icon>
  <span>演唱会管理</span>
</el-menu-item>
```

对应 import：
```ts
import { Ticket } from '@element-plus/icons-vue';
```

---

## 第 6 阶段：用户端前端页面（music-client）

### 6.1 `music-client/src/views/ticket/Ticket.vue`

```vue
<template>
  <div class="ticket-page">
    <h2 class="page-title">演唱会</h2>

    <!-- 状态标签 -->
    <div class="tab-bar">
      <span v-for="tab in tabs" :key="tab.value"
            :class="['tab', { active: activeTab === tab.value }]"
            @click="switchTab(tab.value)">
        {{ tab.label }}
      </span>
    </div>

    <!-- 卡片网格 -->
    <div v-if="!loading && concerts.length > 0" class="card-grid">
      <div v-for="item in concerts" :key="item.id" class="concert-card"
           @click="goDetail(item.id)">
        <el-image :src="item.coverPic || defaultCover" fit="cover" class="card-img">
          <template #error><div class="img-placeholder">暂无封面</div></template>
        </el-image>
        <div class="card-info">
          <h3>{{ item.title }}</h3>
          <p class="singer">{{ item.singerName || '未知歌手' }}</p>
          <p class="venue">{{ item.venue }}</p>
          <p class="time">{{ formatDate(item.showTime) }}</p>
          <div class="card-footer">
            <span class="price" v-if="item.lowestPrice !== null">
              ¥{{ item.lowestPrice }} 起
            </span>
            <el-tag :type="statusTag(item.status)" size="small">
              {{ statusText(item) }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && concerts.length === 0" description="暂无演唱会" />
    <div v-if="loading" class="loading-wrap"><el-skeleton animated :count="4" /></div>

    <!-- 分页 -->
    <el-pagination v-if="total > pageSize"
      class="pagination" background layout="prev, pager, next, total"
      :total="total" :page-size="pageSize"
      v-model:current-page="currentPage" @current-change="fetchList" />
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { HttpManager } from '@/api/index';

export default defineComponent({
  name: 'Ticket',
  setup() {
    const router = useRouter();
    const loading = ref(false);
    const concerts = ref<any[]>([]);
    const total = ref(0);
    const currentPage = ref(1);
    const pageSize = ref(12);
    const activeTab = ref<number | ''>('');

    const tabs = [
      { label: '全部', value: '' },
      { label: '预告中', value: 1 },
      { label: '售票中', value: 2 },
      { label: '已售罄', value: 3 },
    ];

    const defaultCover = 'https://cube.elemecdn.com/6/94/4d3ea53c084bad6931a56d5158a48jpeg.jpeg';

    onMounted(() => fetchList());

    function fetchList() {
      loading.value = true;
      const params: any = { page: currentPage.value, size: pageSize.value };
      if (activeTab.value !== '') params.status = activeTab.value;
      HttpManager.getConcertList(params)
        .then((res: any) => {
          concerts.value = res.data?.records || [];
          total.value = res.data?.total || 0;
        }).finally(() => loading.value = false);
    }

    function switchTab(val: number | '') {
      activeTab.value = val;
      currentPage.value = 1;
      fetchList();
    }

    function goDetail(id: number) {
      router.push(`/ticket/${id}`);
    }

    function formatDate(dateStr: string) {
      if (!dateStr) return '';
      return new Date(dateStr).toLocaleDateString('zh-CN');
    }

    function statusText(item: any) {
      // 预告但已过开售时间 → 显示为售票中
      // 组件间 status 是 DB 里实时刷新的，直接用 DB 值
      const map: Record<number, string> = { 0: '下架', 1: '预告', 2: '售票中', 3: '售罄', 4: '已结束' };
      return map[item.status] || '未知';
    }

    function statusTag(status: number) {
      const map: Record<number, string> = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: 'info' };
      return map[status] || 'info';
    }

    return { loading, concerts, total, currentPage, pageSize, activeTab, tabs,
             defaultCover, switchTab, goDetail, formatDate, statusText, statusTag, fetchList };
  }
});
</script>

<style scoped>
.ticket-page { padding: 20px; max-width: 1200px; margin: 0 auto; }
.page-title { font-size: 24px; margin-bottom: 16px; }
.tab-bar { display: flex; gap: 12px; margin-bottom: 20px; }
.tab { padding: 6px 16px; border-radius: 20px; background: #f5f5f5; cursor: pointer;
       font-size: 14px; transition: all 0.2s; }
.tab.active { background: #409eff; color: #fff; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
              gap: 20px; }
.concert-card { border-radius: 8px; overflow: hidden; box-shadow: 0 2px 12px rgba(0,0,0,0.08);
                cursor: pointer; transition: transform 0.2s; background: #fff; }
.concert-card:hover { transform: translateY(-4px); }
.card-img { width: 100%; height: 180px; }
.img-placeholder { width: 100%; height: 180px; display: flex; align-items: center;
                   justify-content: center; background: #eee; color: #999; font-size: 14px; }
.card-info { padding: 12px 16px 16px; }
.card-info h3 { font-size: 16px; margin-bottom: 6px; overflow: hidden;
                text-overflow: ellipsis; white-space: nowrap; }
.singer { color: #666; font-size: 13px; margin-bottom: 4px; }
.venue, .time { color: #999; font-size: 12px; margin-bottom: 2px; }
.card-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
.price { color: #f56c6c; font-size: 18px; font-weight: bold; }
.loading-wrap { padding: 40px; }
.pagination { margin-top: 24px; display: flex; justify-content: center; }
</style>
```

### 6.2 `music-client/src/views/ticket/TicketDetail.vue`

```vue
<template>
  <div class="ticket-detail" v-loading="loading">
    <div class="back-btn" @click="$router.back()">&larr; 返回</div>

    <div class="detail-layout" v-if="concert">
      <!-- 左侧信息 -->
      <div class="left-panel">
        <el-image :src="concert.coverPic || defaultCover" fit="cover" class="cover-img">
          <template #error><div class="img-placeholder">暂无封面</div></template>
        </el-image>
        <h1>{{ concert.title }}</h1>
        <p class="info-row"><strong>歌手：</strong>{{ concert.singerName || '未知' }}</p>
        <p class="info-row"><strong>场馆：</strong>{{ concert.venue }}</p>
        <p class="info-row"><strong>时间：</strong>{{ formatDateTime(concert.showTime) }}</p>
        <p class="info-row" v-if="concert.saleStartTime">
          <strong>开售：</strong>{{ formatDateTime(concert.saleStartTime) }}
        </p>
        <p class="intro" v-if="concert.introduction">{{ concert.introduction }}</p>
      </div>

      <!-- 右侧票档 -->
      <div class="right-panel">
        <h2>选择票档</h2>
        <div v-for="tier in tiers" :key="tier.id" class="tier-card">
          <div class="tier-info">
            <span class="tier-name">{{ tier.tierName }}</span>
            <span class="tier-price">¥{{ tier.price }}</span>
          </div>
          <div class="tier-stock" :class="{ low: tier.remainingStock <= 10 }">
            剩余 {{ tier.remainingStock }} 张
          </div>
          <!-- 倒计时按钮 -->
          <el-button v-if="isBeforeSale" type="warning" disabled class="tier-btn">
            距开售 {{ countdown }}
          </el-button>
          <!-- 可购买 -->
          <el-button v-else-if="tier.remainingStock > 0 && concert.status === 2"
                     type="primary" class="tier-btn" @click="showBuyDialog(tier)">
            立即购买
          </el-button>
          <!-- 售罄 -->
          <el-button v-else disabled class="tier-btn">已售罄</el-button>
        </div>
        <el-empty v-if="tiers.length === 0" description="暂无票档" />
      </div>
    </div>

    <!-- 购票弹窗 -->
    <el-dialog title="确认购票" v-model="buyDialogVisible" width="420px">
      <div class="order-summary">
        <p><strong>{{ concert?.title }}</strong></p>
        <p>{{ selectedTier?.tierName }} — ¥{{ selectedTier?.price }}</p>
      </div>
      <el-form :model="buyForm" label-width="80px" style="margin-top: 16px;">
        <el-form-item label="身份证号">
          <el-input v-model="buyForm.idCard" placeholder="请输入身份证号" maxlength="18" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="buyForm.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="buyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="buying" @click="doBuy">确认购买</el-button>
      </template>
    </el-dialog>

    <!-- 结果弹窗 -->
    <el-dialog title="购票结果" v-model="resultVisible" width="420px" :close-on-click-modal="false">
      <div v-if="result.success" class="result-success">
        <p style="font-size: 18px; color: #67c23a;">购票成功</p>
        <p>订单号：{{ result.orderNo }}</p>
        <p>金额：¥{{ result.price }}</p>
        <el-button type="primary" @click="goOrder">查看订单 & 二维码</el-button>
      </div>
      <div v-else class="result-fail">
        <p style="font-size: 18px; color: #f56c6c;">购票失败</p>
        <p>{{ result.message }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, reactive, onMounted, onUnmounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { HttpManager } from '@/api/index';
import { ElMessage } from 'element-plus';

export default defineComponent({
  name: 'TicketDetail',
  setup() {
    const route = useRoute();
    const router = useRouter();
    const loading = ref(false);
    const concert = ref<any>(null);
    const tiers = ref<any[]>([]);

    // 购票
    const buyDialogVisible = ref(false);
    const selectedTier = ref<any>(null);
    const buyForm = reactive({ idCard: '', phone: '' });
    const buying = ref(false);

    // 结果
    const resultVisible = ref(false);
    const result = reactive({ success: false, orderNo: '', price: 0, message: '' });

    // 倒计时
    const countdown = ref('');
    let timer: any = null;

    const defaultCover = 'https://cube.elemecdn.com/6/94/4d3ea53c084bad6931a56d5158a48jpeg.jpeg';

    onMounted(() => {
      fetchDetail();
    });

    onUnmounted(() => {
      if (timer) clearInterval(timer);
    });

    function fetchDetail() {
      loading.value = true;
      const id = route.params.id;
      HttpManager.getConcertDetail(id as string)
        .then((res: any) => {
          concert.value = res.data?.concert;
          tiers.value = res.data?.tiers || [];
          startCountdown();
        }).finally(() => loading.value = false);
    }

    const isBeforeSale = computed(() => {
      if (!concert.value?.saleStartTime) return false;
      return new Date(concert.value.saleStartTime).getTime() > Date.now();
    });

    function startCountdown() {
      if (!isBeforeSale.value) return;
      if (timer) clearInterval(timer);
      timer = setInterval(() => {
        const now = Date.now();
        const target = new Date(concert.value.saleStartTime).getTime();
        const diff = target - now;
        if (diff <= 0) {
          countdown.value = '';
          clearInterval(timer);
          return;
        }
        const h = Math.floor(diff / 3600000);
        const m = Math.floor((diff % 3600000) / 60000);
        const s = Math.floor((diff % 60000) / 1000);
        countdown.value = `${h}时${String(m).padStart(2, '0')}分${String(s).padStart(2, '0')}秒`;
      }, 1000);
    }

    function showBuyDialog(tier: any) {
      selectedTier.value = tier;
      buyForm.idCard = '';
      buyForm.phone = '';
      buyDialogVisible.value = true;
    }

    function doBuy() {
      if (!buyForm.idCard || !buyForm.phone) {
        ElMessage.warning('请填写身份证号和手机号');
        return;
      }
      const userInfo = JSON.parse(
        sessionStorage.getItem('user') || localStorage.getItem('user') || '{}');
      if (!userInfo.id) {
        // 尝试从 store 获取
        ElMessage.warning('请先登录');
        return;
      }

      buying.value = true;
      HttpManager.buyTicket({
        concertId: concert.value.id,
        tierId: selectedTier.value.id,
        userId: userInfo.id,
        idCard: buyForm.idCard,
        phone: buyForm.phone,
      }).then((res: any) => {
        buyDialogVisible.value = false;
        if (res.success) {
          result.success = true;
          result.orderNo = res.data.orderNo;
          result.price = res.data.price;
        } else {
          result.success = false;
          result.message = res.message;
        }
        resultVisible.value = true;
      }).finally(() => buying.value = false);
    }

    function goOrder() {
      resultVisible.value = false;
      router.push(`/ticket/order/${result.orderNo}`);
    }

    function formatDateTime(dateStr: string) {
      if (!dateStr) return '';
      return new Date(dateStr).toLocaleString('zh-CN');
    }

    return { loading, concert, tiers, defaultCover, isBeforeSale, countdown,
             buyDialogVisible, selectedTier, buyForm, buying, doBuy, showBuyDialog,
             resultVisible, result, goOrder, formatDateTime };
  }
});
</script>

<style scoped>
.ticket-detail { padding: 20px; max-width: 1000px; margin: 0 auto; }
.back-btn { cursor: pointer; color: #409eff; margin-bottom: 16px; font-size: 14px; }
.detail-layout { display: flex; gap: 30px; }
.left-panel { flex: 1; }
.left-panel h1 { font-size: 24px; margin: 16px 0 12px; }
.cover-img { width: 400px; height: 250px; border-radius: 8px; }
.img-placeholder { width: 400px; height: 250px; display: flex; align-items: center;
                   justify-content: center; background: #eee; color: #999; border-radius: 8px; }
.info-row { margin-bottom: 6px; color: #555; }
.intro { margin-top: 16px; color: #888; line-height: 1.6; }
.right-panel { width: 360px; }
.right-panel h2 { font-size: 18px; margin-bottom: 16px; }
.tier-card { display: flex; align-items: center; padding: 14px 16px; border: 1px solid #eee;
             border-radius: 8px; margin-bottom: 10px; transition: border-color 0.2s; }
.tier-card:hover { border-color: #409eff; }
.tier-info { flex: 1; }
.tier-name { font-size: 15px; font-weight: 500; display: block; }
.tier-price { font-size: 20px; font-weight: bold; color: #f56c6c; }
.tier-stock { font-size: 12px; color: #999; min-width: 80px; text-align: center; }
.tier-stock.low { color: #f56c6c; }
.tier-btn { min-width: 100px; }
.order-summary { background: #f8f8f8; padding: 12px; border-radius: 6px; }
.order-summary p { margin-bottom: 4px; }
.result-success, .result-fail { text-align: center; }
.result-success p, .result-fail p { margin-bottom: 8px; }
</style>
```

### 6.3 `music-client/src/views/ticket/TicketOrder.vue`

```vue
<template>
  <div class="order-page" v-loading="loading">
    <div class="back-btn" @click="$router.push('/ticket')">&larr; 返回演唱会列表</div>

    <div class="order-card" v-if="order">
      <h2>订单详情</h2>
      <div class="order-info">
        <div class="info-item"><label>订单号</label><span>{{ order.orderNo }}</span></div>
        <div class="info-item"><label>演唱会</label><span>{{ order.concertTitle || '未知' }}</span></div>
        <div class="info-item"><label>票档</label><span>{{ order.tierName }}</span></div>
        <div class="info-item"><label>金额</label><span class="price">¥{{ order.price }}</span></div>
        <div class="info-item"><label>身份证</label><span>{{ maskIdCard(order.idCard) }}</span></div>
        <div class="info-item"><label>手机号</label><span>{{ maskPhone(order.phone) }}</span></div>
        <div class="info-item">
          <label>状态</label>
          <el-tag :type="order.verifyStatus === 1 ? 'success' : 'warning'">
            {{ order.verifyStatus === 1 ? '已入场' : '未核验' }}
          </el-tag>
        </div>
      </div>

      <div class="qrcode-section">
        <h3>入场二维码</h3>
        <div class="qr-wrapper">
          <canvas ref="qrCanvas"></canvas>
        </div>
        <p class="qr-hint">入场时请出示此二维码供工作人员扫码核验</p>
      </div>
    </div>

    <el-empty v-if="!loading && !order" description="订单不存在" />
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, onMounted, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import { HttpManager } from '@/api/index';
import QRCode from 'qrcode';

export default defineComponent({
  name: 'TicketOrder',
  setup() {
    const route = useRoute();
    const loading = ref(false);
    const order = ref<any>(null);
    const qrCanvas = ref<HTMLCanvasElement | null>(null);

    onMounted(() => {
      fetchOrder();
    });

    function fetchOrder() {
      loading.value = true;
      const orderNo = route.params.orderNo;
      HttpManager.getTicketOrder(orderNo as string)
        .then((res: any) => {
          if (res.success) {
            order.value = res.data;
            nextTick(() => generateQR(res.data?.qrCodeToken));
          }
        }).finally(() => loading.value = false);
    }

    function generateQR(token: string) {
      if (!token || !qrCanvas.value) return;
      const verifyUrl = `${window.location.origin}/api/verify/${token}`;
      QRCode.toCanvas(qrCanvas.value, verifyUrl, {
        width: 220,
        margin: 2,
        color: { dark: '#000000', light: '#ffffff' },
        errorCorrectionLevel: 'M',
      });
    }

    function maskIdCard(idCard: string) {
      if (!idCard || idCard.length < 8) return '***';
      return idCard.slice(0, 4) + '**********' + idCard.slice(-4);
    }

    function maskPhone(phone: string) {
      if (!phone || phone.length < 7) return '***';
      return phone.slice(0, 3) + '****' + phone.slice(-4);
    }

    return { loading, order, qrCanvas, maskIdCard, maskPhone };
  }
});
</script>

<style scoped>
.order-page { padding: 20px; max-width: 700px; margin: 0 auto; }
.back-btn { cursor: pointer; color: #409eff; margin-bottom: 16px; font-size: 14px; }
.order-card { background: #fff; border-radius: 12px; padding: 30px;
              box-shadow: 0 2px 16px rgba(0,0,0,0.08); }
.order-card h2 { font-size: 22px; margin-bottom: 24px; }
.order-info { display: grid; gap: 12px; }
.info-item { display: flex; justify-content: space-between; padding: 8px 0;
             border-bottom: 1px solid #f0f0f0; }
.info-item label { color: #888; }
.info-item span { font-weight: 500; }
.price { color: #f56c6c; font-size: 18px; font-weight: bold !important; }
.qrcode-section { margin-top: 30px; text-align: center; }
.qrcode-section h3 { margin-bottom: 16px; }
.qr-wrapper { display: inline-block; padding: 12px; border: 2px solid #eee; border-radius: 8px; }
.qr-hint { margin-top: 12px; font-size: 13px; color: #999; }
</style>
```

### 6.4 用户端 API 扩展 — `music-client/src/api/index.ts`

在 `HttpManager` 对象中追加：

```ts
// ==================== 购票 ====================
getConcertList: (params: any) => get('concert/list', params),
getConcertDetail: (id: string | number) => get(`concert/detail/${id}`),
buyTicket: (data: any) => post('ticket/buy', data),
getTicketOrder: (orderNo: string) => get(`ticket/order/${orderNo}`),
getMyTicketOrders: (userId: number) => get('ticket/my-orders', { userId }),
```

### 6.5 用户端路由扩展 — `music-client/src/router/index.ts`

在 `YinContainer` 的 children 数组中添加：

```ts
{
  path: '/ticket',
  name: 'ticket',
  component: () => import('@/views/ticket/Ticket.vue'),
},
{
  path: '/ticket/:id',
  name: 'ticket-detail',
  component: () => import('@/views/ticket/TicketDetail.vue'),
},
{
  path: '/ticket/order/:orderNo',
  name: 'ticket-order',
  component: () => import('@/views/ticket/TicketOrder.vue'),
},
```

### 6.6 导航栏扩展 — `music-client/src/enums/router-name.ts`

在 `RouterName` 枚举中添加：
```ts
Ticket = '/ticket',
```

### 6.7 导航栏扩展 — `music-client/src/enums/nav.ts`

在 `HEADERNAVLIST` 数组中添加（放在 Rank 后面）：
```ts
{ name: '购票', path: RouterName.Ticket },
```

### 6.8 安装依赖

```bash
# 在 music-client 目录下
npm install qrcode
```

---

## 实现顺序总结

| 阶段 | 内容 | 文件数 |
|------|------|--------|
| 1（已给） | SQL + Domain + Mapper + Request + Redis常量 | 13 |
| 2（本文第2节） | Service 接口 + 实现 | 4 |
| 3（本文第3节） | Controller + 管理端接口 + Verify | 5 |
| 4（本文第4节） | 定时任务 + 启动预热 | 2 |
| 5（本文第5节） | 管理端前端（music-manage）| 4 |
| 6（本文第6节） | 用户端前端（music-client）| 8 |

全部代码已给出，按阶段逐一创建即可。创建完毕后重启后端服务，前后端可联调测试。
