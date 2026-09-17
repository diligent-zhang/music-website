package com.example.yin.controller;
import com.example.yin.common.R;
import com.example.yin.service.RankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
@RestController
@RequestMapping("/rank")
public class RankController {
@Autowired
    private RankService rankService;
@Autowired
    private StringRedisTemplate stringRedisTemplate;
//    获取排行榜数据  （支持自定义返回条数）
//    GET/Rank/list?type=day&limit=50
//
    @GetMapping("/list")
    public R getRankList(
            @RequestParam(defaultValue = "day") String type,
            @RequestParam(defaultValue = "20")  int limit) {
        List<Map<String, Object>> rankList = rankService.getRankList(type, Math.min(Math.max(limit, 1), 200));
        return R.success(null,rankList);
    }
    /**
     * 记录一次播放（游客也可上报，故保持公开）
     * POST /rank/play  body: { "songId": 1, "userId": 3 }
     * 防刷：同一 IP+歌曲 45 秒窗口内只计一次（SETNX 去重）
     */
    @PostMapping("/play")
    public R recordPlay(@RequestBody Map<String, Integer> request,
                        javax.servlet.http.HttpServletRequest httpRequest) {
        Integer songId = request.get("songId");
        if (songId == null) {
            return R.error("songId 不能为空");
        }
        Integer rankId = request.getOrDefault("userId", null);
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = httpRequest.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                "rank:play:dedupe:" + ip + ":" + songId, "1", 45, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(first)) {
            return R.success("播放次数更新成功"); // 窗口内重复上报，静默忽略
        }
        rankService.recordPlay(songId, rankId);
        return R.success("播放次数更新成功");

    }
    /**
     * 获取单首歌曲在三个榜单中的排名
     * GET /rank/detail/12
     */
    @GetMapping("/detail/{songId}")
    public R recordDetail(@PathVariable Integer songId) {
        Map<String,Object> detail = rankService.getSongRankDetail(songId);
        return R.success(null,detail);
    }


}