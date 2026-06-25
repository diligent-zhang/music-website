package com.example.yin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.yin.model.domain.ChatMemoryDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMemoryMapper extends BaseMapper<ChatMemoryDO> {

    @Select("SELECT * FROM chat_message WHERE memory_id = #{memoryId} ORDER BY message_index ASC")
    List<ChatMemoryDO> selectByMemoryId(@Param("memoryId") String memoryId);

    @Delete("DELETE FROM chat_message WHERE memory_id = #{memoryId}")
    int deleteByMemoryId(@Param("memoryId") String memoryId);
}
