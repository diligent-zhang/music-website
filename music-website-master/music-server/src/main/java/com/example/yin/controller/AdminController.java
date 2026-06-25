package com.example.yin.controller;

import com.example.yin.common.R;
import com.example.yin.mapper.PlayLogMapper;
import com.example.yin.model.request.AdminRequest;
import com.example.yin.service.AdminService;
import com.example.yin.service.RankService;
import com.example.yin.service.TicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.yin.model.request.ConcertRequest;
import com.example.yin.service.ConcertService;
import com.example.yin.mapper.TicketOrderMapper;
import com.example.yin.mapper.TicketTierMapper;
import com.example.yin.model.domain.TicketOrder;
import com.example.yin.model.domain.TicketTier;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.*;

@RestController
@Slf4j
public class AdminController {
    @Autowired
    private AdminService adminService;

    @Autowired
    private RankService rankService;

    @Autowired
    private PlayLogMapper playLogMapper;
    // ==================== 购票管理：新增依赖注入 ====================
    @Autowired
    private ConcertService concertService;

    @Autowired
    private TicketOrderMapper ticketOrderMapper;

    @Autowired
    private TicketTierMapper ticketTierMapper;
    // 判断是否登录成功
    @PostMapping("/admin/login/status")
    public R loginStatus(@RequestBody AdminRequest adminRequest, HttpSession session) {
        return adminService.verityPasswd(adminRequest, session);
    }

    // ==================== 排行榜管理 ====================

