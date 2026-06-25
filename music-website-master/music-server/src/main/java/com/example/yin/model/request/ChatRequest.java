package com.example.yin.model.request;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequest {
    private String userId;
    private List<Message> messages;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}
