# music-website 架构文档

## 项目概述

本项目是一个在线音乐网站，包含用户端（music-client）、管理后台（music-manage）和 Java 服务端（music-server）三个模块，前后端分离架构。

---

## 目录结构

```
music-website/
├── music-client/          # 用户前台（Vue3 + TypeScript）
│   └── src/
│       ├── api/           # API 接口封装（Axios）
│       ├── assets/        # 静态资源（CSS、图标、图片）
│       ├── components/    # 公共组件（布局、弹窗）
│       ├── enums/         # 枚举常量
│       ├── mixins/        # Vue mixin 复用逻辑
│       ├── router/        # Vue Router 路由配置
│       ├── store/         # Vuex 状态管理
│       ├── types/         # TypeScript 类型定义
│       ├── utils/         # 工具函数
│       └── views/         # 页面组件
│           ├── favorite/      # 我的收藏
│           ├── personal/      # 个人中心
│           ├── rank/          # 排行榜
│           ├── search/        # 搜索
│           ├── setting/       # 设置（个人信息、密码、头像）
│           ├── singer/        # 歌手列表 & 详情
│           ├── song-sheet/    # 歌单列表 & 详情
│           └── ticket/        # 演唱会购票
│
├── music-manage/          # 管理后台（Vue3 + TypeScript）
│   └── src/
│       ├── api/           # API 接口封装
│       ├── components/    # 公共组件
│       └── views/         # 管理页面（用户/歌手/歌单/歌曲/评论管理）
│
├── music-server/          # Java 服务端（Spring Boot 2.6 + MyBatis-Plus）
│   ├── sql/               # 数据库初始化 SQL
│   │   └── tp_music.sql
│   ├── img/               # 本地静态资源（图片、歌曲封面、付款码）
│   │   ├── songPic/       # 歌曲封面
│   │   ├── songListPic/   # 歌单封面
│   │   └── pay-qr-placeholder.jpg  # 充值收款码
│   └── src/main/
│       ├── resources/
│       │   ├── application.properties       # 主配置（端口、Redis）
│       │   ├── application-dev.properties   # 开发环境（MySQL、MinIO）
│       │   ├── application-prod.properties  # 生产环境
│       │   ├── application.yml              # 邮件 & MyBatis-Plus 配置
│       │   └── mapper/                      # MyBatis XML 映射文件
│       └── java/com/example/yin/
│           ├── YinMusicApplication.java     # Spring Boot 启动类
│           ├── common/          # 统一响应体 R
│           ├── config/          # 配置类（CORS、Redis、MinIO、WebMvc）
│           ├── constant/        # 常量（缓存 Key、业务常量）
│           ├── controller/      # 控制器层（20 个 Controller）
│           ├── handler/         # 异常处理
│           ├── mapper/          # MyBatis Mapper 接口
│           ├── model/
│           │   ├── domain/      # 数据库实体
│           │   └── request/     # 请求体 DTO
│           ├── service/
│           │   ├── impl/        # 服务实现
│           │   ├── rag/         # RAG 检索增强生成
│           │   └── tool/        # 工具服务（邮件发送）
│           ├── store/           # 数据存储抽象
│           ├── task/            # 定时任务
│           └── utils/           # 工具类（缓存防护、文件处理、MinIO）
│
└── docs/                   # 需求文档 & 设计文档
```

---

## 分层架构

```
┌─────────────────────────────────────────────────┐
│                  前端层 (Vue3)                    │
│  music-client (用户端)  │  music-manage (管理端)  │
├─────────────────────────────────────────────────┤
│              HTTP REST API (JSON)                │
├─────────────────────────────────────────────────┤
│               Controller 层 (20个)               │
│  请求参数校验、路由映射、Session 管理              │
├─────────────────────────────────────────────────┤
│                Service 层                        │
│  业务逻辑、事务管理、缓存防护、RAG 检索            │
├─────────────────────────────────────────────────┤
│                Mapper 层                         │
│  MyBatis-Plus + XML 映射、数据库交互              │
├─────────────────────────────────────────────────┤
│            基础设施层                             │
│  MySQL 8.0  │  Redis 5+  │  MinIO (对象存储)     │
│  DeepSeek API (AI 对话)  │  SMTP (邮件)          │
└─────────────────────────────────────────────────┘
```

---

## 后端模块详解

### 核心业务模块

| 模块 | 涉及 Controller | 说明 |
|---|---|---|
| 用户系统 | ConsumerController | 注册/登录/信息修改/头像上传/密码重置/余额管理/充值 |
| 音乐播放 | SongController, AudioController | 歌曲查询、音频流播放、歌词获取 |
| 歌单管理 | SongListController, ListSongController | 歌单 CRUD、歌单内歌曲管理 |
| 歌手管理 | SingerController | 歌手列表、按性别筛选 |
| 评论系统 | CommentController | 评论发表/删除/点赞 |
| 收藏系统 | CollectController | 歌曲/歌单收藏 |
| 评分系统 | RankListController | 歌单评分 |
| 排行榜 | RankController | 播放次数排行、播放计数 |
| 演唱会购票 | ConcertController, TicketController, VerifyController | 演出列表、购票、订单、二维码核验 |
| 海报轮播 | BannerController | 首页轮播图 |
| AI 对话 | AiController | DeepSeek API 聊天 |
| 文件管理 | MinioUploadController, MinioController, FileDownloadController | MinIO 上传、下载 |

