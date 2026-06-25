package com.example.yin.controller;

import com.example.yin.utils.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.InputStream;

@Controller
@RequestMapping("/audio")
public class AudioController {

    @Autowired
    private MinioService minioService;

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> playAudio(@PathVariable String fileName) {
        System.out.println("=== MinIO 音频请求 ===");
        System.out.println("请求文件名: " + fileName);

        try {
            String objectName = "song/" + fileName;
            InputStream stream = minioService.getObject(objectName);
            long size = minioService.getObjectSize(objectName);

            System.out.println("MinIO 文件读取成功，大小: " + size + " 字节");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, "audio/mpeg");
            headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(size));

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(size)
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(new InputStreamResource(stream));

        } catch (Exception e) {
            System.err.println("MinIO 音频读取失败: " + fileName);
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
    }
}
