package com.example.yin.controller;
import com.example.yin.common.R;
import com.example.yin.service.RankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/rank")
public class RankController {
@Autowired
    private RankService rankService;
//    获取排行榜数据  （支持自定义返回条数）
//    GET/Rank/list?type=day&limit=50
//
    @GetMapping("/list")
    public R getRankList(
            @RequestParam(defaultValue = "day") String type,
            @RequestParam(defaultValue = "20")  int limit) {
        List<Map<String, Object>> rankList = rankService.getRankList(type, limit);
        return R.success(null,rankList);
    }
    /**
     * 记录一次播放
     * POST /rank/play  body: { "songId": 1, "userId": 3 }
     */
    @PostMapping("/play")
    public R recordPlay(@RequestBody Map<String, Integer> request) {
        Integer songId = request.get("songId");
        Integer rankId = request.getOrDefault("userId",null);
        rankService.recordPlay(songId,rankId);
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