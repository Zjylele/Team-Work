package com.teamwork.backend.project;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface ProjectMapper extends BaseMapper<Project> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM project WHERE id = #{projectId} FOR UPDATE")
    @org.apache.ibatis.annotations.Options(flushCache = org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE, useCache = false)
    Long lock(long projectId);
}
