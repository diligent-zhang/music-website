package com.example.yin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.yin.model.domain.Concert;
import org.springframework.stereotype.Repository;

@Repository
public interface ConcertMapper extends BaseMapper<Concert> {
}