package com.example.yin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.yin.common.R;
import com.example.yin.constant.CacheConstant;
import com.example.yin.mapper.SongMapper;
import com.example.yin.model.domain.Song;
import com.example.yin.model.request.PageResult;
import com.example.yin.model.request.SongRequest;
import com.example.yin.service.SongService;
import com.example.yin.utils.CacheProtectionUtil;
import com.example.yin.utils.FileUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 歌曲服务 — 已集成缓存三大防护：
 * 缓存穿透：空值缓存（短TTL）
 * 缓存雪崩：随机TTL（±20%浮动）
 * 缓存击穿：Redis SETNX 分布式锁
 */
@Service
public class SongServiceImpl extends ServiceImpl<SongMapper, Song> implements SongService {

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private CacheProtectionUtil cacheUtil;

    @Autowired
    private com.example.yin.mapper.SingerMapper singerMapper;

    // ==================== 读操作（走缓存 + 三大防护）====================

    @Override
    public R allSong() {
        List<Song> songs = cacheUtil.getWithProtection(
                CacheConstant.songAllKey(),
                List.class,
                () -> songMapper.selectList(null),
                CacheConstant.TTL_SONG
        );
        populateSingerNames(songs);
        return R.success(null, songs);
    }

    @Override
    public R songOfSingerId(Integer singerId) {
        List<Song> songs = cacheUtil.getWithProtection(
                CacheConstant.songBySingerIdKey(singerId),
                List.class,
                () -> songMapper.selectList(new QueryWrapper<Song>().eq("singer_id", singerId)),
                CacheConstant.TTL_SONG
        );
        populateSingerNames(songs);
        return R.success(null, songs);
    }
    @Override
    public R songOfId(Integer id) {
        List<Song> songs = cacheUtil.getWithProtection(
                CacheConstant.songByIdKey(id),
                List.class,
                () -> songMapper.selectList(new QueryWrapper<Song>().eq("id", id)),
                CacheConstant.TTL_SONG
        );
        populateSingerNames(songs);
        return R.success(null, songs);
    }

