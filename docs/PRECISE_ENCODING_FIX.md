# 精确音乐文件名乱码修复脚本

## 🎯 当前乱码文件清单
通过扫描发现以下乱码文件名：

```
当前乱码文件:
- A虂lvaro Soler-El Mismo Sol.mp3
- A虂lvaro Soler-Sofi虂a.mp3  
- Beyond-鍏夎緣宀佹湀.mp3
- Beyond-鐪熺殑鐖变綘.mp3

应该修正为:
- Alvaro Soler-El Mismo Sol.mp3
- Alvaro Soler-Sofia.mp3
- Beyond-光阴的故事.mp3
- Beyond-真的爱你.mp3
```

## 🛠️ 精确修复命令

### 方案一：PowerShell脚本修复

```powershell
# encoding_fix.ps1
# 设置UTF-8编码
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 歌曲目录
$songDir = "C:\学习\简历项目\music-website-master 音乐\song"

# 乱码修复映射表
$fixMap = @{
    "A虂lvaro Soler-El Mismo Sol.mp3" = "Alvaro Soler-El Mismo Sol.mp3"
    "A虂lvaro Soler-Sofi虂a.mp3" = "Alvaro Soler-Sofia.mp3"
    "Beyond-鍏夎緣宀佹湀.mp3" = "Beyond-光阴的故事.mp3"
    "Beyond-鐪熺殑鐖变綘.mp3" = "Beyond-真的爱你.mp3"
}

# 执行重命名
foreach ($oldName in $fixMap.Keys) {
    $oldPath = Join-Path $songDir $oldName
    $newPath = Join-Path $songDir $fixMap[$oldName]
    
    if (Test-Path $oldPath) {
        Rename-Item -Path $oldPath -NewName $fixMap[$oldName]
        Write-Host "✅ 已修复: $oldName -> $($fixMap[$oldName])"
    } else {
        Write-Host "❌ 文件不存在: $oldName"
    }
}
```

### 方案二：数据库同步更新SQL

```sql
-- 修复数据库中的对应记录
UPDATE song SET 
    name = 'Alvaro Soler-El Mismo Sol.mp3',
    url = '/song/Alvaro Soler-El Mismo Sol.mp3'
WHERE url = '/song/A虂lvaro Soler-El Mismo Sol.mp3';

UPDATE song SET 
    name = 'Alvaro Soler-Sofia.mp3',
    url = '/song/Alvaro Soler-Sofia.mp3'
WHERE url = '/song/A虂lvaro Soler-Sofi虂a.mp3';

UPDATE song SET 
    name = 'Beyond-光阴的故事.mp3',
    url = '/song/Beyond-光阴的故事.mp3'
WHERE url = '/song/Beyond-鍏夎緣宀佹湀.mp3';

UPDATE song SET 
    name = 'Beyond-真的爱你.mp3',
    url = '/song/Beyond-真的爱你.mp3'
WHERE url = '/song/Beyond-鐪熺殑鐖变綘.mp3';
```

### 方案三：批量自动化修复

```sql
-- 批量更新所有乱码
UPDATE song SET 
    name = CASE 
        WHEN name LIKE '%A虂lvaro%' THEN REPLACE(name, 'A虂lvaro', 'Alvaro')
        WHEN name LIKE '%鍏夎緣%' THEN REPLACE(name, '鍏夎緣宀佹湀', '光阴的故事')
        WHEN name LIKE '%鐪熺殑鐖变綘%' THEN REPLACE(name, '鐪熺殑鐖变綘', '真的爱你')
        ELSE name
    END,
    url = CASE 
        WHEN url LIKE '%A虂lvaro%' THEN REPLACE(url, 'A虂lvaro', 'Alvaro')
        WHEN url LIKE '%鍏夎緣%' THEN REPLACE(url, '鍏夎緣宀佹湀', '光阴的故事')
        WHEN url LIKE '%鐪熺殑鐖变綘%' THEN REPLACE(url, '鐪熺殑鐖变綘', '真的爱你')
        ELSE url
    END
WHERE name LIKE '%A虂lvaro%' 
   OR name LIKE '%鍏夎緣%' 
   OR name LIKE '%鐪熺殑鐖变綘%';
```

## 📋 执行步骤

1. **备份重要文件**：
```bash
# 备份song目录
xcopy "C:\学习\简历项目\music-website-master 音乐\song" "C:\backup\song_backup" /E /I
```

2. **执行文件重命名**：
```powershell
# 运行PowerShell脚本
powershell -ExecutionPolicy Bypass -File "encoding_fix.ps1"
```

3. **更新数据库**：
```sql
-- 执行上面的SQL更新语句
```

4. **验证修复结果**：
```sql
-- 检查修复后的文件
SELECT id, name, url FROM song WHERE name LIKE '%Alvaro%' OR name LIKE '%光阴%';
```

## ⚠️ 重要提醒

- ⚠️ **执行前务必备份**数据库和文件系统
- ⚠️ **建议在测试环境**先验证修复效果
- ⚠️ **确保音乐网站服务**在修复过程中处于关闭状态
- ⚠️ **注意文件权限**，确保有重命名权限

执行完成后，重新启动音乐网站服务，乱码问题应该就能完全解决了！