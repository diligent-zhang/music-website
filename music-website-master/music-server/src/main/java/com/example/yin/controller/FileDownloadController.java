package com.example.yin.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.util.ListUtils;
import com.example.yin.constant.Constants;
import com.example.yin.model.domain.SongList;
import com.example.yin.service.SongListService;
import com.example.yin.utils.TestFileUtil;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Controller
@RequestMapping("/download")
public class FileDownloadController {

    @GetMapping("/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request) {
        try {
            // 构建本地文件路径
            Path filePath = Paths.get(Constants.SONG_PATH.replace("file:", ""), fileName);
            
            // 检查文件是否存在
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            
            // 读取文件内容
            byte[] musicBytes = Files.readAllBytes(filePath);
            
            // 创建一个ByteArrayResource对象，用于包装字节数组
            ByteArrayResource resource = new ByteArrayResource(musicBytes);
            
            // 构建响应头
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
            
            // 返回一个 ResponseEntity 对象
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(musicBytes.length)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
