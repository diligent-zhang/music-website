package com.example.yin.controller;

import com.example.yin.common.R;
import com.example.yin.model.request.ChatRequest;
import com.example.yin.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class AiController {

    @Autowired
    private AiService aiService;

    @PostMapping("/ai/chat")
    public R chat(@RequestBody ChatRequest request) {
        return aiService.chat(request);
    }
}
