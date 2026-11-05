package com.teamwork.backend.team;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team")
public class Team {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String inviteCode;
    private Long createdBy;
    private Integer singleRow;
    private LocalDateTime createdAt;
}
