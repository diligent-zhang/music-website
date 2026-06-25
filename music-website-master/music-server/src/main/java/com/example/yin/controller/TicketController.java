package com.example.yin.controller;


import com.example.yin.common.R;
import com.example.yin.model.request.TicketBuyRequest;
import com.example.yin.service.TicketService;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ticket")
public class TicketController {

    @Autowired
    private TicketService ticketService;
    /**
     * 购票下单（核心抢票入口）
     * 流程：用户去重(SETNX) → Lua 原子扣库存 → 生成订单 → Redis缓存 → 异步写MySQL
     * 技术要点：
     *   - Redis Lua 脚本保证库存扣减的原子性，避免超卖
     *   - SETNX 实现用户维度的幂等性，同一用户同一票档只允许一次成功下单
     *   - 订单异步写入 MySQL，不阻塞请求线程，响应时间约 5ms
     * @param request { concertId, tierId, userId, idCard, phone }
     * @return { orderNo, qrCodeToken, price }
     */
    @PostMapping("/buy")
    public R buy(@RequestBody TicketBuyRequest request){
        return ticketService.buy(request);
    }
    /**
     * 查询单个订单详情
     * 优先从 Redis 缓存读取（TTL 7天），缓存未命中时回源 MySQL
     * @param orderNo 订单号
     * @return 订单完整信息（含二维码 token）
     */
    @GetMapping("/order/{orderNo}")
    public R orderDetail(@PathVariable String orderNo){
        return ticketService.getOrder(orderNo);
    }
    /**
     * 查询用户的所有购票订单
     * 关联 concert 表返回演唱会标题、演出时间、场馆等展示信息
     * @param userId 用户 ID
     * @return 订单列表，按创建时间倒序
     */
    @GetMapping("/my-orders")
    public R myOrders(@RequestParam Integer userId) {
        return ticketService.getMyOrders(userId);
    }
    /**
     * 用户取消自己的订单
     */
    @DeleteMapping("/order/{id}")
    public R cancelOrder(@PathVariable Integer id,
                         @RequestParam Integer userId) {
        return ticketService.cancelOrderByUser(id, userId);
    }
}
