package com.example.yin.controller;

import com.example.yin.utils.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Controller
public class MinioController {

    @Autowired
    private MinioService minioService;

    @GetMapping("/test/path")
    @ResponseBody
    public String testPath() {
        return "存储模式: MinIO 对象存储";
    }

    // 获取歌曲 - 从 MinIO 读取
    @GetMapping("/user01/{fileName:.+}")
    public ResponseEntity<byte[]> getMusic(@PathVariable String fileName) {
        try {
            String objectName = "song/" + fileName;
            InputStream stream = minioService.getObject(objectName);
            byte[] bytes = readAllBytes(stream);
            long size = minioService.getObjectSize(objectName);

            HttpHeaders headers = new HttpHeaders();
            String fileExtension = getFileExtension(fileName).toLowerCase();
            switch (fileExtension) {
                case "mp3":
                    headers.setContentType(MediaType.valueOf("audio/mpeg"));
                    break;
                case "wav":
                    headers.setContentType(MediaType.valueOf("audio/wav"));
                    break;
                case "flac":
                    headers.setContentType(MediaType.valueOf("audio/flac"));
                    break;
                case "m4a":
                    headers.setContentType(MediaType.valueOf("audio/mp4"));
                    break;
                default:
                    headers.setContentType(MediaType.valueOf("audio/ogg"));
                    break;
            }
            headers.setContentDispositionFormData("inline", fileName);
            headers.setAccessControlAllowOrigin("*");
            headers.setCacheControl("max-age=86400");

            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    // 获取歌手图片 - 从 MinIO 读取
    @GetMapping("/user01/singer/img/{fileName:.+}")
    public ResponseEntity<byte[]> getImage(@PathVariable String fileName) {
        return serveImage("img/singerPic/" + fileName);
    }

    // 获取歌单图片 - 从 MinIO 读取
    @GetMapping("/user01/songlist/{fileName:.+}")
    public ResponseEntity<byte[]> getImage1(@PathVariable String fileName) {
        return serveImage("img/songListPic/" + fileName);
    }

    // 获取歌曲图片 - 从 MinIO 读取
    @GetMapping("/user01/singer/song/{fileName:.+}")
    public ResponseEntity<byte[]> getImage2(@PathVariable String fileName) {
        return serveImage("img/songPic/" + fileName);
    }

    // 获取头像图片 - 从 MinIO 读取
    @GetMapping("/img/avatorImages/{fileName:.+}")
    public ResponseEntity<byte[]> getImage3(@PathVariable String fileName) {
        return serveImage("img/avatorImages/" + fileName);
    }

    // 获取歌曲图片 - 从 MinIO 读取（主入口，前端直接调用）
    @GetMapping("/img/songPic/{fileName:.+}")
    public ResponseEntity<byte[]> getSongPic(@PathVariable String fileName) {
        return serveImage("img/songPic/" + fileName);
    }

    // 获取歌手图片 - 从 MinIO 读取（主入口）
    @GetMapping("/img/singerPic/{fileName:.+}")
    public ResponseEntity<byte[]> getSingerPic(@PathVariable String fileName) {
        return serveImage("img/singerPic/" + fileName);
    }

    // 获取歌单图片 - 从 MinIO 读取（主入口）
    @GetMapping("/img/songListPic/{fileName:.+}")
    public ResponseEntity<byte[]> getSongListPic(@PathVariable String fileName) {
        return serveImage("img/songListPic/" + fileName);
    }

    // 获取轮播图 - 从 MinIO 读取
    @GetMapping("/img/swiper/{fileName:.+}")
    public ResponseEntity<byte[]> getSwiperPic(@PathVariable String fileName) {
        return serveImage("img/swiper/" + fileName);
    }

    // 获取演唱会图片 - 从 MinIO 读取
    @GetMapping("/img/concertPic/{fileName:.+}")
    public ResponseEntity<byte[]> getConcertPic(@PathVariable String fileName) {
        return serveImage("img/concertPic/" + fileName);
    }

    private ResponseEntity<byte[]> serveImage(String objectName) {
        try {
            InputStream stream = minioService.getObject(objectName);
            byte[] bytes = readAllBytes(stream);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    // Java 8 兼容的 readAllBytes
    private byte[] readAllBytes(InputStream stream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = stream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }
}
