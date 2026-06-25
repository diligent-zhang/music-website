# 音乐 AI 助手 (Music AI Assistant) 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在音乐网站中添加基于 DeepSeek 的智能 AI 音乐小助手，浮动按钮 + 聊天面板交互，支持音乐知识问答和播放器控制。

**Architecture:** Vue3 前端组件通过 `/ai/chat` 接口与 Spring Boot 后端通信，后端代理转发到 DeepSeek API。AI 回复中的特殊标记 `[PLAY:...]` 等由前端解析并触发 Vuex 播放器操作。

**Tech Stack:** Vue 3 + TypeScript + Element Plus + SCSS (前端), Spring Boot 2.6.2 + RestTemplate (后端), DeepSeek API (AI)

---

### Task 1: 后端 — ChatRequest DTO

**Files:**
- Create: `music-server/src/main/java/com/example/yin/model/request/ChatRequest.java`

- [ ] **Step 1: 创建 ChatRequest 请求体**

```java
package com.example.yin.model.request;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequest {
    private List<Message> messages;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd music-server && mvn compile -q`
Expected: BUILD SUCCESS (will fail until AiService/AiController also exist, so skip for now)

---

### Task 2: 后端 — AiService 接口

**Files:**
- Create: `music-server/src/main/java/com/example/yin/service/AiService.java`
- Create: `music-server/src/main/java/com/example/yin/service/impl/AiServiceImpl.java`

- [ ] **Step 1: 创建 AiService 接口**

```java
package com.example.yin.service;

import com.example.yin.common.R;
import com.example.yin.model.request.ChatRequest;

public interface AiService {
    R chat(ChatRequest request);
}
```

- [ ] **Step 2: 创建 AiServiceImpl 实现**

```java
package com.example.yin.service.impl;

import com.example.yin.common.R;
import com.example.yin.model.request.ChatRequest;
import com.example.yin.service.AiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiServiceImpl implements AiService {

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.model}")
    private String model;

    private static final String SYSTEM_PROMPT =
        "你是\"音乐小助手\"，一个对音乐充满热情且知识渊博的 AI 助手。\n" +
        "\n" +
        "【性格】\n" +
        "- 温暖、友好，像一位懂音乐的知心朋友\n" +
        "- 回答简洁有料，不要太啰嗦\n" +
        "- 适当使用音乐相关的 emoji（🎵 🎶 🎸 🎹 🥁 🎤）\n" +
        "\n" +
        "【能力范围】\n" +
        "- 音乐知识：乐理、音乐史、流派风格、乐器、作曲技法\n" +
        "- 歌手/乐队：生平介绍、代表作品、风格特点、趣闻轶事\n" +
        "- 歌曲推荐：根据心情、场景、喜好推荐适合的歌曲\n" +
        "- 音乐资讯：行业动态、音乐节、演唱会信息\n" +
        "- 音乐控制：帮用户搜索歌曲、播放/暂停、调节音量\n" +
        "\n" +
        "【音乐控制格式】\n" +
        "当用户想操作播放器时，在回复末尾用以下格式标记：\n" +
        "[PLAY:歌曲名-歌手名]  表示搜索并播放\n" +
        "[PAUSE]               表示暂停\n" +
        "[RESUME]              表示继续播放\n" +
        "[NEXT]                表示下一首\n" +
        "[VOLUME:数值]         表示调节音量（0-100）\n" +
        "注意：这些标记是给前端程序识别的指令，不要向用户解释这些标记的含义。\n" +
        "\n" +
        "【边界】\n" +
        "- 只回答音乐相关问题，如果用户问无关话题，礼貌地引导回音乐主题\n" +
        "- 不确定的事情坦诚说不知道，不要编造\n" +
        "- 用中文回答";

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public R chat(ChatRequest request) {
        try {
            List<Map<String, String>> messages = new ArrayList<>();

            // system prompt
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM_PROMPT);
            messages.add(systemMsg);

            // user messages
            for (ChatRequest.Message msg : request.getMessages()) {
                Map<String, String> m = new HashMap<>();
                m.put("role", msg.getRole());
                m.put("content", msg.getContent());
                messages.add(m);
            }

            // build request body
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0.7);
            body.put("max_tokens", 1000);

            // call DeepSeek API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                apiUrl + "/chat/completions", entity, Map.class);

            Map<String, Object> responseBody = response.getBody();
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            Map<String, Object> choice = choices.get(0);
            Map<String, String> message = (Map<String, String>) choice.get("message");
            String reply = message.get("content");

            Map<String, String> data = new HashMap<>();
            data.put("reply", reply);
            return R.success("成功", data);

        } catch (Exception e) {
            return R.fatal("AI 服务暂时不可用，请稍后再试");
        }
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd music-server && mvn compile -q`
Expected: BUILD SUCCESS

