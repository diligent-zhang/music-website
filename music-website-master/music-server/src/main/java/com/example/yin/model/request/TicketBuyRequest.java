package com.example.yin.model.request;

import lombok.Data;

@Data
public class TicketBuyRequest {
    private Integer concertId;
    private Integer tierId;
    private Integer userId;
    private String idCard;
    private String phone;
    private String payMethod;   //“yinbi”或“qoder”
}

