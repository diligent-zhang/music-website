package com.example.yin.controller;

import com.example.yin.utils.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class MinioUploadController {

    /**
     * 上传普通文件到本地存储
     */
    public static String uploadFile(MultipartFile file) {
        try {
            String filePath = FileUtils.saveLocally(file, "uploads");
            return "File uploaded successfully! Path: " + filePath;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error uploading file: " + e.getMessage();
        }
    }

    /**
     * 上传歌手图片到本地存储
     */
    public static String uploadImgFile(MultipartFile file) {
        try {
            String filePath = FileUtils.saveLocally(file, "img/singerPic");
            return "File uploaded successfully! Path: " + filePath;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error uploading file: " + e.getMessage();
        }
    }

    /**
     * 上传歌单图片到本地存储
     */
    public static String uploadSonglistImgFile(MultipartFile file) {
        try {
            String filePath = FileUtils.saveLocally(file, "img/songListPic");
            return "File uploaded successfully! Path: " + filePath;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error uploading file: " + e.getMessage();
        }
    }

    /**
     * 上传歌曲图片到本地存储
     */
    public static String uploadSongImgFile(MultipartFile file) {
        try {
            String filePath = FileUtils.saveLocally(file, "img/songPic");
            return "File uploaded successfully! Path: " + filePath;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error uploading file: " + e.getMessage();
        }
    }

    /**
     * 上传头像图片到本地存储
     */
    public static String uploadAtorImgFile(MultipartFile file) {
        try {
            String filePath = FileUtils.saveLocally(file, "img/avatorImages");
            return "File uploaded successfully! Path: " + filePath;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error uploading file: " + e.getMessage();
        }
    }
}