---

### Task 3: 后端 — AiController

**Files:**
- Create: `music-server/src/main/java/com/example/yin/controller/AiController.java`

- [ ] **Step 1: 创建 AiController**

```java
package com.example.yin.controller;

import com.example.yin.common.R;
import com.example.yin.model.request.ChatRequest;
import com.example.yin.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {

    @Autowired
    private AiService aiService;

    @PostMapping("/ai/chat")
    public R chat(@RequestBody ChatRequest request) {
        return aiService.chat(request);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd music-server && mvn compile -q`
Expected: BUILD SUCCESS

---

### Task 4: 后端 — 配置文件

**Files:**
- Modify: `music-server/src/main/resources/application.properties`

- [ ] **Step 1: 在 application.properties 末尾追加 DeepSeek 配置**

Edit `music-server/src/main/resources/application.properties` — append:

```properties
# DeepSeek AI
ai.api.key=sk-your-deepseek-api-key
ai.api.url=https://api.deepseek.com
ai.model=deepseek-chat
```

- [ ] **Step 2: 验证**

Run: `cd music-server && mvn compile -q`
Expected: BUILD SUCCESS

---

### Task 5: 前端 — API 层

**Files:**
- Modify: `music-client/src/api/index.ts`

- [ ] **Step 1: 在 HttpManager 对象中添加 sendAiMessage 方法**

Edit `music-client/src/api/index.ts` — 在 `HttpManager` 对象内、`getMyTicketOrders` 之后添加：

```typescript
// =======================> AI 助手 API
sendAiMessage: (data: { messages: { role: string; content: string }[] }) => post('ai/chat', data),
```

完整的插入位置在 `getMyTicketOrders` 那行之后、`};` 闭合之前。

---

### Task 6: 前端 — YinAiMessage 消息气泡组件

**Files:**
- Create: `music-client/src/components/layouts/YinAiMessage.vue`

- [ ] **Step 1: 创建消息气泡组件**

```vue
<template>
  <div class="ai-message" :class="role">
    <div class="avatar">
      <el-icon v-if="role === 'assistant'" :size="20"><Service /></el-icon>
      <el-icon v-else :size="20"><UserFilled /></el-icon>
    </div>
    <div class="bubble" :class="role">
      <div class="content" v-html="renderedContent"></div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { Service, UserFilled } from '@element-plus/icons-vue';

const props = defineProps<{
  role: 'user' | 'assistant';
  content: string;
}>();

const renderedContent = computed(() => {
  // 简单的换行转 <br> 处理
  return props.content.replace(/\n/g, '<br>');
});
</script>

<style lang="scss" scoped>
@import '@/assets/css/var.scss';

.ai-message {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;

  &.assistant {
    flex-direction: row;
  }
  &.user {
    flex-direction: row-reverse;
  }
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: $theme-color;
  color: $color-white;

  .user & {
    background: $color-blue-active;
  }
}

.bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;

  &.assistant {
    background: $color-light-grey;
    color: #333;
    border-bottom-left-radius: 4px;
  }
  &.user {
    background: $theme-color;
    color: $color-white;
    border-bottom-right-radius: 4px;
  }
}
</style>
```

---

### Task 7: 前端 — YinAiTyping 打字动画组件

**Files:**
- Create: `music-client/src/components/layouts/YinAiTyping.vue`

- [ ] **Step 1: 创建打字指示器组件**

```vue
<template>
  <div class="ai-message assistant">
    <div class="avatar">
      <el-icon :size="20"><Service /></el-icon>
    </div>
    <div class="bubble typing">
      <span class="dot"></span>
      <span class="dot"></span>
      <span class="dot"></span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { Service } from '@element-plus/icons-vue';
</script>

<style lang="scss" scoped>
@import '@/assets/css/var.scss';

.ai-message {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: $theme-color;
  color: $color-white;
}

.bubble {
  padding: 10px 14px;
  border-radius: 12px;
  background: $color-light-grey;
  border-bottom-left-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #999;
  animation: bounce 1.4s infinite ease-in-out both;

  &:nth-child(1) { animation-delay: -0.32s; }
  &:nth-child(2) { animation-delay: -0.16s; }
}

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
</style>
```

