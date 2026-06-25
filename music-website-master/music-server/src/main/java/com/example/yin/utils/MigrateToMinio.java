package com.example.yin.utils;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地文件迁移到 MinIO 工具
 * 独立运行: mvn exec:java -Dexec.mainClass="com.example.yin.utils.MigrateToMinio"
 * 或直接在 IDE 中运行 main 方法
 *
 * 使用前请确认 application-dev.properties 中 MinIO 配置正确
 */
public class MigrateToMinio {

    private static final String ENDPOINT = "http://localhost:9005";
    private static final String ACCESS_KEY = "root";
    private static final String SECRET_KEY = "12345678";
    private static final String BUCKET = "user01";

    private static final Map<String, String> DIR_MAPPING = new LinkedHashMap<>();

    static {
        DIR_MAPPING.put("song", "song");
        DIR_MAPPING.put("img/songPic", "img/songPic");
        DIR_MAPPING.put("img/singerPic", "img/singerPic");
        DIR_MAPPING.put("img/songListPic", "img/songListPic");
        DIR_MAPPING.put("img/avatorImages", "img/avatorImages");
        DIR_MAPPING.put("img/swiper", "img/swiper");
        DIR_MAPPING.put("img/concertPic", "img/concertPic");
    }

    public static void main(String[] args) throws Exception {
        String baseDir = System.getProperty("user.dir");
        System.out.println("工作目录: " + baseDir);
        System.out.println("MinIO 地址: " + ENDPOINT);
        System.out.println("目标 Bucket: " + BUCKET);
        System.out.println();

        MinioClient client = MinioClient.builder()
                .endpoint(ENDPOINT)
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();

        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
            System.out.println("Bucket 已创建: " + BUCKET);
        }

        int total = 0;
        int success = 0;
        int skipped = 0;

        for (Map.Entry<String, String> entry : DIR_MAPPING.entrySet()) {
            String localDir = entry.getKey();
            String minioPrefix = entry.getValue();
            Path dirPath = Paths.get(baseDir, localDir);

            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                System.out.println("[跳过] 目录不存在: " + localDir);
                continue;
            }

            File[] files = dirPath.toFile().listFiles();
            if (files == null || files.length == 0) {
                System.out.println("[空目录] " + localDir);
                continue;
            }

            System.out.println("--- 迁移目录: " + localDir + " (" + files.length + " 个文件) ---");
            for (File file : files) {
                if (!file.isFile()) continue;
                total++;
                String objectName = minioPrefix + "/" + file.getName();
                try {
                    String contentType = Files.probeContentType(file.toPath());
                    if (contentType == null) {
                        contentType = "application/octet-stream";
                    }
                    client.putObject(
                            PutObjectArgs.builder()
                                    .bucket(BUCKET)
                                    .object(objectName)
                                    .stream(new FileInputStream(file), file.length(), -1)
                                    .contentType(contentType)
                                    .build()
                    );
                    System.out.println("  [OK] " + file.getName() + " -> " + objectName);
                    success++;
                } catch (Exception e) {
                    System.out.println("  [FAIL] " + file.getName() + ": " + e.getMessage());
                    skipped++;
                }
            }
        }

        System.out.println();
        System.out.println("========== 迁移完成 ==========");
        System.out.println("总计: " + total + "  成功: " + success + "  失败: " + skipped);
    }
}
