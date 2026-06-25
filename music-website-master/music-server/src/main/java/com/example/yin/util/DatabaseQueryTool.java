package com.example.yin.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseQueryTool {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/tp_music?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String username = "root";
        String password = "root";
        
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, username, password);
            Statement stmt = conn.createStatement();
            
            // Query song information
            ResultSet rs = stmt.executeQuery("SELECT id, name, url FROM song LIMIT 5");
            System.out.println("=== Song URL Format Check ===");
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("id"));
                System.out.println("Name: " + rs.getString("name"));
                System.out.println("URL: " + rs.getString("url"));
                System.out.println("---");
            }
            
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}