---

### Task 8: 前端 — YinAiAssistant 主体组件

**Files:**
- Create: `music-client/src/components/layouts/YinAiAssistant.vue`

- [ ] **Step 1: 创建主组件**

```vue
<template>
  <!-- 浮动按钮 -->
  <div class="ai-fab" @click="togglePanel" v-show="!visible">
    <el-icon :size="24"><Headset /></el-icon>
  </div>

  <!-- 聊天面板 -->
  <transition name="panel-slide">
    <div class="ai-panel" v-show="visible">
      <!-- 头部 -->
      <div class="panel-header">
        <span class="title">🎵 音乐小助手</span>
        <el-icon class="close-btn" :size="18" @click="visible = false"><Close /></el-icon>
      </div>

      <!-- 消息列表 -->
      <div class="panel-body" ref="bodyRef">
        <yin-ai-message
          v-for="(msg, i) in messages"
          :key="i"
          :role="msg.role"
          :content="msg.content"
        />
        <yin-ai-typing v-if="loading" />
      </div>

      <!-- 输入区 -->
      <div class="panel-footer">
        <el-input
          v-model="inputText"
          placeholder="问我音乐相关的问题..."
          @keyup.enter="send"
          :disabled="loading"
          size="small"
        />
        <el-button
          type="primary"
          :icon="Promotion"
          circle
          size="small"
          @click="send"
          :disabled="loading || !inputText.trim()"
        />
      </div>
    </div>
  </transition>
</template>

<script lang="ts" setup>
import { ref, nextTick, watch } from 'vue';
import { useStore } from 'vuex';
import { Headset, Close, Promotion } from '@element-plus/icons-vue';
import { HttpManager } from '@/api';
import YinAiMessage from './YinAiMessage.vue';
import YinAiTyping from './YinAiTyping.vue';

interface Message {
  role: 'user' | 'assistant';
  content: string;
}

const store = useStore();
const visible = ref(false);
const messages = ref<Message[]>([]);
const inputText = ref('');
const loading = ref(false);
const bodyRef = ref<HTMLElement | null>(null);

function togglePanel() {
  visible.value = !visible.value;
}

// 自动滚动到底部
watch(messages, async () => {
  await nextTick();
  if (bodyRef.value) {
    bodyRef.value.scrollTop = bodyRef.value.scrollHeight;
  }
}, { deep: true });

// 解析控制标记并执行操作
function parseControlMarkers(text: string): string {
  const playMatch = text.match(/\[PLAY:(.+?)-(.+?)\]/);
  if (playMatch) {
    const songName = playMatch[1].trim();
    const singerName = playMatch[2].trim();
    // 搜索歌曲并播放
    HttpManager.getSongOfSingerName(singerName).then((res: any) => {
      const songs = res.data || [];
      const match = songs.find((s: any) => s.name?.includes(songName)) || songs[0];
      if (match) {
        const songUrl = HttpManager.attachImageUrl(match.url);
        store.dispatch('song/playMusic', {
          id: match.id,
          url: songUrl,
          pic: match.pic,
          index: 0,
          songTitle: match.name || match.title,
          singerName: singerName,
          lyric: match.lyric,
          currentSongList: songs,
        });
      }
    }).catch(() => {});
    return text.replace(/\[PLAY:.+?\]/g, '');
  }

  if (/\[PAUSE\]/.test(text)) {
    store.commit('song/setIsPlay', false);
    return text.replace(/\[PAUSE\]/g, '');
  }
  if (/\[RESUME\]/.test(text)) {
    store.commit('song/setIsPlay', true);
    return text.replace(/\[RESUME\]/g, '');
  }
  if (/\[NEXT\]/.test(text)) {
    store.commit('song/setAutoNext', true);
    return text.replace(/\[NEXT\]/g, '');
  }
  const volMatch = text.match(/\[VOLUME:(\d+)\]/);
  if (volMatch) {
    const vol = parseInt(volMatch[1]) / 100;
    store.commit('song/setVolume', vol);
    return text.replace(/\[VOLUME:\d+\]/g, '');
  }

  return text;
}

async function send() {
  const text = inputText.value.trim();
  if (!text || loading.value) return;

  messages.value.push({ role: 'user', content: text });
  inputText.value = '';
  loading.value = true;

  try {
    const apiMessages = messages.value.map(m => ({ role: m.role, content: m.content }));
    const res: any = await HttpManager.sendAiMessage({ messages: apiMessages });
    const reply = res.data?.reply || res.reply || '抱歉，我暂时无法回复。';
    const cleanReply = parseControlMarkers(reply);
    messages.value.push({ role: 'assistant', content: cleanReply });
  } catch {
    messages.value.push({ role: 'assistant', content: '抱歉，网络出错了，请稍后再试。' });
  } finally {
    loading.value = false;
  }
}
</script>

<style lang="scss" scoped>
@import '@/assets/css/var.scss';

.ai-fab {
  position: fixed;
  z-index: 200;
  right: 10px;
  bottom: 130px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: $theme-color;
  color: $color-white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
  animation: breathe 2s ease-in-out infinite;
  transition: transform 0.2s;

  &:hover {
    transform: scale(1.1);
  }
}

@keyframes breathe {
  0%, 100% { box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3); }
  50% { box-shadow: 0 2px 20px rgba(48, 164, 252, 0.6); }
}

.ai-panel {
  position: fixed;
  z-index: 200;
  right: 10px;
  bottom: 180px;
  width: 380px;
  height: 520px;
  background: $color-white;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: $theme-color;
  color: $color-white;

  .title {
    font-size: 15px;
    font-weight: 600;
  }
  .close-btn {
    cursor: pointer;
    &:hover { opacity: 0.8; }
  }
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f9fafb;
}

.panel-footer {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #eee;
}

.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: all 0.3s ease;
}
.panel-slide-enter-from,
.panel-slide-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
```

