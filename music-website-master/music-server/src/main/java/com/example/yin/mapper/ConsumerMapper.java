package com.example.yin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.yin.model.domain.Consumer;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface ConsumerMapper extends BaseMapper<Consumer> {
    int debitYinbi(@Param("userId") Integer userId,@Param("amount") BigDecimal amount);
    int rechargeYinbi(@Param("userId") Integer userId,@Param("amount") BigDecimal amount);
}
