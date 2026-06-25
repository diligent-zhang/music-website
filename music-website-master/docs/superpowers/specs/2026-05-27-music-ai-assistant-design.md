# 音乐 AI 助手 (Music AI Assistant) 设计文档

## 概述

在音乐网站中添加一个智能 AI 音乐小助手，以浮动按钮 + 聊天面板的形式呈现，调用 DeepSeek 大模型提供音乐知识问答和播放器控制功能。

## 架构

```
用户浏览器                      music-server (Spring Boot)        DeepSeek API
┌──────────────┐   POST /ai/chat   ┌────────────────┐   HTTP    ┌──────────┐
│ YinAiAssistant│ ────────────────> │ AiController   │ ────────> │ deepseek │
│ (Vue3 组件)   │ <──────────────── │ AiService      │ <──────── │  -chat   │
└──────────────┘     JSON回复        └────────────────┘           └──────────┘
```

- 前端浮动按钮 + 聊天面板，放在 `YinContainer.vue` 中
- 后端新增 `/ai/chat` 接口，作为 DeepSeek API 的代理层
- API Key 仅存在后端配置文件，不暴露到前端
- 后端在 system prompt 中注入网站热门数据，帮助 AI 做出更准确的推荐

## 前端设计

### 组件树

```
YinContainer.vue
└── el-main
    ├── YinScrollTop    (现有: right:10px; bottom:80px)
    └── YinAiAssistant  (新增: right:10px; bottom:130px)
        ├── 浮动圆按钮 (音乐图标，呼吸动画)
        ├── 聊天面板头部 (标题 + 关闭按钮)
        ├── 消息列表 (滚动区域)
        │   ├── AI消息: 白色气泡靠左
        │   └── 用户消息: 蓝色气泡靠右
        ├── 打字动画指示器
        └── 输入区域 (输入框 + 发送按钮)
```

### 新增文件

```
music-client/src/components/layouts/
├── YinAiAssistant.vue   ← 浮动按钮 + 聊天面板主体
├── YinAiMessage.vue      ← 单条消息气泡渲染
└── YinAiTyping.vue       ← 打字动画指示器
```

### YinAiAssistant.vue 核心设计

```
Props: 无
State:
  - visible: boolean           // 聊天面板是否展开
  - messages: Message[]        // 消息列表
  - inputText: string          // 输入框内容
  - loading: boolean           // 等待AI回复

消息结构 Message:
  - role: 'user' | 'assistant'
  - content: string            // 纯文本（已过滤控制标记）
  - rawContent?: string        // 含控制标记的原始回复

浮动按钮:
  - 位置: position:fixed; right:10px; bottom:130px
  - 样式: 圆形, 主题色背景, CSS呼吸动画
  - 图标: Element Plus Music 或 Service 图标
  - 首次显示气泡提示 "音乐小助手"

聊天面板:
  - 位置: position:fixed; right:10px; bottom:180px
  - 尺寸: width:380px; height:520px
  - 样式: 圆角卡片, 白色背景, 阴影
  - z-index: 高于 PlayBar 和 ScrollTop

音乐控制标记解析:
  - 渲染前从 content 中提取 [PLAY:...] [PAUSE] 等标记
  - 标记不展示给用户，仅触发 Vuex store 操作
  - 支持: [PLAY:歌名-歌手] [PAUSE] [RESUME] [NEXT] [VOLUME:0-100]
```

### 音乐控制流程

```
用户输入 "播放周杰伦的晴天"
  → POST /ai/chat { messages: [...] }
  → AI 回复: "好的！为你搜索周杰伦的《晴天》🎵\n[PLAY:晴天-周杰伦]"
  → 前端解析: 提取 [PLAY:晴天-周杰伦]
  → 过滤标记后展示文本: "好的！为你搜索周杰伦的《晴天》🎵"
  → 调用 HttpManager.getSongList() 或搜索接口查找
  → 取第一个匹配结果
  → Vuex store.dispatch('song/playMusic', song)
  → 播放器开始播放
```

