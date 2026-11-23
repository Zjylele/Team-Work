package com.teamwork.backend.project;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import java.util.List;

@Mapper
public interface ProjectMemberMapper {
    @Select("SELECT role FROM project_member WHERE project_id=#{projectId} AND account_id=#{accountId}")
    String role(long projectId, long accountId);
    @Insert("INSERT INTO project_member(project_id,account_id,role) VALUES(#{projectId},#{accountId},#{role}) ON DUPLICATE KEY UPDATE role=VALUES(role)")
    void put(long projectId, long accountId, String role);
    @Delete("DELETE FROM project_member WHERE project_id=#{projectId} AND account_id=#{accountId}")
    void remove(long projectId, long accountId);
    record Member(long accountId, String displayName, String role) {}
    @Select("SELECT pm.account_id AS accountId,a.display_name AS displayName,pm.role FROM project_member pm JOIN account a ON a.id=pm.account_id WHERE pm.project_id=#{projectId} ORDER BY a.display_name")
    List<Member> list(long projectId);
}
