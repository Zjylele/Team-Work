package com.teamwork.backend.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task")
public class TaskItem {

    public static final String TODO = "TODO";
    public static final String DOING = "DOING";
    public static final String DONE = "DONE";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long stageId;
    private String title;
    private String description;
    private Long assigneeId;
    private String status;
    private String priority;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.time.LocalDate dueDate;
    private LocalDateTime createdAt;

    public static boolean valid(String status) {
        return TODO.equals(status) || DOING.equals(status) || DONE.equals(status);
    }
}
