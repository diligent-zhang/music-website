package com.example.yin.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.yin.model.domain.PlayLog;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
@Repository
public interface PlayLogMapper extends BaseMapper<PlayLog> {
    /**
     * 查询指定时段内歌曲的播放量排名（DB回退方案）
     */
    List<Map<String, Object>> selectRankByTimeRange(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit
    );

    List<Map<String, Object>> selectPlayLogs(
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("songName") String songName,
            @Param("userId") Integer userId
    );

    long countPlayLogs(
            @Param("songName") String songName,
            @Param("userId") Integer userId
    );

    int deleteById(@Param("id") Long id);
}