package com.example.yin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.yin.common.R;
import com.example.yin.mapper.RankListMapper;
import com.example.yin.model.domain.RankList;
import com.example.yin.model.request.RankListRequest;
import com.example.yin.service.RankListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RankListServiceImpl extends ServiceImpl<RankListMapper, RankList> implements RankListService {

    @Autowired
    private RankListMapper rankListMapper;

    @Override
    public R addRank(RankListRequest rankListAddRequest) {
        RankList rankList = new RankList();
        rankList.setSongListId(rankListAddRequest.getSongListId());
        rankList.setConsumerId(rankListAddRequest.getConsumerId());
        rankList.setScore(rankListAddRequest.getScore());
        
        if (rankListMapper.insert(rankList) > 0) {
            return R.success("评分成功");
        }
        return R.error("评分失败");
    }

    @Override
    public R rankOfSongListId(Long songListId) {
        Double rank = rankListMapper.selectAvg(songListId);
        if (rank != null) {
            return R.success(String.valueOf(rank));
        }
        return R.success(String.valueOf(0));
    }

    @Override
    public R getUserRank(Long consumerId, Long songListId) {
        QueryWrapper<RankList> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("consumer_id", consumerId)
                   .eq("song_list_id", songListId);
        
        RankList rankList = rankListMapper.selectOne(queryWrapper);
        if (rankList != null) {
            return R.success(rankList.getScore());
        }
        return R.success(0);
    }
}