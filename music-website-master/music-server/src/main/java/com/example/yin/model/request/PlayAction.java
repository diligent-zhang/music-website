package com.example.yin.model.request;

import lombok.Data;

@Data
public class PlayAction {
    private String type;   // play / pause / resume / next / volume
    private Integer songId;
    private String name;
    private String singerName;
    private String url;
    private String pic;
    private String lyric;
    private Integer volume; // 仅 type=volume 时有值
}
