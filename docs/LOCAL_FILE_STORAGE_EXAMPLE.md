# 本地文件存储 vs MinIO 存储对比

## 本地文件存储实现步骤

### 1. 文件上传实现（本地存储）

```java
@Service
public class LocalFileStorageService {
    
    @Value("${file.storage.path:C:\\music-website\\files\\}") // 默认本地存储路径
    private String storagePath;
    
    public String uploadFile(MultipartFile file, String subDir) throws IOException {
        // 确保目录存在
        Path uploadPath = Paths.get(storagePath, subDir);
        Files.createDirectories(uploadPath);
        
        // 生成唯一文件名
        String originalFileName = file.getOriginalFilename();
        String extension = getFileExtension(originalFileName);
        String fileName = UUID.randomUUID().toString() + "." + extension;
        
        // 保存文件
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        // 返回相对路径
        return "/" + subDir + "/" + fileName;
    }
    
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}
```

### 2. 文件读取实现（本地存储）

```java
@Controller
public class LocalFileController {
    
    @Value("${file.storage.path:C:\\music-website\\files\\}")
    private String storagePath;
    
    @GetMapping("/images/{subDir}/{fileName:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveImage(@PathVariable String subDir, 
                                             @PathVariable String fileName) {
        try {
            Path filePath = Paths.get(storagePath, subDir, fileName);
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists()) {
                String contentType = determineContentType(fileName);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    private String determineContentType(String fileName) {
        String extension = fileName.toLowerCase().substring(fileName.lastIndexOf(".") + 1);
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            default:
                return "application/octet-stream";
        }
    }
}
```

### 3. 与MinIO实现的主要区别

| 特性 | 本地文件存储 | MinIO存储 |
|------|-------------|-----------|
| 文件存储位置 | 服务器本地磁盘 | 分布式对象存储 |
| 可扩展性 | 有限（受单机磁盘限制） | 高（可水平扩展） |
| 可靠性 | 受单点故障影响 | 高（多副本机制） |
| 并发处理能力 | 受限于本地I/O | 高并发处理 |
| 成本 | 低（仅服务器成本） | 中等（服务器+MinIO服务） |
| 实现复杂度 | 相对简单 | 需要额外配置 |

### 4. 本地文件存储的配置

```properties
# application.properties
file.storage.path=/var/www/music-website/files/
file.max.size=10MB
file.allowed.types=image/jpeg,image/png,image/gif,audio/mp3
```

### 5. 数据库中的文件路径存储

在本地存储方案中，数据库中存储的路径将是：
- 本地存储路径：`/songs/abc123-def456-ghi789.jpg`
- 对应的URL：`http://yourdomain.com/images/songs/abc123-def456-ghi789.jpg`

### 6. 安全性考虑

```java
@Component
public class FileSecurityValidator {
    
    private final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "mp3");
    
    public boolean isValidFile(MultipartFile file) {
        String extension = getFileExtension(file.getOriginalFilename());
        return ALLOWED_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    public boolean isSafeFilePath(String filePath) {
        // 防止路径遍历攻击
        Path path = Paths.get(filePath).normalize();
        return !path.toString().contains("..");
    }
}
```

## 总结

本地文件存储相比MinIO更简单直接，但缺乏分布式存储的优势。在小型项目中可能更易实现，但在高并发或大规模应用中，MinIO等对象存储服务更具优势。