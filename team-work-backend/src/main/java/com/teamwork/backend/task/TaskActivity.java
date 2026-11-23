package com.teamwork.backend.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("task_activity")
public class TaskActivity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long taskId;
    private Long actorId;
    private String action;
    private String beforeValue;
    private String afterValue;
    private LocalDateTime createdAt;
}
