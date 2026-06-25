# 音频404错误调试指南

## 🎯 问题现象
进入页面显示"音频播放失败: 资源不存在（404，URL错误）"

## 🔍 排查步骤

### 1. 启动诊断工具
```bash
cd music-website-master/music-server
javac -cp "src/main/java;C:\Users\Public\.m2\repository\mysql\mysql-connector-java\8.0.28\mysql-connector-java-8.0.28.jar" src/main/java/com/example/yin/util/AudioPathDiagnostic.java
java -cp "src/main/java;C:\Users\Public\.m2\repository\mysql\mysql-connector-java\8.0.28\mysql-connector-java-8.0.28.jar" com.example.yin.util.AudioPathDiagnostic
```

### 2. 检查后端日志
启动后端服务后，播放音频时观察控制台输出：
```
=== 音频请求诊断 ===
请求文件名: xxx.mp3
歌曲目录路径: C:\学习\简历项目\music-website-master 音乐\song
完整文件路径: C:\学习\简历项目\music-website-master 音乐\song\xxx.mp3
文件是否存在: true/false
```

### 3. 常见问题及解决方案

#### 问题1: 数据库URL格式不正确
**现象**: 文件名提取错误
**解决方案**: 
- 检查数据库中song表的url字段格式
- 应该是类似 `/song/uuid.mp3` 或 `uuid.mp3` 的格式

#### 问题2: 文件实际不存在
**现象**: 路径存在但文件不存在
**解决方案**:
- 确认song目录下有对应文件
- 检查文件名是否匹配（包括大小写、扩展名）

#### 问题3: 路径解析错误
**现象**: 完整路径拼接错误
**解决方案**:
- 检查Constants.SONG_PATH配置
- 确认路径分隔符正确处理

### 4. 临时解决方案

如果急需测试，可以：
1. 手动复制一个音频文件到song目录
2. 在数据库中添加对应的记录
3. 测试播放功能

### 5. 预期输出示例
```
=== 音频路径诊断工具 ===

1. 常量配置检查:
   ASSETS_PATH: C:\学习\简历项目\music-website-master 音乐
   SONG_PATH: file:C:\学习\简历项目\music-website-master 音乐/song/

2. 歌曲目录检查:
   目录路径: C:\学习\简历项目\music-website-master 音乐\song
   目录存在: true
   目录可读: true
   文件数量: 50

3. 数据库歌曲检查:
   歌曲ID: 1
   歌曲名: 测试歌曲
   数据库URL: /song/123e4567-e89b-12d3-a456-426614174000.mp3
   文件路径: C:\学习\简历项目\music-website-master 音乐\song\123e4567-e89b-12d3-a456-426614174000.mp3
   文件存在: true
```

## 🛠️ 紧急修复步骤

如果上述方法都无法解决，可以尝试：

1. **清理并重建项目**:
   ```bash
   cd music-website-master/music-server
   mvn clean install
   ```

2. **检查Java版本兼容性**:
   ```bash
   java -version
   ```

3. **手动验证路径**:
   - 确认song目录存在且有音频文件
   - 检查数据库连接正常
   - 验证文件权限