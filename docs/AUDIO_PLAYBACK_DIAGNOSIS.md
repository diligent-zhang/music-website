# 音频播放问题诊断报告

## 🔍 问题分析

### 1. 链接拼接流程
```
前端组件 → Vuex存储 → YinAudio.vue → attachImageUrl() → 后端Controller → 本地文件系统
```

### 2. 具体流程追踪

**前端调用链:**
1. `SongList.vue` 中的 `handleClick(row)` 调用 `playMusic()`
2. `playMusic()` 通过Vuex的 `dispatch("playMusic", {...})` 存储歌曲信息
3. `YinAudio.vue` 组件通过 `:src="attachImageUrl(songUrl)"` 获取音频源
4. `HttpManager.attachImageUrl(url)` 处理: `${getBaseURL()}/${url}`

**后端处理链:**
1. `FileDownloadController` 接收 `/download/{fileName}` 请求
2. 构建本地文件路径: `Constants.SONG_PATH + fileName`
3. `Constants.SONG_PATH = "file:" + ASSETS_PATH + "/song/"`
4. 实际查找路径: `C:\学习\简历项目\music-website-master 音乐\song\{fileName}`

### 3. 关键问题点

#### 问题1: 路径不匹配
- **前端期望**: `http://localhost:8888/song/uuid.mp3` (通过attachImageUrl)
- **后端处理**: `/download/uuid.mp3` (FileDownloadController)
- **实际文件**: `C:\学习\简历项目\music-website-master 音乐\song\uuid.mp3`

#### 问题2: URL格式不一致
数据库中的歌曲URL可能是:
- `/song/uuid.mp3` (相对路径)
- `song/uuid.mp3` (相对路径，无前导斜杠)
- 完整的MinIO路径格式

### 4. 解决方案

需要统一以下几点:
1. **路径格式标准化**: 确保数据库存储的URL格式一致
2. **前端URL处理**: 修改attachImageUrl逻辑适配本地文件访问
3. **后端路由映射**: 确保正确的Controller处理音频请求

## 🛠️ 建议修复方案

### 方案一: 统一使用下载接口
修改前端直接使用 `/download/{fileName}` 路径

### 方案二: 添加专门的音频访问接口
创建 `/audio/{fileName}` 接口专门处理音频播放

### 方案三: 修正路径映射
确保前端生成的URL能正确映射到后端处理逻辑