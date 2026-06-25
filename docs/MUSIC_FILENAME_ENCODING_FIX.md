# 音乐文件名乱码修复方案

## 🔍 乱码问题分析
当前数据库中的音乐文件名存在编码问题：
- 错误显示: `A虂lvaro Soler-El Mismo Sol.mp3`
- 正确应该是: `Alvaro Soler-El Mismo Sol.mp3`
- 中文乱码: `Beyond-鍏夎緣宀佹湀.mp3` 应该是 `Beyond-光阴的故事.mp3`

## 🛠️ 修复方案

### 方案一：数据库字符集修复（推荐）

```sql
-- 1. 检查当前数据库字符集
SHOW VARIABLES LIKE 'character_set%';
SHOW VARIABLES LIKE 'collation%';

-- 2. 修改数据库字符集为utf8mb4
ALTER DATABASE tp_music CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 3. 修改song表字符集
ALTER TABLE song CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 4. 检查表字符集
SHOW FULL COLUMNS FROM song;
```

### 方案二：批量更新文件名

```sql
-- 手动修正已知的乱码文件名
UPDATE song SET 
    name = REPLACE(name, 'A虂lvaro', 'Alvaro'),
    url = REPLACE(url, 'A虂lvaro', 'Alvaro')
WHERE name LIKE '%A虂lvaro%';

UPDATE song SET 
    name = REPLACE(name, '鍏夎緣', '光阴'),
    url = REPLACE(url, '鍏夎緣', '光阴')
WHERE name LIKE '%鍏夎緣%';

-- 继续添加其他乱码修复规则
```

### 方案三：文件系统重命名 + 数据库同步

```bash
# 1. 重命名实际文件（使用正确编码）
ren "A虂lvaro Soler-El Mismo Sol.mp3" "Alvaro Soler-El Mismo Sol.mp3"
ren "Beyond-鍏夎緣宀佹湀.mp3" "Beyond-光阴的故事.mp3"

# 2. 同步更新数据库记录
UPDATE song SET 
    name = 'Alvaro Soler-El Mismo Sol.mp3',
    url = '/song/Alvaro Soler-El Mismo Sol.mp3'
WHERE url LIKE '%A虂lvaro%';

UPDATE song SET 
    name = 'Beyond-光阴的故事.mp3',
    url = '/song/Beyond-光阴的故事.mp3'
WHERE url LIKE '%鍏夎緣%';
```

### 方案四：Python脚本自动化修复

```python
# encoding_fix.py
import os
import mysql.connector

# 连接数据库
conn = mysql.connector.connect(
    host='localhost',
    user='root',
    password='root',
    database='tp_music'
)
cursor = conn.cursor()

# 歌曲目录
song_dir = r"C:\学习\简历项目\music-website-master 音乐\song"

# 乱码映射表
encoding_fix_map = {
    'A虂lvaro': 'Alvaro',
    '鍏夎緣宀佹湀': '光阴的故事',
    '鐪熺殑鐖变綘': '真的爱你',
    # 添加更多映射...
}

# 处理文件重命名和数据库更新
for old_name, new_name in encoding_fix_map.items():
    # 重命名文件
    old_path = os.path.join(song_dir, old_name + '.mp3')
    new_path = os.path.join(song_dir, new_name + '.mp3')
    
    if os.path.exists(old_path):
        os.rename(old_path, new_path)
        print(f"重命名: {old_name} -> {new_name}")
        
        # 更新数据库
        sql = "UPDATE song SET name = %s, url = %s WHERE name LIKE %s"
        cursor.execute(sql, (new_name + '.mp3', '/song/' + new_name + '.mp3', '%' + old_name + '%'))
        conn.commit()
        print(f"数据库更新完成: {old_name}")

cursor.close()
conn.close()
```

## 📋 修复验证步骤

1. **备份数据库**：
```sql
mysqldump -u root -p tp_music > tp_music_backup.sql
```

2. **执行修复方案**

3. **验证修复结果**：
```sql
-- 检查修复后的数据
SELECT id, name, url FROM song WHERE name LIKE '%Alvaro%' OR name LIKE '%光阴%';
```

4. **测试播放功能**

## ⚠️ 注意事项

- 执行前务必**备份数据库和文件**
- 建议先在测试环境验证
- 处理中文文件名时注意**编码一致性**
- 操作时**关闭音乐网站服务**避免冲突

推荐使用方案一（数据库字符集修复）作为根本解决方案。