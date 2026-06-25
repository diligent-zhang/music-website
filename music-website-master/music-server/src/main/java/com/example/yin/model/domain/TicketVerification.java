package com.example.yin.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@TableName(value = "ticket_verification")
@Data
public class TicketVerification {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer orderId;

    private Integer operatorId;

    private Date verifyTime;
}