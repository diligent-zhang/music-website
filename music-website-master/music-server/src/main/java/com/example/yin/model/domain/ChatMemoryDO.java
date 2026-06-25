package com.example.yin.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@TableName("chat_message")
@Data
public class ChatMemoryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String memoryId;
    private Integer messageIndex;
    private String messageType;
    private String content;
    private String toolName;
    private String toolExecutionId;
    private Date createTime;
}
