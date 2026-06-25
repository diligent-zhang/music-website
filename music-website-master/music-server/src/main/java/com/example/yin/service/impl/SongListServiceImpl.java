package com.example.yin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.yin.common.R;
import com.example.yin.constant.CacheConstant;
import com.example.yin.mapper.SongListMapper;
import com.example.yin.model.domain.SongList;
import com.example.yin.model.request.SongListRequest;
import com.example.yin.service.SongListService;
import com.example.yin.utils.CacheProtectionUtil;
import com.example.yin.utils.FileUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 歌单服务 — 仅全量查询 allSongList 使用缓存防护
 */
@Service
public class SongListServiceImpl extends ServiceImpl<SongListMapper, SongList> implements SongListService {

    @Autowired
    private SongListMapper songListMapper;

    @Autowired
    private CacheProtectionUtil cacheUtil;

    // ==================== 读操作 ====================

    @Override
    public R allSongList() {
        List<SongList> songLists = cacheUtil.getWithProtection(
                CacheConstant.songListAllKey(),
                List.class,
                () -> songListMapper.selectList(null),
                CacheConstant.TTL_SONGLIST
        );
        return R.success(null, songLists);
    }

    @Override
    public List<SongList> findAllSong() {
        return songListMapper.selectList(null);
    }

    @Override
    public R likeTitle(String title) {
        List<SongList> songLists = songListMapper.selectList(new QueryWrapper<SongList>().like("title", title));
        return R.success(null, songLists);
    }

    @Override
    public R likeStyle(String style) {
        List<SongList> songLists = songListMapper.selectList(new QueryWrapper<SongList>().like("style", style));
        return R.success(null, songLists);
    }

    // ==================== 写操作 ====================

    @Override
    public R addSongList(SongListRequest addSongListRequest) {
        SongList songList = new SongList();
        BeanUtils.copyProperties(addSongListRequest, songList);
        String pic = "/img/songListPic/123.jpg";
        songList.setPic(pic);
        if (songListMapper.insert(songList) > 0) {
            // 新增歌单 → 全量列表缓存失效
            cacheUtil.evict(CacheConstant.songListAllKey());
            return R.success("添加成功");
        } else {
            return R.error("添加失败");
        }
    }

    @Override
    public R updateSongListMsg(SongListRequest updateSongListRequest) {
        SongList songList = new SongList();
        BeanUtils.copyProperties(updateSongListRequest, songList);
        if (songListMapper.updateById(songList) > 0) {
            cacheUtil.evict(CacheConstant.songListAllKey());
            return R.success("修改成功");
        } else {
            return R.error("修改失败");
        }
    }

    @Override
    public R updateSongListImg(MultipartFile avatorFile, int id) {
        String s = null;
        try {
            s = FileUtils.saveToMinio(avatorFile, "img/songListPic");
        } catch (IOException e) {
            e.printStackTrace();
            return R.error("文件上传失败: " + e.getMessage());
        }
        if (s != null) {
            SongList songList = new SongList();
            songList.setId(id);
            songList.setPic(s);
            if (songListMapper.updateById(songList) > 0) {
                cacheUtil.evict(CacheConstant.songListAllKey());
                return R.success("上传成功", s);
            } else {
                return R.error("上传失败");
            }
        } else {
            return R.error("上传失败");
        }
    }

    @Override
    public R deleteSongList(Integer id) {
        if (songListMapper.deleteById(id) > 0) {
            cacheUtil.evict(CacheConstant.songListAllKey());
            return R.success("删除成功");
        } else {
            return R.error("删除失败");
        }
    }
}
