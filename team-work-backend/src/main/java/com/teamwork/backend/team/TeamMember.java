package com.teamwork.backend.team;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_member")
public class TeamMember {

    public static final String CREATOR = "CREATOR";
    public static final String MEMBER = "MEMBER";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private Long accountId;
    private String role;
    private LocalDateTime createdAt;
}