    // 为歌曲列表填充歌手名（批量查询，避免 N+1）
    private void populateSingerNames(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        // 收集所有 singerId
        java.util.Set<Integer> singerIds = songs.stream()
                .map(Song::getSingerId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());
        if (singerIds.isEmpty()) return;
        // 一次批量查询
        java.util.List<com.example.yin.model.domain.Singer> singers = singerMapper.selectBatchIds(singerIds);
        java.util.Map<Integer, String> nameMap = singers.stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.example.yin.model.domain.Singer::getId,
                        com.example.yin.model.domain.Singer::getName));
        // 填充到每首歌
        for (Song song : songs) {
            if (song.getSingerId() != null) {
                song.setSingerName(nameMap.get(song.getSingerId()));
            }
        }
    }
    @Override
    public R songOfSingerName(String name) {
        QueryWrapper<Song> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("name", name);
        List<Song> songs = songMapper.selectList(queryWrapper);
        if (songs == null || songs.isEmpty()) {
            return R.error("添加失败，没有找到该歌,无法加入该歌单");
        }
        return R.success(null, songs);
    }

    @Override
    public R getRankList(String type) {
        List<Song> songs = cacheUtil.getWithProtection(
                CacheConstant.songRankKey(type),
                List.class,
                () -> {
                    switch (type) {
                        case "day":
                            return songMapper.selectRankByDay();
                        case "week":
                            return songMapper.selectRankByWeek();
                        case "month":
                            return songMapper.selectRankByMonth();
                        default:
                            return songMapper.selectRankByDay();
                    }
                },
                CacheConstant.TTL_SONG
        );
        return R.success(null, songs);
    }

    // ==================== 写操作（更新DB后清理缓存，保证一致性）====================
    @Override
    public R addSong(SongRequest addSongRequest, MultipartFile lrcfile, MultipartFile mpfile) {
        Song song = new Song();
        BeanUtils.copyProperties(addSongRequest, song);
        String pic = "/img/songPic/tubiao.jpg";
        String s = null;
        try {
            s = FileUtils.saveToMinio(mpfile, "song");
        } catch (IOException e) {
            e.printStackTrace();
            return R.error("文件上传失败: " + e.getMessage());
        }
        String storeUrlPath = s;
        song.setPic(pic);
        song.setUrl(storeUrlPath);
        if (lrcfile != null && (song.getLyric().equals("[00:00:00]暂无歌词"))) {
            byte[] fileContent = new byte[0];
            try {
                fileContent = lrcfile.getBytes();
                String content = new String(fileContent, "GB2312");
                song.setLyric(content);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (s != null && songMapper.insert(song) > 0) {
            // 新增歌曲 → 全量歌曲列表缓存失效，下次请求重新加载
            cacheUtil.evict(CacheConstant.songAllKey());
            return R.success("上传成功", storeUrlPath);
        } else {
            return R.error("上传失败");
        }
    }
    @Override
    public R updateSongMsg(SongRequest updateSongRequest) {
        Song song = new Song();
        BeanUtils.copyProperties(updateSongRequest, song);
        if (songMapper.updateById(song) > 0) {
            // 修改歌曲 → 清理全量列表 + 该歌曲的单独缓存
            cacheUtil.evict(CacheConstant.songAllKey());
            cacheUtil.evict(CacheConstant.songByIdKey(song.getId()));
            return R.success("修改成功");
        } else {
            return R.error("修改失败");
        }
    }
    @Override
    public R updateSongUrl(MultipartFile urlFile, int id) {
        Song song = songMapper.selectById(id);
        String path = song.getUrl();
        FileUtils.deleteFromMinio(path);

        String s = null;
        try {
            s = FileUtils.saveToMinio(urlFile, "song");
        } catch (IOException e) {
            e.printStackTrace();
            return R.error("文件上传失败: " + e.getMessage());
        }
        String storeUrlPath = s;
        song.setId(id);
        song.setUrl(storeUrlPath);
        song.setName(urlFile.getOriginalFilename());
        if (s != null && songMapper.updateById(song) > 0) {
            // 更新歌曲文件URL → 清理相关缓存
            cacheUtil.evict(CacheConstant.songAllKey());
            cacheUtil.evict(CacheConstant.songByIdKey(id));
            cacheUtil.evict(CacheConstant.songBySingerIdKey(song.getSingerId()));
            return R.success("更新成功", storeUrlPath);
        } else {
            return R.error("更新失败");
        }
    }
    @Override
    public R updateSongPic(MultipartFile urlFile, int id) {
        String s = null;
        try {
            s = FileUtils.saveToMinio(urlFile, "img/songPic");
        } catch (IOException e) {
            e.printStackTrace();
            return R.error("文件上传失败: " + e.getMessage());
        }
        if (s != null) {
            Song song = new Song();
            song.setId(id);
            song.setPic(s);
            if (songMapper.updateById(song) > 0) {
                cacheUtil.evict(CacheConstant.songAllKey());
                cacheUtil.evict(CacheConstant.songByIdKey(id));
                return R.success("上传成功", s);
            } else {
                return R.error("上传失败");
            }
        } else {
            return R.error("上传失败");
        }
    }

    @Override
    public R updateSongLrc(MultipartFile lrcFile, int id) {
        Song song = songMapper.selectById(id);
        if (lrcFile != null && !(song.getLyric().equals("[00:00:00]暂无歌词"))) {
            byte[] fileContent = new byte[0];
            try {
                fileContent = lrcFile.getBytes();
                String content = new String(fileContent, "GB2312");
                song.setLyric(content);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (songMapper.updateById(song) > 0) {
            // 更新歌词 → 清理该歌曲缓存
            cacheUtil.evict(CacheConstant.songAllKey());
            cacheUtil.evict(CacheConstant.songByIdKey(id));
            return R.success("更新成功");
        } else {
            return R.error("更新失败");
        }
    }
    @Override
    public R deleteSong(Integer id) {
        Song song = songMapper.selectById(id);
        String path = song.getUrl();
        // 删除 MinIO 中的音频文件
        if (path != null && path.startsWith("/song/")) {
            FileUtils.deleteFromMinio(path);
        }
        if (songMapper.deleteById(id) > 0) {
            cacheUtil.evict(CacheConstant.songAllKey());
            cacheUtil.evict(CacheConstant.songByIdKey(id));
            cacheUtil.evict(CacheConstant.songBySingerIdKey(song.getSingerId()));
            return R.success("删除成功");
        } else {
            return R.error("删除失败");
        }
    }

    @Override
    public R songOfIds(java.util.List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.success(null, java.util.Collections.emptyList());
        }
        // 排序后拼接作为缓存key，确保相同ID集合命中同一缓存
        String joined = ids.stream().sorted().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        java.util.List<Song> songs = cacheUtil.getWithProtection(
                CacheConstant.songByIdsKey(joined),
                List.class,
                () -> {
                    java.util.List<Song> dbSongs = songMapper.selectBatchIds(ids);
                    populateSingerNames(dbSongs);
                    return dbSongs;
                },
                CacheConstant.TTL_SONG
        );
        return R.success(null, songs);
    }

    @Override
    public R songOfSongListId(Integer songListId, Integer page, Integer
            pageSize) {
        // 默认值保护
        int currentPage = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 20 : pageSize;

        // 每页独立缓存 key
        String cacheKey = CacheConstant.songBySongListIdPageKey(songListId,
                currentPage, size);

  /*      - 缓存从全量 List<Song> 变为 PageResult（包含分页数据 + total）
        - new Page<>(currentPage, size) 的页码从 1 开始（MyBatis-Plus 约定）
        - pageParam.getTotal() 是分页插件自动执行 COUNT 后塞进去的
                - 每页独立缓存，用户在第 2 页和第 3 页之间切换时各自命中自己的缓存*/
        PageResult result = cacheUtil.getWithProtection(
                cacheKey,
                PageResult.class,
                () -> {
                    // MyBatis-Plus Page 对象：参数为 (当前页, 每页大小)
                    Page<Song> pageParam = new Page<>(currentPage, size);
                    // 执行查询，分页插件自动拦截 —— 无需改 SQL
                    List<Song> songs =
                            songMapper.selectBySongListId(pageParam, songListId);
                    // pageParam.getTotal() 是插件自动填充的总行数
                    return new PageResult(songs, pageParam.getTotal(),
                            currentPage, size);
                },
                CacheConstant.TTL_SONG
        );
        return R.success(null, result);
    }
    @Override
    public R updatePlayCount(Integer id) {
        if (songMapper.updatePlayCount(id) > 0) {
            cacheUtil.evict(CacheConstant.songRankKey("day"));
            cacheUtil.evict(CacheConstant.songRankKey("week"));
            cacheUtil.evict(CacheConstant.songRankKey("month"));
            return R.success("更新播放次数成功");
        } else {
            return R.error("更新播放次数失败");
        }
    }
}
