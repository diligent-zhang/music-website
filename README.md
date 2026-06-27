# music-website

> 在线音乐网站 —— Vue3 + Spring Boot + MyBatis-Plus 全栈项目

[![License](https://img.shields.io/badge/License-CC%20BY--NC%204.0-lightgrey.svg)](music-website-master/LICENSE)

## 项目介绍

一个功能完整的在线音乐平台，支持音乐播放、用户登录注册、歌单管理、评论收藏、演唱会购票、AI 音乐助手等功能。包含用户前台和管理后台两个前端，前后端分离架构。
该项目借鉴于https://github.com/Yin-Hongwei/music-website；
我对该项目进行了一些功能的添加，包括排行榜、演唱会购票、支付功能、ai音乐小助手等功能。其中ai助手能够识别用户要播放歌曲的要求并做出自动播放音乐的功能。
项目演示地址：

### 功能清单

**用户端**
- 音乐播放（播放/暂停、上下曲、音量控制、进度拖动、歌词同步）
- 用户注册登录（用户名 + 邮箱两种方式）
- 忘记密码（邮箱验证码重置）
- 个人信息编辑、头像上传
- 歌曲/歌单/歌手搜索
- 歌单列表、歌手列表分页展示
- 歌单评分、歌曲评论
- 歌曲/歌单收藏
- 音乐下载
- 音符充值（虚拟货币）
- 演唱会浏览 & 购票（二维码入场凭证）
- AI 音乐助手（DeepSeek）

**管理端**
- 用户管理
- 歌手管理
- 歌单管理
- 歌曲管理
- 评论管理
- 收藏/评分管理
- 票务订单管理

---

## 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue 3 + TypeScript + Vue Router + Vuex + Axios + Element Plus + ECharts |
| 后端 | Spring Boot 2.6 + MyBatis-Plus 3.5 + Redis + MinIO |
| 数据库 | MySQL 8.0 |
| AI | LangChain4j + DeepSeek API + BGE-small-zh 嵌入模型 |
| 部署 | Docker Compose |

> 详细架构说明见 [ARCHITECTURE.md](ARCHITECTURE.md)

---

## 性能优化专项

| 文档 | 方向 | 核心技术 | 优化效果 |
|------|------|---------|---------|
| [高并发抢票优化](music-website-master/docs/ticket-concurrency-optimization.md) | 票务抢购 | Redis Lua 原子扣库存、SETNX 去重、@Async 异步写库、原子 SQL 扣余额 | 响应中位数 29ms，200 并发 0 超卖 0 重复 |
| [歌单查询优化](music-website-master/docs/performance-optimization-summary.md) | 列表查询 | MyBatis-Plus 分页、三表 JOIN 合并、Redis 分页缓存 | 100 首歌加载 3s → 300ms |

---

## 快速开始

### 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | Java 运行环境 |
| MySQL | 8.0+ | 主数据库 |
| Redis | 5.0+ | 缓存服务 |
| MinIO | latest | 对象存储（图片/音频上传） |
| Node.js | 14.17+ | 前端运行环境 |
| Maven | 3.6+ | 后端构建（或使用 ./mvnw） |

### 1. 克隆项目

```bash
git clone git@github.com:Yin-Hongwei/music-website.git
cd music-website
```

### 2. 初始化数据库

创建 MySQL 数据库，导入 SQL 文件：

```sql
CREATE DATABASE IF NOT EXISTS tp_music DEFAULT CHARACTER SET utf8mb4;
```

然后导入 `music-server/sql/tp_music.sql`：

```bash
mysql -u root -p tp_music < music-server/sql/tp_music.sql
```

### 3. 启动 Redis

```bash
redis-server
```

或使用 Docker：

```bash
docker run -d --name redis -p 6379:6379 redis:7
```

### 4. 启动 MinIO

MinIO 用于存储用户头像、歌曲文件和封面图片。

**方式一：直接下载运行**

从 [MinIO 官网](https://min.io/download) 下载对应平台的二进制文件：

```bash
# Linux/macOS
./minio server /data --console-address ":9001" --address ":9005"

# Windows
minio.exe server D:\minio-data --console-address ":9001" --address ":9005"
```

**方式二：Docker 启动（推荐）**

```bash
docker run -d \
  --name minio \
  -p 9005:9005 \
  -p 9001:9001 \
  -e MINIO_ROOT_USER=root \
  -e MINIO_ROOT_PASSWORD=12345678 \
  -v /data/minio:/data \
  minio/minio server /data --console-address ":9001" --address ":9005"
```

启动后访问 http://localhost:9001 进入 MinIO 控制台，使用上面的账号密码登录。

**创建 Bucket：**

登录 MinIO 控制台后，创建一个名为 `user01` 的 bucket（与配置文件中的 `minio.bucket-name` 一致），并将 bucket 的 Access Policy 设为 `public`（允许公开读取）。

### 5. 配置文件修改

编辑 `music-server/src/main/resources/application-dev.properties`：

```properties
# 数据库配置（修改为你自己的用户名密码）
spring.datasource.url=jdbc:mysql://localhost:3306/tp_music?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=你的数据库用户名
spring.datasource.password=你的数据库密码

# MinIO 配置（对应你启动 MinIO 时设置的用户名密码）
minio.endpoint=http://localhost:9005
minio.access-key=root
minio.secret-key=12345678
minio.bucket-name=user01
```

如需使用邮箱验证码功能，编辑 `music-server/src/main/resources/application.yml`：

```yaml
spring:
  mail:
    host: smtp.163.com
    port: 465
    username: 你的邮箱地址
    password: 你的邮箱授权码
mail:
  address: 你的邮箱地址
```

如需使用 AI 助手功能，编辑 `music-server/src/main/resources/application.properties`：

```properties
ai.api.key=你的DeepSeek-API-Key
ai.api.url=https://api.deepseek.com
ai.model=deepseek-chat
```

### 6. 启动后端

```bash
cd music-server

# Linux/macOS
./mvnw spring-boot:run

# 或使用 Maven
mvn spring-boot:run
```

后端启动后运行在 http://localhost:8888。

### 7. 启动前端

**用户端：**

```bash
cd music-client
npm install
npm run serve
```

启动后访问 http://localhost:8080。

**管理端：**

```bash
cd music-manage
npm install
npm run serve
```

启动后访问管理后台（默认端口 8081 或其他可用端口）。

---

## 充值收款码配置

充值时显示的收款码图片位于 `music-server/img/pay-qr-placeholder.jpg`。

替换为你自己的微信/支付宝收款码：

1. 将你的收款码图片重命名为 `pay-qr-placeholder.jpg`
2. 放到 `music-server/img/` 目录下覆盖原文件

图片路径已在 `music-client/src/components/dialog/YinRechargeDialog.vue` 中配置：

```ts
const qrCodeUrl = ref('/img/pay-qr-placeholder.jpg');
```

充值流程：用户扫码支付 → 填写交易流水号 → 系统验证流水号格式（≥6 位）→ 余额到账。

---

## Docker 部署（Linux 服务器）

将以下文件放到服务器同一目录：

- 前端构建产物（`music-client/dist`、`music-manage/dist`）
- 后端 jar 包（`mvn package` 编译）
- Nginx 配置
- `docker-compose.yml`

```bash
docker compose up --build
```

---

## 常见问题

**Q：图片或音乐加载失败？**

确保 `music-server/img/`、`music-server/song/` 目录存在且包含对应资源文件。

**Q：音乐无法播放？**

检查音频文件是否损坏，可尝试更换音乐资源。音频文件通过 `/audio/{fileName}` 接口提供流式播放。

**Q：MinIO 连接失败？**

1. 确认 MinIO 服务是否启动：访问 http://localhost:9001
2. 确认 `application-dev.properties` 中的 endpoint、access-key、secret-key 是否正确
3. 确认 bucket `user01` 是否已创建

**Q：Redis 连接失败？**

确认 Redis 服务已启动，默认连接 `127.0.0.1:6379`，无密码。

**Q：忘记密码功能不可用？**

需要在 `application.yml` 中配置真实的邮箱账号和 SMTP 授权码。

---

## 开源协议

本项目基于 [CC BY-NC 4.0](music-website-master/LICENSE) 协议开源，仅供学习交流使用，**禁止商业用途**。

Copyright (c) 2018-2025 Yin-Hongwei

该项目借鉴于开源项目:https://github.com/Yin-Hongwei/music-website；

由于本人技术并不是很强，当前项目不够完善，存在一些问题，希望未来能够有充足的能力去完善该项目。
