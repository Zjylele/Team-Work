package com.teamwork.backend.project;

import lombok.Data;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface PortfolioMapper {
    record Portfolio(long id, String name) {}
    record Completion(long taskId, LocalDateTime completedAt) {}

    @Data
    class NewPortfolio {
        private Long id;
        private Long teamId;
        private String name;
    }

    @Select("SELECT id, name FROM portfolio WHERE team_id = #{teamId} ORDER BY id")
    List<Portfolio> list(long teamId);

    @Select("SELECT id, name FROM portfolio WHERE team_id = #{teamId} AND id = #{id}")
    Portfolio get(long teamId, long id);

    @Select("SELECT id FROM portfolio WHERE team_id = #{teamId} AND id = #{id} FOR UPDATE")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    Long lock(long teamId, long id);

    @Select("SELECT project_id FROM portfolio_project WHERE portfolio_id = #{id} ORDER BY project_id")
    List<Long> projectIds(long id);

    @Select("""
            <script>
            SELECT a.task_id, MAX(a.created_at) AS completed_at
            FROM task t
            JOIN task_activity a ON a.task_id = t.id
            WHERE t.project_id IN
                <foreach collection="projectIds" item="projectId" open="(" close=")" separator=",">#{projectId}</foreach>
                AND t.status = 'DONE'
                AND a.action = 'STATUS_CHANGED' AND a.after_value = 'DONE'
            GROUP BY a.task_id
            HAVING MAX(a.created_at) >= #{weekStart} AND MAX(a.created_at) &lt; #{weekEnd}
            </script>
            """)
    List<Completion> completions(List<Long> projectIds, LocalDateTime weekStart, LocalDateTime weekEnd);

    @Insert("INSERT INTO portfolio (team_id, name) VALUES (#{teamId}, #{name})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(NewPortfolio row);

    @Update("UPDATE portfolio SET name = #{name} WHERE id = #{id}")
    void rename(long id, String name);

    @Insert("INSERT INTO portfolio_project (portfolio_id, project_id) VALUES (#{id}, #{projectId})")
    void addProject(long id, long projectId);

    @Delete("DELETE FROM portfolio_project WHERE portfolio_id = #{id} AND project_id = #{projectId}")
    void removeProject(long id, long projectId);

    @Delete("DELETE FROM portfolio WHERE id = #{id} AND team_id = #{teamId}")
    void delete(long teamId, long id);
}
