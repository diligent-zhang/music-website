package com.example.yin.service.impl;

import com.example.yin.common.R;
// import com.example.yin.mapper.ChatMemoryMapper;  // 暂时关闭 MySQL 长记忆存储
import com.example.yin.model.request.ChatRequest;
import com.example.yin.model.request.PlayAction;
import com.example.yin.service.AiService;
import com.example.yin.service.rag.SongContentRetriever;
import com.example.yin.service.tool.MusicTools;

// import com.example.yin.store.MySqlChatMemoryStore;  // 暂时关闭 MySQL 长记忆存储
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private MusicTools musicTools;

    @Autowired
    private SongContentRetriever songContentRetriever;
    // @Autowired
    // private ChatMemoryMapper chatMemoryMapper;  // 暂时关闭 MySQL 长记忆存储

    // 播放/暂停/切歌等简单指令的正则，直接拦截执行，不走 AI Agent
    private static final Pattern PLAY_PATTERN =
            Pattern.compile("^(播放|放一首|来一首|放|听|play)\\s*(.+?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAUSE_PATTERN =
            Pattern.compile("^(暂停|停一下|停|pause)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESUME_PATTERN =
            Pattern.compile("^(继续|接着放|接着播放|resume)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_PATTERN =
            Pattern.compile("^(下一首|切歌|换一首|next)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VOLUME_PATTERN =
            Pattern.compile("^(音量|声音|volume)\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);

    @Override
    public R chat(ChatRequest request) {
        String userId = extractUserId(request);
        String userMessage = extractUserMessage(request);

        if (userMessage == null || userMessage.isBlank()) {
            return R.error("消息不能为空");
        }

        log.info("用户 [{}] 消息: {}", userId, userMessage);

        // ===== 快速通道：暂停/继续/切歌/音量等控制指令直接执行，不经过 AI；播放指令走 LLM =====
        String directResult = handleDirectCommand(userMessage);
        if (directResult != null) {
            List<PlayAction> actions = musicTools.drainActions();
            String reply = buildReplyWithLegacyMarkers(directResult, actions);
            log.info("直接指令回复: {}", reply);
            Map<String, Object> respData = new LinkedHashMap<>();
            respData.put("reply", reply);
            if (!actions.isEmpty()) {
                respData.put("actions", actions);
            }
            return R.success("成功", respData);
        }

        // ===== AI Agent 通道：复杂查询走 LLM =====
        ContentRetriever contentRetriever = songContentRetriever.build();
        // MySqlChatMemoryStore store = new MySqlChatMemoryStore(chatMemoryMapper);  // 暂时关闭 MySQL 长记忆存储
        MusicAssistant assistant = AiServices.builder(MusicAssistant.class)
                .chatModel(chatModel)
                .tools(musicTools)
                .contentRetriever(contentRetriever)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(20)
                                // .chatMemoryStore(store)  // 暂时关闭 MySQL 长记忆存储，使用默认内存存储
                                .build())
                .build();

        try {
            String aiReply = assistant.chat(userId, userMessage);
            List<PlayAction> actions = musicTools.drainActions();
            String reply = buildReplyWithLegacyMarkers(aiReply, actions);
            log.info("Agent 回复: {}", reply);
            Map<String, Object> respData = new LinkedHashMap<>();
            respData.put("reply", reply);
            if (!actions.isEmpty()) {
                respData.put("actions", actions);
            }
            return R.success("成功", respData);
        } catch (Exception e) {
            log.error("Agent 推理失败", e);
            return R.error("抱歉，我暂时无法回答，请稍后再试。");
        }
    }

    /**
     * 拦截简单的播放控制指令（暂停/继续/切歌/音量），直接执行，不走 AI。
     * 播放指令不再拦截，统一走 AI Agent，由大模型调用工具完成。
     * 返回 null 表示不是简单指令，需要走 AI Agent。
     */
    private String handleDirectCommand(String msg) {
        String trimmed = msg.trim();

        // 播放指令不再拦截，交给 AI Agent 走大模型调用工具
        // Matcher m = PLAY_PATTERN.matcher(trimmed);
        // if (m.find()) { ... }

        if (PAUSE_PATTERN.matcher(trimmed).find()) {
            musicTools.pausePlayback();
            return "已暂停播放。";
        }

        if (RESUME_PATTERN.matcher(trimmed).find()) {
            musicTools.resumePlayback();
            return "已继续播放。";
        }

        if (NEXT_PATTERN.matcher(trimmed).find()) {
            musicTools.nextSong();
            return "已切换到下一首。";
        }

        Matcher m = VOLUME_PATTERN.matcher(trimmed);
        if (m.find()) {
            int vol = Integer.parseInt(m.group(2));
            String result = musicTools.setVolume(vol);
            return result;
        }

        return null; // 不是简单指令，走 AI Agent
    }

    // ==================== 内部方法 ====================

    private String extractUserId(ChatRequest request) {
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            return request.getUserId();
        }
        return "default";
    }

    /** 从 PlayAction 列表生成旧格式标记，追加到 reply 末尾（向后兼容） */
    private String buildReplyWithLegacyMarkers(String baseReply, List<PlayAction> actions) {
        if (actions == null || actions.isEmpty()) return baseReply;
        StringBuilder sb = new StringBuilder(baseReply);
        for (PlayAction a : actions) {
            String marker;
            switch (a.getType()) {
                case "play":   marker = "[PLAY:" + a.getSongId() + "]"; break;
                case "pause":  marker = "[PAUSE]"; break;
                case "resume": marker = "[RESUME]"; break;
                case "next":   marker = "[NEXT]"; break;
                case "volume": marker = "[VOLUME:" + a.getVolume() + "]"; break;
                default: continue;
            }
            if (sb.indexOf(marker) < 0) sb.append(marker);
        }
        return sb.toString();
    }

    private String extractUserMessage(ChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return null;
        }
        return request.getMessages().get(request.getMessages().size() - 1).getContent();
    }

    // ==================== Agent 接口定义 ====================

    interface MusicAssistant {

        @SystemMessage("""
                你是音乐助手"小音"，拥有音乐知识库，可回答创作背景、歌词含义等。

                工具：
                - searchSongs / searchSongsBySinger：搜索歌曲/歌手
                - getRankList：排行榜
                - getSongLists / searchSongListByStyle：浏览歌单
                - searchSingers：搜索歌手信息
                - recommendSongs：推荐歌曲
                - playSong(songName)：播放歌曲（用户说"播放XX"时直接调用）
                - pausePlayback / resumePlayback / nextSong / setVolume：播放控制

                规则：
                - 用户说"播放XX"，直接调用 playSong，调用后告知用户即可。
                - 推荐歌曲列3-5首并简介，等用户选择后再播放。
                - 只回答音乐相关问题，中文，语气温暖。
                """)
        String chat(@MemoryId String userId, @UserMessage String message);
    }
}
