package com.teamwork.backend.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_comment")
public class TaskComment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long authorId;
    private String content;
    private LocalDateTime createdAt;
}
