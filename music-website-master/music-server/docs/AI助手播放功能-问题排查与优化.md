# AI 音乐助手播放功能：问题排查与优化总结

## 项目背景

在音乐网站项目中集成 AI 助手"小音"，用户可通过自然语言与助手对话，实现搜索歌曲、播放音乐、控制播放状态等功能。技术栈：Spring Boot + Vue 3 + Vuex + LangChain4j + DeepSeek 大模型 + MySQL + MinIO。

---

## 一、问题现象

用户说"播放海阔天空"，AI 助手回复了文字，但**音乐始终不播放**。同时伴有聊天上下文反复清空、重新初始化、无限循环的问题。

---

## 二、根因分析（3 个问题，层层递进）

### 问题 1：Vuex 命名空间 Bug（前端）—— 静默失败

**现象**：dispatch 和 commit 调用不报错，但音乐不播放。

**根因**：Vuex Store 的 modules 配置**没有开启 `namespaced: true`**：

```javascript
// store/index.ts
export default createStore({
  modules: { configure, user, song },  // 没有 namespaced!
});
```

当模块不开启命名空间时，所有 actions/mutations 注册在全局。但 AI 助手组件中使用了带命名空间的写法：

```typescript
// ❌ 错误：song 模块未命名空间，这个 action 不存在，静默失败
store.dispatch('song/playMusic', { ... })
store.commit('song/setIsPlay', false)
```

正确写法应为：

```typescript
// ✅ 正确：直接使用全局注册的 action 名
store.dispatch('playMusic', { ... })
store.commit('setIsPlay', false)
```

**为什么难发现**：Vuex 对不存在的 action/mutation 不会抛出异常，而是**静默失败**。正常播放（点击歌单等）走的是 `mixin.ts`，里面用的是不带前缀的调用方式，所以一直正常。只有 AI 助手组件写错了前缀。

### 问题 2：MySQL 聊天记忆导致无限循环（服务端）

**现象**：对话上下文反复清空 → 重新初始化 → 重新查库 → 重新发 AI 请求。

**根因**：`AiServiceImpl` 中使用 `MySqlChatMemoryStore` 将聊天记录持久化到 MySQL 的 `chat_message` 表。每次请求创建新的 Store 实例，DB 读写失败或数据不一致时导致上下文丢失，进而触发连锁反应。

**解决方案**：注释掉 `MySqlChatMemoryStore`，改用 LangChain4j 默认的 `InMemoryChatMemoryStore`，对话历史仅存于 JVM 内存，避免 DB 依赖。

### 问题 3：播放指令被正则拦截，绕过 LLM（服务端）—— 架构缺陷

**现象**：播放功能"能用"，但没有经过大模型推理。

**根因**：`AiServiceImpl.handleDirectCommand()` 中，播放指令被正则提前拦截：

```java
// 播放正则匹配后，直接调用 MusicTools.playSong()，完全不走 AI Agent
Pattern PLAY_PATTERN = Pattern.compile("^(播放|放一首|...)\\s*(.+?)$");
if (m.find()) {
    String result = musicTools.playSong(songName, null);  // 直接执行
    return "好的，" + result;  // 直接返回，AI Agent 永远不会执行
}
```

这导致：
- 大模型从未参与播放决策
- 无法利用 LLM 的语义理解能力（如"放一首周杰伦最火的歌"→ 需要 LLM 先搜索再选择）
- RAG 音乐知识库在播放场景下完全用不上
- Tool 的 function calling 机制形同虚设

**解决方案**：将播放指令从快速通道移除，让其走 AI Agent 路径，由 LLM 通过 function calling 调用 `playSong` Tool。

---

## 三、优化后架构