### API 调用

```
沿用现有 HttpManager 模式，在 music-client/src/api/index.ts 新增:

sendAiMessage: (data) => post('ai/chat', data)
```

## 后端设计

### 新增文件

```
music-server/src/main/java/com/example/music/
├── controller/
│   └── AiController.java
├── service/
│   └── AiService.java (接口)
│   └── impl/
│       └── AiServiceImpl.java
└── model/
    └── ChatRequest.java
```

### API 端点

```
POST /ai/chat

Request:
{
  "messages": [
    { "role": "user", "content": "推荐几首适合下雨天听的歌" }
  ]
}

Response:
{
  "code": 200,
  "message": "成功",
  "data": {
    "reply": "下雨天最适合听一些舒缓的..."
  }
}
```

### AiServiceImpl 核心逻辑

```
1. 接收 ChatRequest (messages 列表)
2. 构建 fullMessages:
   [0] { role: "system", content: SYSTEM_PROMPT + siteContext }
   [1..n] ChatRequest.messages
3. 调用 DeepSeek API:
   POST https://api.deepseek.com/chat/completions
   Headers:
     Authorization: Bearer {DEEPSEEK_API_KEY}
     Content-Type: application/json
   Body:
     {
       "model": "deepseek-chat",
       "messages": fullMessages,
       "temperature": 0.7,
       "max_tokens": 1000
     }
4. 解析 response.choices[0].message.content
5. 返回 { reply: content }
```

### 网站数据注入

```
每次请求时，在 system prompt 末尾追加当前网站数据:

【当前网站热门数据】
热门歌曲: {动态从数据库查询top10}
热门歌手: {动态从数据库查询top10}
推荐歌单: {动态从数据库查询top5}
```

### 配置 (application.properties)

```properties
# DeepSeek AI
ai.api.key=sk-your-deepseek-api-key
ai.api.url=https://api.deepseek.com
ai.model=deepseek-chat
```

### 依赖

使用 Spring Boot 内建的 RestTemplate，无需额外 Maven 依赖。

## AI 提示词设计

### System Prompt

```
你是"音乐小助手"，一个对音乐充满热情且知识渊博的 AI 助手。

【性格】
- 温暖、友好，像一位懂音乐的知心朋友
- 回答简洁有料，不要太啰嗦
- 适当使用音乐相关的 emoji（🎵 🎶 🎸 🎹 🥁 🎤）

【能力范围】
- 音乐知识：乐理、音乐史、流派风格、乐器、作曲技法
- 歌手/乐队：生平介绍、代表作品、风格特点、趣闻轶事
- 歌曲推荐：根据心情、场景、喜好推荐适合的歌曲
- 音乐资讯：行业动态、音乐节、演唱会信息
- 音乐控制：帮用户搜索歌曲、播放/暂停、调节音量

【音乐控制格式】
当用户想操作播放器时，在回复末尾用以下格式标记：
[PLAY:歌曲名-歌手名]  表示搜索并播放
[PAUSE]               表示暂停
[RESUME]              表示继续播放
[NEXT]                表示下一首
[VOLUME:数值]         表示调节音量（0-100）
注意：这些标记是给前端程序识别的指令，不要向用户解释这些标记的含义。

【边界】
- 只回答音乐相关问题，如果用户问无关话题，礼貌地引导回音乐主题
- 不确定的事情坦诚说不知道，不要编造
- 用中文回答
```

## 数据流总结

```
1. 用户输入消息
2. 前端追加到本地 messages 列表
3. POST /ai/chat 发送完整消息历史
4. 后端注入 system prompt + 网站数据
5. 转发到 DeepSeek API
6. 接收 AI 回复文本
7. 前端解析控制标记 → 触发播放器操作
8. 过滤标记 → 展示纯文本回复
```
