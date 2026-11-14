package com.teamwork.backend.planning;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PlanningMapper {
    record Plan(Long id, String kind, String name, String description, LocalDate startDate, LocalDate endDate) {}

    @Select("""
        SELECT id, kind, name, description, start_date AS startDate, end_date AS endDate
        FROM project_plan WHERE project_id = #{projectId} ORDER BY id DESC
        """)
    List<Plan> list(long projectId);

    @Select("SELECT id FROM project_plan WHERE id = #{planId} AND project_id = #{projectId} FOR UPDATE")
    Long lock(long projectId, long planId);

    @Insert("""
        INSERT INTO project_plan(project_id, kind, name, description, start_date, end_date)
        VALUES(#{projectId}, #{form.kind}, #{form.name}, #{form.description}, #{form.startDate}, #{form.endDate})
        """)
    void create(long projectId, PlanningController.Form form);

    @Update("""
        UPDATE project_plan SET name = #{form.name}, description = #{form.description},
        start_date = #{form.startDate}, end_date = #{form.endDate}
        WHERE id = #{planId} AND project_id = #{projectId} AND kind = #{form.kind}
        """)
    int update(long projectId, long planId, PlanningController.Form form);

    @Delete("DELETE FROM project_plan WHERE id = #{planId} AND project_id = #{projectId}")
    void delete(long projectId, long planId);

    @Select("SELECT task_id FROM project_plan_task WHERE plan_id = #{planId} ORDER BY task_id")
    List<Long> tasks(long planId);

    @Delete("DELETE FROM project_plan_task WHERE plan_id = #{planId}")
    void clearTasks(long planId);

    @Insert("""
        INSERT INTO project_plan_task(plan_id, task_id)
        SELECT #{planId}, id FROM task WHERE id = #{taskId} AND project_id = #{projectId}
        """)
    int addTask(long projectId, long planId, long taskId);
}
