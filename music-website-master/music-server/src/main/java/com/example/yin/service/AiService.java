package com.example.yin.service;

import com.example.yin.common.R;
import com.example.yin.model.request.ChatRequest;

public interface AiService {
    R chat(ChatRequest request);
}
