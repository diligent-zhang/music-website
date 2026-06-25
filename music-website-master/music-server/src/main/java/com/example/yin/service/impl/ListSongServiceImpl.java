package com.example.yin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.yin.common.R;
import com.example.yin.constant.CacheConstant;
import com.example.yin.mapper.ListSongMapper;
import com.example.yin.model.domain.ListSong;
import com.example.yin.model.request.ListSongRequest;
import com.example.yin.service.ListSongService;
import com.example.yin.utils.CacheProtectionUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListSongServiceImpl extends ServiceImpl<ListSongMapper, ListSong> implements ListSongService {
    @Autowired
    private ListSongMapper listSongMapper;

    @Autowired
    private CacheProtectionUtil cacheUtil;
    @Override
    public List<ListSong> allListSong() {
        return listSongMapper.selectList(null);
    }
    @Override
    public R updateListSongMsg(ListSongRequest updateListSongRequest) {
        ListSong listSong = new ListSong();
        BeanUtils.copyProperties(updateListSongRequest, listSong);
        if (listSongMapper.updateById(listSong) > 0) {
            return R.success("修改成功");
        } else {
            return R.error("修改失败");
        }
    }
    @Override
    public R deleteListSong(Integer songId) {
        // 删除前查出关联的歌单ID，用于清缓存
        QueryWrapper<ListSong> selectWrapper = new QueryWrapper<>();
        selectWrapper.eq("song_id", songId);
        List<ListSong> records = listSongMapper.selectList(selectWrapper);

        QueryWrapper<ListSong> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("song_id", songId);
        if (listSongMapper.delete(deleteWrapper) > 0) {
            // 清除每个关联歌单的分页缓存
            for (ListSong ls : records) {
                cacheUtil.evictByPrefix(CacheConstant.CACHE_SONG + "::songListId::" + ls.getSongListId() + "::");
            }
            return R.success("删除成功");
        } else {
            return R.error("删除失败");
        }
    }

    @Override
    public R addListSong(ListSongRequest addListSongRequest) {
        ListSong listSong = new ListSong();
        BeanUtils.copyProperties(addListSongRequest, listSong);
        if (listSongMapper.insert(listSong) > 0) {
            // 清除该歌单的所有分页缓存，保证下次查询数据一致
            cacheUtil.evictByPrefix(CacheConstant.CACHE_SONG + "::songListId::" + addListSongRequest.getSongListId() + "::");
            return R.success("添加成功");
        } else {
            return R.error("添加失败");
        }
    }

    @Override
    public R listSongOfSongId(Integer songListId) {
        QueryWrapper<ListSong> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("song_list_id",songListId);
        return R.success("查询成功", listSongMapper.selectList(queryWrapper));
    }

}
