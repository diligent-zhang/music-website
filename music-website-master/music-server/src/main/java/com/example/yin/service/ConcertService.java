package com.example.yin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.yin.common.R;
import com.example.yin.model.domain.Concert;
import com.example.yin.model.request.ConcertRequest;
import org.springframework.web.multipart.MultipartFile;

public interface ConcertService extends IService<Concert> {

    R listConcerts(Integer page, Integer size, Integer status,boolean excludeExpired);
    R getDetail(Integer concertId);
    R addConcert(ConcertRequest request);
    R updateConcert(ConcertRequest request);
    R updateStatus(Integer concertId, Integer status);
    R uploadCover(MultipartFile file, Integer concertId);
}