### 关键技术点

**缓存防护（Cache Protection）**
- 高频全量查询（如用户列表）使用 Redis 缓存，设置 TTL 过期
- 写操作后主动淘汰缓存，避免脏数据
- 防止缓存击穿：空值短期缓存

**RAG 检索增强生成**
- 基于 BGE-small-zh 中文嵌入模型
- 结合 LangChain4j 实现音乐知识检索问答

**票务系统**
- 二维码生成（qrcode 库）与核验
- 订单状态管理（待支付 → 已支付 → 已核验）
- 入场核验链接：`{origin}/api/verify/{qrCodeToken}`

**音符虚拟货币**
- 余额查询/充值/扣款
- 充值需输入交易流水号验证（6 位以上）

**MinIO 对象存储**
- 头像、歌曲文件、封面图片上传到 MinIO
- 支持本地 MinIO 或 Docker 部署

**跨域处理**
- WebMvcConfig 全局 CORS 配置
- `allowedOriginPatterns: "*"`

---

## 前端技术栈

| 技术 | 说明 |
|---|---|
| Vue 3.x | 组合式 API + Composition API |
| TypeScript | 类型安全 |
| Vue Router 4 | SPA 路由 |
| Vuex 4 | 全局状态管理（用户信息、播放状态） |
| Axios | HTTP 请求封装 |
| Element Plus | UI 组件库 |
| ECharts | 数据可视化（管理后台） |
| QRCode | 票务二维码生成 |

### 前端核心功能

- **音乐播放器**：播放/暂停、上下曲、音量控制、进度拖动、歌词同步
- **搜索系统**：歌曲搜索、歌单搜索、歌手搜索
- **用户系统**：注册/登录（用户名+邮箱两种方式）、忘记密码、个人资料编辑、头像上传
- **收藏 & 评分**：歌曲收藏、歌单评分
- **票务系统**：演唱会浏览、购票、订单查看、二维码入场凭证
- **充值系统**：扫码支付 + 交易流水号验证

---

## 数据流示例

### 用户充值流程
```
用户点击充值 → 弹出收款码 + 金额选择 + 流水号输入
  → 前端校验（金额 > 0、流水号非空）
    → POST /user/yinbi/recharge {userId, amount, transactionNo}
      → ConsumerController 提取参数
        → ConsumerService.rechargeYinbi() 校验流水号长度 ≥ 6
          → 更新数据库 yinbi 余额
            → 返回最新余额 → 前端更新 Vuex
```

### 音乐播放流程
```
用户点击歌曲 → Song.vue 触发 play
  → Vuex 更新当前播放歌曲信息
    → Audio 组件监听变化
      → GET /song/detail?id=xxx 获取歌曲元信息
        → GET /audio/{fileName} 获取音频流
          → HTML5 Audio 播放 + 歌词 Canvas 渲染
```

---

## 数据库核心表

| 表名 | 说明 |
|---|---|
| consumer | 用户表（含 yinbi 余额字段） |
| singer | 歌手表 |
| song | 歌曲表 |
| song_list | 歌单表 |
| list_song | 歌单-歌曲关联表 |
| comment | 评论表 |
| collect | 收藏表 |
| rank_list | 评分表 |
| concert | 演唱会表 |
| ticket_order | 票务订单表（含 qr_code_token） |
| banner | 首页轮播图表 |
| user_support | 评论点赞表 |

---

## 环境依赖

| 依赖 | 版本 | 用途 |
|---|---|---|
| JDK | 17 | Java 运行环境 |
| MySQL | 8.0+ | 主数据库 |
| Redis | 5.0+ | 缓存 & Session |
| MinIO | latest | 对象存储（图片/音频） |
| Node.js | 14.17+ | 前端构建 |
| Maven | 3.6+ | 后端构建 |

---

## 部署架构（Docker）

```
┌──────────┐  ┌──────────┐  ┌──────────┐
│  Nginx   │  │   jar    │  │  MinIO   │
│  前端静态 │  │  Spring  │  │  对象存储 │
│   :80    │  │  :8888   │  │  :9005   │
└──────────┘  └──────────┘  └──────────┘
                    │
           ┌────────┴────────┐
      ┌────┴────┐      ┌────┴────┐
      │  MySQL  │      │  Redis  │
      │  :3306  │      │  :6379  │
      └─────────┘      └─────────┘
```
