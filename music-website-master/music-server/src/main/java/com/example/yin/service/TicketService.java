package com.example.yin.service;

import com.example.yin.common.R;
import com.example.yin.model.request.TicketBuyRequest;

/**
 * 购票服务接口
 * 核心业务：高并发抢票场景下的库存扣减、订单生成、订单查询
 */
public interface TicketService {

    /**
     * 购票（抢票核心接口）
     * 流程：参数校验 → 用户去重（SETNX）→ Lua原子扣库存 → 生成订单 → Redis缓存 → 异步写MySQL
     * 不继承 IService，因为 TicketOrder 的增删改查逻辑都在本接口内部封装，
     * 外部不应直接操作 TicketOrder 表
     */
    R buy(TicketBuyRequest request);

    /**
     * 查询单个订单详情
     * 查询策略：先查 Redis 缓存（内存级速度），未命中再查 MySQL
     * 使用场景：用户下单后跳转订单页、入场核验扫码时展示订单信息
     */
    R getOrder(String orderNo);

    /**
     * 查询某用户的所有购票订单
     * 委托给 TicketOrderMapper.selectOrdersByUser（关联查询用户名等信息）
     */
    R getMyOrders(Integer userId);

    /**
     * 删除订单（管理员操作）
     * 清理 MySQL 记录 + 归还 Redis 库存 + 释放已购标记 + 删除订单缓存
     */
    R cancelOrder(Integer orderId);

    /**
     * 用户取消订单
     * 与管理员删除逻辑相同，但需要校验 userId 归属
     */
    R cancelOrderByUser(Integer orderId, Integer userId);
}