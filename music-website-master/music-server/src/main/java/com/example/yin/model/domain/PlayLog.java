package com.example.yin.model.domain;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;
@TableName(value = "play_log")
@Data
public class PlayLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer songId;

    private Integer userId;

    @TableField(fill = FieldFill.INSERT)
    private Date playTime;
}