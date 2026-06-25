package com.example.yin.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@TableName(value = "rank_snapshot")
@Data
public class RankSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String snapshotDate;

    private String periodType;

    private Integer songId;

    private Integer rankPosition;

    private Integer playCount;
}
