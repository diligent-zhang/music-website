package com.example.yin.store;

import com.example.yin.mapper.ChatMemoryMapper;
import com.example.yin.model.domain.ChatMemoryDO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MySqlChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryMapper mapper;

    public MySqlChatMemoryStore(ChatMemoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        if (memoryId == null) return List.of();
        List<ChatMemoryDO> rows = mapper.selectByMemoryId(memoryId.toString());
        if (rows == null || rows.isEmpty()) return List.of();

        List<ChatMessage> messages = new ArrayList<>();
        for (ChatMemoryDO row : rows) {
            ChatMessage msg = rowToMessage(row);
            if (msg != null) messages.add(msg);
        }
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (memoryId == null) {
            log.warn("memoryId is null, 跳过存储");
            return;
        }
        // 先删后插，保证幂等
        mapper.deleteByMemoryId(memoryId.toString());

        int index = 0;
        for (ChatMessage msg : messages) {
            // 过滤工具相关消息：只持久化纯文本的 USER/AI 对话
            // 原因：AiMessage 的 toolExecutionRequests 无法通过行存储完整保留，
            // 恢复后 ToolExecutionResultMessage 变成孤儿消息，DeepSeek API 会拒收
            if (msg instanceof AiMessage am) {
                if (am.hasToolExecutionRequests()) continue;
            }
            if (msg instanceof ToolExecutionResultMessage) continue;
            if (msg instanceof SystemMessage) continue;

            ChatMemoryDO row = messageToRow(memoryId.toString(), index, msg);
            if (row != null) {
                mapper.insert(row);
                index++;
            }
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        if (memoryId == null) return;
        mapper.deleteByMemoryId(memoryId.toString());
    }

    // ==================== 类型转换 ====================

    private ChatMessage rowToMessage(ChatMemoryDO row) {
        String type = row.getMessageType();
        String content = row.getContent();
        if (type == null || content == null) return null;

        try {
            return switch (type) {
                case "USER" -> UserMessage.from(content);
                case "AI" -> AiMessage.from(content);
                case "SYSTEM" -> SystemMessage.from(content);
                case "TOOL_RESULT" -> ToolExecutionResultMessage.from(
                        row.getToolExecutionId(),
                        row.getToolName(),
                        content);
                default -> {
                    log.warn("未知消息类型: {}", type);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.error("消息重建失败 type={}", type, e);
            return null;
        }
    }

    private ChatMemoryDO messageToRow(String memoryId, int index, ChatMessage msg) {
        ChatMemoryDO row = new ChatMemoryDO();
        row.setMemoryId(memoryId);
        row.setMessageIndex(index);

        if (msg instanceof UserMessage um) {
            row.setMessageType("USER");
            row.setContent(um.singleText());
        } else if (msg instanceof AiMessage am) {
            row.setMessageType("AI");
            row.setContent(am.text() != null ? am.text() : "");
        } else if (msg instanceof SystemMessage sm) {
            row.setMessageType("SYSTEM");
            row.setContent(sm.text());
        } else if (msg instanceof ToolExecutionResultMessage trm) {
            row.setMessageType("TOOL_RESULT");
            row.setContent(trm.text() != null ? trm.text() : "");
            row.setToolName(trm.toolName());
            row.setToolExecutionId(trm.id());
        } else {
            log.warn("不支持的消息类型: {}", msg.getClass().getName());
            return null;
        }
        return row;
    }
}
