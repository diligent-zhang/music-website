# 购票功能设计文档

**日期**：2026-05-22
**状态**：设计完成，待用户审阅

---

## 1. 功能概述

在音乐网站新增购票功能，用户可浏览演唱会信息、选择票档、购买门票，生成二维码供线下核验。需解决高并发抢票场景下的超卖、请求雪崩、并发锁、非阻塞等性能问题。

## 2. 核心决策

| 决策项 | 选择 |
|--------|------|
| 入口位置 | 顶部导航栏新增"购票"Tab（与首页/歌单/歌手/排行榜并列） |
| 支付方式 | 模拟支付，代码结构预留扩展点 |
| 票档模型 | 一场演唱会多种票档（如VIP区/A区/B区），独立库存 |
| 限购规则 | 每用户每票档限购 1 张 |
| 开售时间 | 前端展示倒计时，无后端强控 |
| 核验方式 | 独立核验页面，现场扫码确认入场 |

## 3. 技术方案

### 3.1 抢票核心：Redis 库存 + Lua 原子扣减

- 库存数据放在 Redis 中，扣库存使用 Lua 脚本保证原子性
- Redis 单线程特性天然无锁，Lua 脚本内 GET + DECR 不可分割
- 订单异步写入 MySQL，不阻塞请求线程（~5ms 响应）
- Redis 和 MySQL 最终一致性，定时任务对账修复

### 3.2 防超卖（三位一体）

1. **Redis Lua 原子扣减**：第一道防线
2. **MySQL 唯一索引 `uk_user_tier`**：兜底拦截
3. **定时对账任务**：每 5 分钟 `total_stock - COUNT(order)` 校正 Redis 库存

### 3.3 防雪崩

- Nginx `limit_req_zone`：单 IP 每秒 5 次
- 应用层令牌桶：全局 QPS 上限 5000，单用户每秒 1 次购票
- Lettuce 连接池限制：max-active=50，连接耗尽快速失败
- Redis 不可用时降级返回"系统繁忙"，不穿透 MySQL

### 3.4 无锁并发

关键路径不使用 `synchronized` 或分布式锁：
- 用户去重：`SETNX` 原子操作
- 库存扣减：Lua 脚本原子执行
- 重复请求：前端按钮防抖 + 后端 SETNX 双重防护

---

## 4. 数据库设计

### 4.1 新增表

```sql
-- 演唱会信息
CREATE TABLE `concert` (
  `id`              INT(10) UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`           VARCHAR(255) NOT NULL,
  `singer_id`       INT(10) UNSIGNED DEFAULT NULL,
  `singer_name`     VARCHAR(100) DEFAULT NULL,
  `venue`           VARCHAR(255) NOT NULL,
  `cover_pic`       VARCHAR(255) DEFAULT NULL,
  `show_time`       DATETIME NOT NULL,
  `sale_start_time` DATETIME DEFAULT NULL,
  `introduction`    TEXT DEFAULT NULL,
  `status`          TINYINT NOT NULL DEFAULT 1 COMMENT '0=下架,1=预告,2=售票中,3=售罄,4=结束',
  `create_time`     DATETIME NOT NULL,
  `update_time`     DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_singer_id` (`singer_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 票档
