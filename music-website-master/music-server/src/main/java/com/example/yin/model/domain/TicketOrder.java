package com.example.yin.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@TableName(value = "ticket_order")
@Data
public class TicketOrder {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String orderNo;

    private Integer userId;

    private Integer concertId;

    private Integer tierId;

    private String tierName;

    private BigDecimal price;

    private String idCard;

    private String phone;

    private String qrCodeToken;

    private Integer payStatus;

    private Integer verifyStatus;

    private Date verifyTime;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}