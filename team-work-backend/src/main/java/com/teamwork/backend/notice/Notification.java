package com.teamwork.backend.notice;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {

    public static final String TASK_ASSIGNED = "TASK_ASSIGNED";
    public static final String TASK_COMMENTED = "TASK_COMMENTED";
    public static final String TASK_STATUS_CHANGED = "TASK_STATUS_CHANGED";
    public static final String TASK_STARTED = "TASK_STARTED";
    public static final String TASK_COMPLETED = "TASK_COMPLETED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipientId;
    private Long taskId;
    private String type;
    private Integer readFlag;
    private LocalDateTime createdAt;
}
