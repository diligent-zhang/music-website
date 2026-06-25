package com.example.yin.controller;

import com.example.yin.common.R;
import com.example.yin.model.request.ConcertRequest;
import com.example.yin.service.ConcertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端 - 演唱会浏览接口
 * 提供演唱会列表查询和详情查看功能
 */
@RestController
public class ConcertController {

    @Autowired
    private ConcertService concertService;

    /**
     * 分页查询演唱会列表
     * @param page   页码，默认第 1 页
     * @param size   每页条数，默认 10 条
     * @param status 可选状态筛选：1=预告, 2=售票中, 3=售罄, 4=结束
     * @return 分页数据 { records, total }
     */
    @GetMapping("/concert/list")
    public R list(@RequestParam(defaultValue = "1") Integer page,
                  @RequestParam(defaultValue = "10") Integer size,
                  @RequestParam(required = false) Integer status) {
        return concertService.listConcerts(page, size, status,true);
    }

    /**
     * 查询演唱会详情（含票档列表和各票档实时库存）
     * 实时库存从 Redis 读取，保证高并发下的数据一致性
     * @param id 演唱会 ID
     * @return { concert, tiers: [{id, tierName, price, totalStock, remainingStock}] }
     */
    @GetMapping("/concert/detail/{id}")
    public R detail(@PathVariable Integer id) {
        return concertService.getDetail(id);
    }
}