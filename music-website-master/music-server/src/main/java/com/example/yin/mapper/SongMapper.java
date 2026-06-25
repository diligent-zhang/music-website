package com.example.yin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.yin.model.domain.Song;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;

@Repository
public interface SongMapper extends BaseMapper<Song> {

    List<Song> selectRankByDay();

    List<Song> selectRankByWeek();

    List<Song> selectRankByMonth();


    int updatePlayCount(Integer id);

    void updatePlayCountByValue(@Param("songId") Integer songId,
                                @Param("playCount") Integer playCount);

    List<Song> selectBySongListId(@Param("page") Page<Song> page,
                                  @Param("songListId") Integer songListId);
}
//解释： MyBatis-Plus 分页插件按参数类型识别 Page
//对象（不在乎参数名叫什么）。检测到 Page 后自动做两件事：
//        1. 执行 COUNT 查询得到总行数，写入 page.setTotal(...)
//  2. 在原始 SQL 后追加 LIMIT offset, pageSize