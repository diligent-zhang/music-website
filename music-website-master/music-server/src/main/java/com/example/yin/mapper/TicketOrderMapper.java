package com.example.yin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.yin.model.domain.TicketOrder;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public interface TicketOrderMapper extends BaseMapper<TicketOrder> {

    List<Map<String, Object>> selectOrdersByConcert(
            @Param("concertId") Integer concertId,
            @Param("offset") int offset,
            @Param("size") int size
    );

    long countOrdersByConcert(@Param("concertId") Integer concertId);

    List<Map<String, Object>> selectOrdersByUser(
            @Param("userId") Integer userId
    );
}