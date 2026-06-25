package com.example.yin.utils;

import com.example.yin.constant.Constants;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class FileUtils {

    /**
     * 上传文件到 MinIO 对象存储
     * @param file 上传的文件
     * @param subDir 子目录（如 song, img/songPic）
     * @return 文件存储路径（如 /song/uuid.mp3），与本地存储格式一致
     */
    public static String saveToMinio(MultipartFile file, String subDir) throws IOException {
        MinioService minioService = SpringContextHolder.getBean(MinioService.class);
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String fileName = UUID.randomUUID().toString() + "." + extension;
        String objectName = subDir + "/" + fileName;
        try {
            minioService.upload(objectName, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (Exception e) {
            throw new IOException("MinIO 上传失败: " + e.getMessage(), e);
        }
        String relativePath = "/" + objectName;
        System.out.println("MinIO 上传成功: " + relativePath);
        return relativePath;
    }

    /**
     * 从 MinIO 删除文件
     * @param filePath 文件路径（如 /song/uuid.mp3）
     */
    public static boolean deleteFromMinio(String filePath) {
        try {
            MinioService minioService = SpringContextHolder.getBean(MinioService.class);
            String objectName = filePath.startsWith("/") ? filePath.substring(1) : filePath;
            minioService.delete(objectName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Deprecated
    public static String saveLocally(MultipartFile file, String subDir) throws IOException {
        String baseDir = Constants.ASSETS_PATH;
        Path targetDir = Paths.get(baseDir, subDir);
        Files.createDirectories(targetDir);
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String fileName = UUID.randomUUID().toString() + "." + extension;
        Path filePath = targetDir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String relativePath = "/" + subDir + "/" + fileName;
        System.out.println("保存文件路径: " + relativePath + " (完整路径: " + filePath.toString() + ")");
        return relativePath;
    }

    private static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "jpg";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    @Deprecated
    public static boolean deleteFile(String filePath) {
        try {
            String baseDir = Constants.ASSETS_PATH;
            Path fullPath = Paths.get(baseDir + filePath);
            return Files.deleteIfExists(fullPath);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
