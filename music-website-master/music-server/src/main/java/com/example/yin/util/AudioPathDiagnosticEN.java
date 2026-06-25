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

public class AudioPathDiagnosticEN {
    public static void main(String[] args) {
        System.out.println("=== Audio Path Diagnostic Tool ===\n");
        
        // 1. Check constant configuration
        System.out.println("1. Constant Configuration Check:");
        System.out.println("   ASSETS_PATH: " + Constants.ASSETS_PATH);
        System.out.println("   SONG_PATH: " + Constants.SONG_PATH);
        System.out.println();
        
        // 2. Check song directory existence
        String songDirPath = Constants.SONG_PATH.replace("file:", "");
        File songDir = new File(songDirPath);
        System.out.println("2. Song Directory Check:");
        System.out.println("   Directory Path: " + songDirPath);
        System.out.println("   Directory Exists: " + songDir.exists());
        if (songDir.exists()) {
            System.out.println("   Directory Readable: " + songDir.canRead());
            System.out.println("   File Count: " + songDir.listFiles().length);
        }
        System.out.println();
        
        // 3. Check database songs
        checkDatabaseSongs();
        
        // 4. Test path resolution
        testPathResolution();
    }
    
    private static void checkDatabaseSongs() {
        System.out.println("3. Database Songs Check:");
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
                
                System.out.println("   Song ID: " + id);
                System.out.println("   Song Name: " + name);
                System.out.println("   Database URL: " + dbUrl);
                
                // Test if file exists
                if (dbUrl != null && !dbUrl.isEmpty()) {
                    String fileName = dbUrl.startsWith("/") ? dbUrl.substring(1) : dbUrl;
                    fileName = fileName.replace("song/", "");
                    Path filePath = Paths.get(Constants.SONG_PATH.replace("file:", ""), fileName);
                    boolean exists = Files.exists(filePath);
                    System.out.println("   File Path: " + filePath);
                    System.out.println("   File Exists: " + exists);
                }
                System.out.println();
                count++;
            }
            
            conn.close();
        } catch (Exception e) {
            System.err.println("   Database connection failed: " + e.getMessage());
        }
        System.out.println();
    }
    
    private static void testPathResolution() {
        System.out.println("4. Path Resolution Test:");
        
        // Test several possible URL formats
        String[] testUrls = {
            "/song/test.mp3",
            "song/test.mp3", 
            "test.mp3",
            "/song/123e4567-e89b-12d3-a456-426614174000.mp3"
        };
        
        for (String url : testUrls) {
            System.out.println("   Original URL: " + url);
            String fileName = extractFileName(url);
            Path filePath = Paths.get(Constants.SONG_PATH.replace("file:", ""), fileName);
            System.out.println("   Extracted Filename: " + fileName);
            System.out.println("   Full Path: " + filePath);
            System.out.println("   Path Exists: " + Files.exists(filePath));
            System.out.println();
        }
    }
    
    private static String extractFileName(String url) {
        if (url == null) return "";
        
        // Remove leading slash
        if (url.startsWith("/")) {
            url = url.substring(1);
        }
        
        // If contains path separator, only take filename part
        if (url.contains("/")) {
            return url.substring(url.lastIndexOf("/") + 1);
        }
        
        return url;
    }
}