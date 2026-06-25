package com.example.yin.util;

import com.example.yin.constant.Constants;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class AudioPathDiagnostic {
    public static void main(String[] args) {
        System.out.println("=== 音频路径诊断工具 ===\n");
        
        // 1. 检查常量配置
        System.out.println("1. 常量配置检查:");
        System.out.println("   ASSETS_PATH: " + Constants.ASSETS_PATH);
        System.out.println("   SONG_PATH: " + Constants.SONG_PATH);
        System.out.println();
        
        // 2. 检查song目录是否存在
        String songDirPath = Constants.SONG_PATH.replace("file:", "");
        File songDir = new File(songDirPath);
        System.out.println("2. 歌曲目录检查:");
        System.out.println("   目录路径: " + songDirPath);
        System.out.println("   目录存在: " + songDir.exists());
        if (songDir.exists()) {
            System.out.println("   目录可读: " + songDir.canRead());
            System.out.println("   文件数量: " + songDir.listFiles().length);
        }
        System.out.println();
        
        // 3. 检查数据库中的歌曲URL
        checkDatabaseSongs();
        
        // 4. 测试路径解析
        testPathResolution();
    }
    
    private static void checkDatabaseSongs() {
        System.out.println("3. 数据库歌曲检查:");
        String url = "jdbc:mysql://localhost:3306/tp_music?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String username = "root";
        String password = "root";
        
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, username, password);
            Statement stmt = conn.createStatement();
            
            ResultSet rs = stmt.executeQuery("SELECT id, name, url FROM song LIMIT 5");
            int count = 0;
            while (rs.next() && count < 3) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String dbUrl = rs.getString("url");
                
                System.out.println("   歌曲ID: " + id);
                System.out.println("   歌曲名: " + name);
                System.out.println("   数据库URL: " + dbUrl);
                
                // 测试文件是否存在
                if (dbUrl != null && !dbUrl.isEmpty()) {
                    String fileName = dbUrl.startsWith("/") ? dbUrl.substring(1) : dbUrl;
                    fileName = fileName.replace("song/", "");
                    Path filePath = Paths.get(Constants.SONG_PATH.replace("file:", ""), fileName);
                    boolean exists = Files.exists(filePath);
                    System.out.println("   文件路径: " + filePath);
                    System.out.println("   文件存在: " + exists);
                }
                System.out.println();
                count++;
            }
            
            conn.close();
        } catch (Exception e) {
            System.err.println("   数据库连接失败: " + e.getMessage());
        }
        System.out.println();
    }
    
    private static void testPathResolution() {
        System.out.println("4. 路径解析测试:");
        
        // 模拟几种可能的URL格式
        String[] testUrls = {
            "/song/test.mp3",
            "song/test.mp3", 
            "test.mp3",
            "/song/123e4567-e89b-12d3-a456-426614174000.mp3"
        };
        
        for (String url : testUrls) {
            System.out.println("   原始URL: " + url);
            String fileName = extractFileName(url);
            Path filePath = Paths.get(Constants.SONG_PATH.replace("file:", ""), fileName);
            System.out.println("   提取文件名: " + fileName);
            System.out.println("   完整路径: " + filePath);
            System.out.println("   路径存在: " + Files.exists(filePath));
            System.out.println();
        }
    }
    
    private static String extractFileName(String url) {
        if (url == null) return "";
        
        // 移除前导斜杠
        if (url.startsWith("/")) {
            url = url.substring(1);
        }
        
        // 如果包含路径分隔符，只取文件名部分
        if (url.contains("/")) {
            return url.substring(url.lastIndexOf("/") + 1);
        }
        
        return url;
    }
}