```
用户说"播放海阔天空"
  │
  ▼
handleDirectCommand()  ← 仅处理暂停/继续/切歌/音量（简单控制）
  │ 返回 null（播放指令不再拦截）
  ▼
AI Agent 通道
  │
  ├─ DeepSeek 大模型（语义理解）
  ├─ MusicTools（19 个 Tool 函数，function calling）
  ├─ SongContentRetriever（RAG 音乐知识库）
  └─ MessageWindowChatMemory（内存对话记忆，20 条窗口）
  │
  ▼
LLM 决定调用 playSong("海阔天空", null)
  │
  ▼
MusicTools.playSong()
  ├─ MySQL LIKE 模糊查询歌曲
  ├─ 封装 PlayAction { type, songId, name, singerName, url, pic, lyric }
  └─ 存入 ThreadLocal pendingActions
  │
  ▼
LLM 生成自然语言回复
  │
  ▼
响应 JSON
  {
    "reply": "好的，正在为您播放《海阔天空》- Beyond",
    "actions": [{
      "type": "play",
      "songId": 42,
      "name": "海阔天空",
      "singerName": "Beyond",
      "url": "/song/uuid.mp3",
      "pic": "/img/songPic/xxx.jpg",
      "lyric": "..."
    }]
  }
  │
  ▼
前端 processActions()
  ├─ store.dispatch('playMusic', { id, url, pic, name, singerName, lyric })
  └─ YinAudio.vue → <audio> 标签 → AudioController → MinIO 流式播放
```

---

## 四、改动文件清单

| 文件 | 改动 | 目的 |
|---|---|---|
| `PlayAction.java` | **新增** | 结构化播放动作 DTO，含完整歌曲信息 |
| `MusicTools.java` | 修改 | `pendingCommands` → `pendingActions`，存储完整歌曲数据 |
| `AiServiceImpl.java` | 修改 | (1) 播放指令不再拦截，走 LLM (2) 响应含 `actions` 数组 (3) MySQL 记忆改为内存记忆 |
| `YinAiAssistant.vue` | 修改 | (1) 修复 Vuex 命名空间 bug (2) 新增 `processActions()` 直接消费结构化数据 |

---

## 五、面试表述参考

> "在开发 AI 音乐助手的过程中，我遇到了一个比较典型的多层问题。表面上看是'AI 回复了但不播放'，实际排查下来涉及三个层面：
>
> **第一层是前端状态管理的问题。** Vuex 模块没有开启 namespaced，但组件里用了 `song/playMusic` 这种命名空间写法，导致 dispatch 静默失败。这个问题隐蔽在 Vuex 对不存在的 action 不抛异常，我当时是通过对比正常播放链路（mixin.ts）和 AI 播放链路的调用方式才定位到的。
>
> **第二层是服务端架构设计的问题。** 原来的实现把播放指令放在快速通道里用正则拦截，直接调工具方法就返回了，大模型根本没参与。这等于把 AI 助手的'智能'阉割了——它只能做关键字匹配，无法理解'放一首伤感的歌'这种语义查询。
>
> **第三层是持久化策略的问题。** MySQL 聊天记忆在高并发或异常情况下会导致上下文反复丢失，触发连锁的 DB 查询和 AI 请求。我把它改成了内存存储，在功能可用性和持久化之间做了权衡。
>
> 优化后的方案让播放请求完整走过 LLM → function calling → Tool 查询 → 结构化响应 → 前端播放这条链路，AI 真正参与到了播放决策中。"

---

## 六、关键技术点（面试可能追问）

**Q: 为什么用 ThreadLocal 传递播放指令？**
A: 因为 LangChain4j 的 Tool 方法返回值会直接交给 LLM 生成回复，不能让 LLM 看到 `[PLAY:123]` 这种技术标记（会干扰它的回复质量）。ThreadLocal 在同一请求线程内传递旁路数据，既不污染 LLM 的上下文，又能在回复生成后拼回去。

**Q: 为什么返回结构化的 actions 数组而不只是文字标记？**
A: 旧方案前端拿到 `[PLAY:123]` 文本标记后需要正则解析 + 二次 API 请求才能拿到歌曲 URL。新方案把完整歌曲信息（id、url、name、singer、lyric）直接放在响应里，前端零额外请求就能驱动播放，减少了一轮网络往返。

**Q: DeepSeek function calling 的可靠性如何？**
A: DeepSeek 的 API 兼容 OpenAI 格式，LangChain4j 通过 `langchain4j-open-ai` 适配。在 System Prompt 中明确告知每个工具的使用时机后，调用准确率较高。对于模糊匹配场景（如"放一首周杰伦的歌"），LLM 会先调 `searchSongsBySinger` 再调 `playSong`，比纯正则灵活得多。