CREATE TABLE `ticket_tier` (
  `id`           INT(10) UNSIGNED NOT NULL AUTO_INCREMENT,
  `concert_id`   INT(10) UNSIGNED NOT NULL,
  `tier_name`    VARCHAR(100) NOT NULL,
  `price`        DECIMAL(10,2) NOT NULL,
  `total_stock`  INT(10) UNSIGNED NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_concert_id` (`concert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 订单
CREATE TABLE `ticket_order` (
  `id`            INT(10) UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`      VARCHAR(32) NOT NULL,
  `user_id`       INT(10) UNSIGNED NOT NULL,
  `concert_id`    INT(10) UNSIGNED NOT NULL,
  `tier_id`       INT(10) UNSIGNED NOT NULL,
  `tier_name`     VARCHAR(100) NOT NULL,
  `price`         DECIMAL(10,2) NOT NULL,
  `id_card`       VARCHAR(18) NOT NULL,
  `phone`         VARCHAR(15) NOT NULL,
  `qr_code_token` VARCHAR(64) NOT NULL,
  `pay_status`    TINYINT NOT NULL DEFAULT 0 COMMENT '0=待支付,1=已支付,2=已取消',
  `verify_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未核验,1=已核验',
  `verify_time`   DATETIME DEFAULT NULL,
  `create_time`   DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_user_tier` (`user_id`, `concert_id`, `tier_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_qr_token` (`qr_code_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 核验记录
CREATE TABLE `ticket_verification` (
  `id`           INT(10) UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`     INT(10) UNSIGNED NOT NULL,
  `operator_id`  INT(10) UNSIGNED DEFAULT NULL,
  `verify_time`  DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
```

---

## 5. Redis 键设计

```
# 库存计数器（String，初始 = total_stock）
ticket:stock:{tierId}              →  "500"

# 用户已购标记（防重复下单）
ticket:purchased:{userId}:{tierId} →  "1"

# 订单详情缓存（TTL = 演唱会结束后 7 天）
ticket:order:{orderNo}             →  JSON

# 开售状态标记
ticket:on_sale:{concertId}         →  "1"
```

---

## 6. 抢票核心流程

```
用户请求 POST /ticket/buy
      │
      ▼
  ① 参数校验：concertId、tierId 有效性，status，开售时间
      │
      ▼
  ② SETNX ticket:purchased:{uid}:{tierId}
     → 0: 已购买，拒绝
     → 1: 继续
      │
      ▼
  ③ Lua 脚本：GET stock → if > 0 then DECR → return 1 else return 0
     → 0: 售罄
     → 1: 抢到
      │
      ▼
  ④ 生成 orderNo + qrCodeToken
     写入 Redis ticket:order:{orderNo}
     @Async 异步 INSERT MySQL
     返回成功（含订单号、二维码token）
```

回滚策略：MySQL 插入失败时 INCR 归还库存 + DEL 释放已购标记。定时任务每 5 分钟对账校正。

---

## 7. API 设计

### 7.1 用户端

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/concert/list?page=&size=&status=` | 演唱会列表 |
| `GET` | `/concert/detail/{id}` | 演唱会详情（含票档+实时库存） |
| `POST` | `/ticket/buy` | 购票 |
| `GET` | `/ticket/order/{orderNo}` | 订单详情 |
| `GET` | `/ticket/my-orders?userId=` | 我的订单 |

### 7.2 核验端

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/verify/{qrCodeToken}` | 查询核验信息 |
| `POST` | `/verify/confirm` | 确认入场 |

### 7.3 管理端

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/admin/concert/add` | 发布演唱会 |
| `PUT` | `/admin/concert/update` | 修改演唱会 |
| `PUT` | `/admin/concert/status` | 状态变更 |
| `GET` | `/admin/concert/list` | 管理端列表 |
| `GET` | `/admin/ticket/orders` | 某演唱会订单 |
| `GET` | `/admin/ticket/stats/{concertId}` | 售票统计 |

---

## 8. 前端设计

### 8.1 路由

| 路径 | 视图 | 说明 |
|------|------|------|
| `/ticket` | `Ticket.vue` | 演唱会列表 |
| `/ticket/:id` | `TicketDetail.vue` | 详情+选票+下单 |
| `/ticket/order/:orderNo` | `TicketOrder.vue` | 订单详情+二维码 |

在 `HEADERNAVLIST` 中新增 `{ name: "购票", path: "/ticket" }`。

### 8.2 页面功能

- **Ticket.vue**：标签切换（全部/预告中/售票中/已售罄）、卡片网格、分页
- **TicketDetail.vue**：演唱详情、票档选择、倒计时/购买按钮、下单表单（身份证+手机号）
- **TicketOrder.vue**：订单信息、二维码展示（前端 `qrcode` 包生成，内容为 verify URL）

---

## 9. 后端代码结构

按项目现有模式新增：

```
model/domain/      Concert.java, TicketTier.java, TicketOrder.java, TicketVerification.java
model/request/     ConcertRequest.java, TicketBuyRequest.java, VerifyRequest.java
mapper/            ConcertMapper.java, TicketTierMapper.java, TicketOrderMapper.java, TicketVerificationMapper.java
service/           ConcertService.java, TicketService.java, VerifyService.java
service/impl/      ConcertServiceImpl.java, TicketServiceImpl.java, VerifyServiceImpl.java
controller/        ConcertController.java, TicketController.java, VerifyController.java
config/            TicketRedisKey.java (常量)
task/              TicketStockReconciliationTask.java (对账任务)
```

---

## 10. 对账与运维

### 定时任务

| 任务 | 频率 | 说明 |
|------|------|------|
| 库存对账 | 每 5 分钟 | `tier.total_stock - COUNT(order)` 与 Redis 库存对比，不一致则校正 |
| 状态刷新 | 每 1 分钟 | 扫描 `status=1(预告)` 且 `sale_start_time <= now` 的演唱会，更新为 `status=2(售票中)` |
| 售罄标记 | 每 1 分钟 | 检测库存为 0 的票档所属演唱会，标记 `status=3(售罄)` |
| 过期清理 | 每天 | 清理已结束演唱会的 Redis 缓存 |

### 缓存预热

服务启动时（`CommandLineRunner`，复用 `RankDataInitializer` 模式）：
- 从 `ticket_tier` 表加载 `total_stock` 写入 Redis `ticket:stock:{tierId}`
- 仅写入不存在的 key（`SETNX`），避免覆盖运行时库存