- [ ] **Step 2: 验证组件语法**

Run: `cd music-client && npx vue-tsc --noEmit --skipLibCheck src/components/layouts/YinAiAssistant.vue 2>&1 || true`
Expected: No critical errors (some type warnings from vuex are OK)

---

### Task 9: 前端 — 集成到 YinContainer

**Files:**
- Modify: `music-client/src/views/YinContainer.vue`

- [ ] **Step 1: 在模板中添加 YinAiAssistant 组件**

Edit `music-client/src/views/YinContainer.vue` — 在 `<yin-scroll-top></yin-scroll-top>` 之后添加：

```html
      <yin-ai-assistant></yin-ai-assistant>
```

- [ ] **Step 2: 在 script 中添加导入**

Edit `music-client/src/views/YinContainer.vue` — 在 import 区域添加：

```typescript
import YinAiAssistant from "@/components/layouts/YinAiAssistant.vue";
```

完整的 `<script lang="ts" setup>` 部分变为：

```typescript
import { getCurrentInstance } from "vue";
import YinHeader from "@/components/layouts/YinHeader.vue";
import YinCurrentPlay from "@/components/layouts/YinCurrentPlay.vue";
import YinPlayBar from "@/components/layouts/YinPlayBar.vue";
import YinScrollTop from "@/components/layouts/YinScrollTop.vue";
import YinFooter from "@/components/layouts/YinFooter.vue";
import YinAudio from "@/components/layouts/YinAudio.vue";
import YinAiAssistant from "@/components/layouts/YinAiAssistant.vue";
```

---

### Task 10: 前端 — 构建验证

- [ ] **Step 1: 完整构建验证**

Run: `cd music-client && npx vue-tsc --noEmit --skipLibCheck 2>&1 | head -30`
Expected: No TypeScript errors (or only pre-existing errors unrelated to our changes)

---

### 文件变更总览

| 操作 | 文件 |
|------|------|
| Create | `music-server/src/main/java/com/example/yin/model/request/ChatRequest.java` |
| Create | `music-server/src/main/java/com/example/yin/service/AiService.java` |
| Create | `music-server/src/main/java/com/example/yin/service/impl/AiServiceImpl.java` |
| Create | `music-server/src/main/java/com/example/yin/controller/AiController.java` |
| Modify | `music-server/src/main/resources/application.properties` |
| Modify | `music-client/src/api/index.ts` |
| Create | `music-client/src/components/layouts/YinAiMessage.vue` |
| Create | `music-client/src/components/layouts/YinAiTyping.vue` |
| Create | `music-client/src/components/layouts/YinAiAssistant.vue` |
| Modify | `music-client/src/views/YinContainer.vue` |
