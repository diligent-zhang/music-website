package com.example.yin.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.util.Date;

/**
 * 自定义 Date 反序列化器：空字符串 → null，正常日期 → 交由 Jackson 原生解析
 */
@JsonComponent
public class JacksonConfig {

    public static class CustomDateDeserializer extends JsonDeserializer<Date> {
        @Override
        public Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getText();
            if (text == null || text.isEmpty()) {
                return null;
            }
            // 非空字符串交给 Jackson 内置解析器处理（支持 ISO 8601 等格式）
            return ctxt.parseDate(text);
        }
    }
}
