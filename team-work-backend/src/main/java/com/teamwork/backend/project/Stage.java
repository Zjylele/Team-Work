package com.teamwork.backend.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("stage")
public class Stage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private Integer sortOrder;
}
