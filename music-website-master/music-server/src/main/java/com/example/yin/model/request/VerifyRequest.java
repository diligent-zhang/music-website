package com.example.yin.model.request;

import lombok.Data;

@Data
public class VerifyRequest {
    private String qrCodeToken;
    private Integer operatorId;
}