# 音频播放修复测试指南

## 🧪 测试步骤

### 1. 启动服务
```bash
# 启动后端服务
cd music-website-master/music-server
mvn spring-boot:run

# 启动前端服务  
cd music-website-master/music-client
npm run serve
```

### 2. 验证修复效果

#### 测试点1: 音频URL生成
- 打开浏览器开发者工具
- 访问歌曲列表页面
- 点击任意歌曲播放
- 检查Network标签页，确认音频请求URL格式为: `http://localhost:8888/audio/文件名.mp3`

#### 测试点2: 404错误解决
- 确认音频文件能正常加载（状态码200）
- 检查Response Headers包含正确的Content-Type: audio/mpeg

#### 测试点3: 播放功能
- 验证音频能正常播放
- 检查播放控制功能（播放/暂停/进度条）
- 确认没有JavaScript错误

### 3. 常见问题排查

#### 问题1: 仍然出现404
检查点：
- 数据库中歌曲的url字段格式是否正确
- 本地song目录下是否存在对应文件
- AudioController是否正确接收请求

#### 问题2: 音频无法播放
检查点：
- 浏览器控制台是否有CORS错误
- 音频文件格式是否支持（MP3/M4A）
- Response Headers是否正确设置

#### 问题3: 路径解析错误
检查点：
- Constants.SONG_PATH配置是否正确
- 文件系统路径权限
- 中文文件名编码问题

## 📊 预期结果

✅ 音频URL正确生成为: `http://localhost:8888/audio/uuid.mp3`
✅ 后端能正确找到并返回音频文件
✅ 前端能正常播放音频，无404错误
✅ 播放控制功能完整可用