# 从MinIO存储转换为本地文件存储的总结

## 1. 修改概述

我们成功将项目从MinIO存储系统转换为本地文件存储系统，以下是主要修改内容：

## 2. 修改的服务类

### 2.1 SongServiceImpl
- 替换了 `addSong`、`updateSongUrl` 和 `updateSongPic` 方法中的MinIO上传逻辑
- 更改为使用 `FileUtils.saveLocally()` 进行本地文件上传
- 更新了文件路径为本地存储路径格式

### 2.2 ConsumerServiceImpl
- 替换了 `updateUserAvator` 方法中的MinIO上传逻辑
- 更改为使用 `FileUtils.saveLocally()` 进行本地头像上传
- 文件保存到 `img/avatorImages` 目录

### 2.3 SingerServiceImpl
- 替换了 `updateSingerPic` 方法中的MinIO上传逻辑
- 更改为使用 `FileUtils.saveLocally()` 进行本地歌手图片上传
- 文件保存到 `img/singerPic` 目录

## 3. 新增的工具类

### 3.1 FileUtils
- 创建了 `FileUtils` 工具类处理本地文件操作
- 包含文件上传、删除等功能
- 使用UUID生成唯一的文件名避免冲突

## 4. 路径配置

利用项目中现有的 `Constants` 和 `WebPicConfig` 配置：

- 头像图片：`/img/avatorImages/**` → `[项目根目录]/img/avatorImages/`
- 歌手图片：`/img/singerPic/**` → `[项目根目录]/img/singerPic/`
- 歌曲图片：`/img/songPic/**` → `[项目根目录]/img/songPic/`
- 歌曲音频：`/song/**` → `[项目根目录]/song/`
- 歌单图片：`/img/songListPic/**` → `[项目根目录]/img/songListPic/`

## 5. 异常处理

- 使用try-catch块处理文件操作异常
- 返回适当的错误信息给前端
- 确保程序的健壮性

## 6. 优点

- 减少了对外部服务（MinIO）的依赖
- 简化了文件存储架构
- 降低了部署复杂度
- 利用了Spring Boot内置的静态资源配置

## 7. 注意事项

- 确保服务器有足够的磁盘空间
- 设置适当的文件权限
- 定期清理不需要的文件
- 考虑备份策略