# 音乐网站项目功能概览

## 项目架构概述

这是一个基于前后端分离的音乐网站项目，包含三个主要部分：

1. **前端客户端** (music-client)：基于 Vue 3 + TypeScript + Element Plus 构建
2. **管理后台** (music-manage)：用于内容管理的后台系统
3. **后端服务** (music-server)：基于 Spring Boot + MySQL + Redis 的 RESTful API 服务

## 技术栈

### 前端技术栈
- **框架**: Vue 3.2.13
- **UI 框架**: Element Plus 2.0.4
- **路由**: Vue Router 4.0.3
- **状态管理**: Vuex 4.0.0
- **HTTP 客户端**: Axios 1.13.3
- **构建工具**: Vue CLI ~5.0.0
- **语言**: TypeScript

### 后端技术栈
- **框架**: Spring Boot 2.6.2
- **数据库**: MySQL 8.0.16
- **ORM**: MyBatis-Plus 3.5.1
- **缓存**: Redis
- **对象存储**: MinIO
- **其他**: Lombok, FastJSON, Apache Commons

## 功能模块分析

### 前端客户端功能模块

#### 1. 用户模块
- 用户注册 (sign-up)
- 用户登录 (sign-in)
- 密码找回 (FPassword)
- 邮箱登录 (loginByemail)
- 个人中心 (personal)
- 个人资料设置 (personal-data)

#### 2. 音乐播放模块
- 歌曲列表展示
- 音乐播放控制
- 歌词显示 (lyric)

#### 3. 歌单模块
- 歌单列表 (song-sheet)
- 歌单详情页 (song-sheet-detail)

#### 4. 歌手模块
- 歌手列表 (singer)
- 歌手详情页 (singer-detail)

#### 5. 搜索模块
- 全局搜索功能 (search)

#### 6. 设置模块
- 个人设置 (setting)

### 后端服务功能模块

#### 1. 用户管理模块
- 用户注册与登录
- 用户信息管理
- 用户权限控制

#### 2. 音乐资源管理
- 歌曲信息管理
- 歌词管理
- 音乐文件存储

#### 3. 歌单管理
- 歌单创建与编辑
- 歌单歌曲管理

#### 4. 歌手管理
- 歌手信息管理
- 歌手分类

#### 5. 评论管理
- 音乐评论
- 评论审核

#### 6. 系统管理
- 文件上传 (MinIO)
- 缓存管理 (Redis)
- 数据统计

## 项目启动流程与常见问题解决

### 启动顺序
1. **数据库服务** - MySQL 必须先启动
2. **缓存服务** - Redis 可选但推荐启动
3. **对象存储** - MinIO 可选
4. **后端服务** - Spring Boot 应用 (默认端口 8888)
5. **前端服务** - Vue 应用 (默认端口 8080)

### 前端页面无法显示的可能原因及解决方案

#### 1. 后端服务未启动
- **问题**: 前端请求无法到达后端 API
- **解决方案**: 
  - 确保在 music-server 目录下执行 `mvn spring-boot:run` 启动后端服务
  - 检查后端服务是否在 8888 端口正常运行

#### 2. 数据库连接失败
- **问题**: 后端无法连接到 MySQL 数据库
- **解决方案**:
  - 确认 MySQL 服务已启动
  - 检查 `application-dev.properties` 中的数据库连接参数
  - 默认配置: URL: jdbc:mysql://localhost:3306/tp_music, 用户名: root, 密码: root

#### 3. 环境配置问题
- **问题**: 前端 API 地址配置错误
- **解决方案**:
  - 检查 `vue.config.js` 中的 `NODE_HOST` 配置，默认指向 "http://localhost:8888"
  - 确保此地址与后端服务实际运行地址一致

#### 4. 依赖安装问题
- **问题**: Node.js 包或 Maven 依赖未正确安装
- **解决方案**:
  - 在 music-client 目录下执行 `npm install`
  - 在 music-server 目录下执行 `mvn clean install`

#### 5. 跨域问题
- **问题**: 前后端端口不同导致跨域
- **解决方案**:
  - 后端已配置跨域支持，确保前端请求携带凭据 (`withCredentials = true`)

### 启动命令示例

#### 后端启动
```bash
cd music-website-master/music-server
mvn spring-boot:run
```

#### 前端启动
```bash
cd music-website-master/music-client
npm install
npm run serve
```

### 端口配置
- **后端 API**: http://localhost:8888
- **前端开发服务器**: 默认由 Vue CLI 自动分配 (通常是 http://localhost:8080)
- **数据库**: MySQL 默认 3306 端口
- **缓存**: Redis 默认 6379 端口
- **对象存储**: MinIO 默认 9000 端口

## 开发建议

1. **启动顺序至关重要** - 先启动后端再启动前端
2. **数据库初始化** - 使用 sql/tp_music.sql 初始化数据库
3. **环境变量一致性** - 确保前后端配置中的端口和服务地址匹配
4. **日志监控** - 关注前后端日志输出以排查问题
5. **依赖管理** - 定期更新依赖包，注意版本兼容性

## 项目特点

- 前后端完全分离，便于独立开发和部署
- 响应式设计，支持多设备访问
- 完整的用户认证和授权机制
- 支持多媒体文件上传和管理
- 具备基本的音乐播放和管理功能
- 提供管理后台用于内容维护