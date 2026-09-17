package com.example.yin.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.time.Duration;

@Slf4j
@Configuration
public class LangChain4jConfig {

    private static final String PLACEHOLDER_KEY = "your-api-key";

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.model}")
    private String modelName;

    @PostConstruct
    public void validateApiKey() {
        if (apiKey == null || apiKey.isBlank() || PLACEHOLDER_KEY.equals(apiKey)) {
            log.warn("==================================================================");
            log.warn("  ai.api.key 未配置（当前为占位符），音乐小助手 AI 功能将不可用！");
            log.warn("  请在 application.properties 填入 DeepSeek 密钥，");
            log.warn("  或设置环境变量 DEEPSEEK_API_KEY=sk-xxx 后重启。");
            log.warn("==================================================================");
        }
    }

    @Bean
    public ChatModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(apiUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(2)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}