    // 分页查询播放记录
    @GetMapping("/admin/playLogs")
    public R getPlayLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String songName,
            @RequestParam(required = false) Integer userId) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = playLogMapper
                .selectPlayLogs(offset, size, songName, userId);
        long total = playLogMapper.countPlayLogs(songName, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return R.success(null, result);
    }

    // 删除单条播放记录
    @DeleteMapping("/admin/playLog/{id}")
    public R deletePlayLog(@PathVariable Long id) {
        playLogMapper.deleteById(id);
        return R.success("删除成功");
    }

    // 手动修改播放次数
    @PutMapping("/admin/rank/playCount")
    public R updatePlayCount(@RequestBody Map<String, Object> body) {
        Integer songId = (Integer) body.get("songId");
        String type = (String) body.get("type");
        Long playCount = ((Number) body.get("playCount")).longValue();
        rankService.updatePlayCount(songId, type, playCount);
        return R.success("修改成功");
    }

    // 手动重置榜单
    @PostMapping("/admin/rank/reset")
    public R resetRank(@RequestBody Map<String, String> body) {
        String type = body.get("type");
        rankService.resetRank(type);
        return R.success("重置成功");
    }

    // 导出榜单 CSV
    @GetMapping("/admin/rank/export")
    public R exportRank(@RequestParam(defaultValue = "day") String type) {
        List<Map<String, Object>> data = rankService.exportRank(type);
        return R.success(null, data);
    }

    // ==================== 演唱会管理接口 ====================

    /**
     * 发布新演唱会
     * 同时创建票档并在 Redis 中初始化库存计数器（SETNX 防止覆盖已有数据）
     * @param request { title, singerName, venue, coverPic, showTime, saleStartTime,
     *                  introduction, tiers: [{tierName, price, totalStock}] }
     */
    @PostMapping("/admin/concert/add")
    public R addConcert(@RequestBody ConcertRequest request) {
        log.info("收到演唱会发布请求: title={}, tiers={}", request.getTitle(), request.getTiers());
        return concertService.addConcert(request);
    }

    /**
     * 修改演唱会信息（不含票档变更）
     * 票档和库存一旦创建不应随意修改，避免数据混乱
     */
    @PutMapping("/admin/concert/update")
    public R updateConcert(@RequestBody ConcertRequest request) {
        return concertService.updateConcert(request);
    }

    /**
     * 变更演唱会状态
     * 常用操作：下架(0)、上架预告(1)、手动设为售票中(2)、标记结束(4)
     * 设为售票中时会同步写 Redis 开售标记，供前端判断是否展示"立即购买"按钮
     */
    @PutMapping("/admin/concert/status")
    public R updateConcertStatus(@RequestParam Integer concertId,
                                 @RequestParam Integer status) {
        return concertService.updateStatus(concertId, status);
    }

    /**
     * 上传演唱会封面图片（不关联演唱会 ID，仅保存文件并返回 URL）
     * 用于新增演唱会时提前上传封面，URL 随表单一起提交
     */
    @PostMapping("/admin/concert/cover/upload")
    public R uploadCoverOnly(@RequestParam("file") MultipartFile file) throws IOException {
        String picPath = com.example.yin.utils.FileUtils.saveLocally(file, "img/concertPic");
        return R.success("上传成功", picPath);
    }

    /**
     * 上传演唱会封面图片并更新到指定演唱会记录
     * @param file      封面图片文件（JPG/PNG/GIF，≤2MB）
     * @param concertId 演唱会 ID
     */
    @PostMapping("/admin/concert/cover/update")
    public R uploadCover(@RequestParam("file") MultipartFile file,
                         @RequestParam("id") Integer concertId) {
        return concertService.uploadCover(file, concertId);
    }

    /**
     * 管理端演唱会列表（不分状态筛选，展示全部）
     */
    @GetMapping("/admin/concert/list")
    public R adminConcertList(@RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "10") Integer size) {
        return concertService.listConcerts(page, size, null,false);//管理端不过滤，管理员看到全部
    }

    // ==================== 订单管理接口 ====================

    /**
     * 查询某演唱会的所有订单（分页）
     * 关联 consumer 表显示用户名，方便运营查看
     */
    @GetMapping("/ticket/orders")
    public R ticketOrders(@RequestParam Integer concertId,
                          @RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "20") Integer size) {
        // 手动计算 offset，因为 XML 中使用 LIMIT offset, size 语法
        int offset = (page - 1) * size;
        List<Map<String, Object>> records = ticketOrderMapper
                .selectOrdersByConcert(concertId, offset, size);
        long total = ticketOrderMapper.countOrdersByConcert(concertId);

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", total);
        return R.success("成功", data);
    }
    /**
     * 售票统计：按票档汇总已售/剩余数量
     * 从 MySQL 直接 COUNT 订单，保证数据准确（不依赖 Redis 缓存）
     */
    @GetMapping("/ticket/stats/{concertId}")
    public R ticketStats(@PathVariable Integer concertId) {
        // 查该演唱会所有票档
        List<TicketTier> tiers = ticketTierMapper.selectList(
                new LambdaQueryWrapper<TicketTier>().eq(TicketTier::getConcertId, concertId));

        List<Map<String, Object>> stats = new ArrayList<>();
        for (TicketTier tier : tiers) {
            // 统计该票档已售订单数（COUNT by concert_id + tier_id）
            QueryWrapper<TicketOrder> wrapper = new QueryWrapper<>();
            wrapper.eq("concert_id", concertId).eq("tier_id", tier.getId());
            long sold = ticketOrderMapper.selectCount(wrapper);
            Map<String, Object> item = new HashMap<>();
            item.put("tierId", tier.getId());
            item.put("tierName", tier.getTierName());
            item.put("totalStock", tier.getTotalStock());
            item.put("sold", sold);
            item.put("remaining", tier.getTotalStock() - sold);  // 剩余 = 总库存 - 已售
            stats.add(item);
        }

          return R.success("成功", stats);
}
    @Autowired
    private TicketService ticketService;

    /**
     * 删除订单（管理员操作）
     * 清理订单的同时归还 Redis 库存、释放用户购买标记
     */
    @DeleteMapping("/ticket/order/{id}")
    public R deleteTicketOrder(@PathVariable Integer id) {
        return ticketService.cancelOrder(id);
    }

}
