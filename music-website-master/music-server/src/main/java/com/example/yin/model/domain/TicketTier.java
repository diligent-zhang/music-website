package com.example.yin.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@TableName(value = "ticket_tier")
@Data
public class TicketTier {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer concertId;

    private String tierName;

    private BigDecimal price;

    private Integer totalStock;
}