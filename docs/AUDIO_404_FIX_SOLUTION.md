# 音频404问题具体解决方案

## 🎯 问题确认
数据库中歌曲的URL指向不存在的文件：
- 数据库请求: `张杰-逆战.mp3`
- 实际存在: 其他文件名（如 `a_hisa - night.mp3` 等）

## 🛠️ 立即可执行的解决方案

### 方案一：临时测试（最快）
1. **选择一个现有文件进行测试**：
   ```
   请求URL: http://localhost:8888/audio/a_hisa - night.mp3
   ```

2. **或者更新一条数据库记录**：
   ```sql
   UPDATE song SET url = '/song/a_hisa - night.mp3' WHERE id = 1 LIMIT 1;
   ```

### 方案二：批量修复数据
1. **查看所有歌曲记录**：
   ```sql
   SELECT id, name, url FROM song;
   ```

2. **更新URL为实际存在的文件名**：
   ```sql
   -- 示例更新语句
   UPDATE song SET url = '/song/a_hisa - night.mp3' WHERE id = 1;
   UPDATE song SET url = '/song/A虂lvaro Soler-El Mismo Sol.mp3' WHERE id = 2;
   -- 继续为其他记录设置对应的URL
   ```

### 方案三：自动化修复脚本
```sql
-- 创建修复映射表
UPDATE song s 
JOIN (
    SELECT 'a_hisa - night.mp3' as filename, 1 as song_id
    UNION SELECT 'A虂lvaro Soler-El Mismo Sol.mp3', 2
    UNION SELECT 'A虂lvaro Soler-Sofi虂a.mp3', 3
    -- 继续添加其他文件映射
) mapping ON s.id = mapping.song_id
SET s.url = CONCAT('/song/', mapping.filename);
```

## 📋 验证步骤

执行修复后验证：
1. 重新启动后端服务
2. 访问歌曲页面
3. 播放歌曲，观察是否还出现404错误
4. 检查后端日志显示"文件是否存在: true"

## ⚠️ 注意事项

- **备份数据库**：执行更新前先备份
- **逐步测试**：建议先修复1-2条记录进行测试
- **编码问题**：注意中文文件名的编码一致性
- **文件权限**：确保服务有读取音频文件的权限

推荐使用方案一进行快速测试验证，确认问题解决后再批量修复。