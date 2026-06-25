package com.example.yin.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@TableName(value = "concert")
@Data
public class Concert {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String title;

    private Integer singerId;

    private String singerName;

    private String venue;

    private String coverPic;

    private Date showTime;

    private Date saleStartTime;

    private Date saleEndTime;

    private String introduction;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

}