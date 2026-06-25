package com.example.yin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.yin.common.R;
import com.example.yin.model.domain.Song;
import com.example.yin.model.request.SongRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface SongService extends IService<Song> {

    R addSong (SongRequest addSongRequest,MultipartFile lrcfile,  MultipartFile mpfile) throws IOException;

    R updateSongMsg(SongRequest updateSongRequest);

    R updateSongUrl(MultipartFile urlFile, int id) throws IOException;

    R updateSongPic(MultipartFile urlFile, int id) throws IOException;

    R deleteSong(Integer id);

    R allSong();

    R songOfSingerId(Integer singerId);

    R songOfId(Integer id);

    R songOfSingerName(String name);

    R updateSongLrc(MultipartFile lrcFile, int id);

    R getRankList(String type);

    R updatePlayCount(Integer id);

    R songOfIds(java.util.List<Integer> ids);

    R songOfSongListId(Integer songListId, Integer page, Integer pageSize);
}
