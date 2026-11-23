package com.teamwork.backend.planning;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TaskDependencyMapper {
    record Dependency(Long predecessorTaskId, Long successorTaskId) {}

    @Select("""
            SELECT d.predecessor_task_id, d.successor_task_id
            FROM task_dependency d
            JOIN task predecessor ON predecessor.id = d.predecessor_task_id
            WHERE predecessor.project_id = #{projectId}
            ORDER BY d.predecessor_task_id, d.successor_task_id
            """)
    List<Dependency> list(long projectId);

    @Insert("""
            INSERT INTO task_dependency (predecessor_task_id, successor_task_id)
            VALUES (#{predecessorTaskId}, #{successorTaskId})
            """)
    void insert(Dependency dependency);

    @Delete("""
            DELETE FROM task_dependency
            WHERE predecessor_task_id = #{predecessorTaskId} AND successor_task_id = #{successorTaskId}
            """)
    void delete(Dependency dependency);

    @Select("""
            SELECT COUNT(*) FROM task_dependency d
            JOIN task predecessor ON predecessor.id = d.predecessor_task_id
            WHERE d.successor_task_id = #{taskId} AND predecessor.status <> 'DONE'
            """)
    long incompletePredecessors(long taskId);
}
