package com.teamwork.backend.task;

import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.project.ProjectMapper;
import com.teamwork.backend.project.ProjectService;
import com.teamwork.backend.team.TeamMember;
import com.teamwork.backend.team.TeamCalendar;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
class TaskWorkflow {
    static final String REVIEW = "REVIEW";
    private final JdbcTemplate db;
    private final ProjectService access;
    private final ProjectMapper projects;
    private final TeamCalendar calendar;

    TaskWorkflow(JdbcTemplate db, ProjectService access, ProjectMapper projects, TeamCalendar calendar) {
        this.db = db;
        this.access = access;
        this.projects = projects;
        this.calendar = calendar;
    }

    Config get(TeamMember actor, Long projectId) {
        access.requireAccessible(projectId, actor);
        return config(projectId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Config save(TeamMember actor, Long projectId, Config config) {
        access.requireAccessible(projectId, actor);
        projects.lock(projectId);
        var role = access.role(access.requireAccessible(projectId, actor), actor);
        if (!role.equals("OWNER") && !role.equals("ADMIN"))
            throw new ApiException(403, "只有项目所有者或管理员可以配置工作流");
        if (config.approverId() != null) access.requireAssignable(projectId, config.approverId(), actor.getTeamId());
        db.update("""
                INSERT INTO project_workflow(project_id, approver_id, assignment_reminder, status_reminder, due_reminder)
                VALUES (?, ?, ?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE approver_id=incoming.approver_id, assignment_reminder=incoming.assignment_reminder,
                    status_reminder=incoming.status_reminder, due_reminder=incoming.due_reminder
                """, projectId, config.approverId(), config.assignmentReminder(), config.statusReminder(), config.dueReminder());
        return config;
    }

    Config config(Long projectId) {
        return storedConfig(projectId).orElse(new Config(null, true, true, false));
    }

    java.util.Optional<Config> storedConfig(Long projectId) {
        return db.query("""
                SELECT approver_id, assignment_reminder, status_reminder, due_reminder
                FROM project_workflow WHERE project_id=?
                """, (rs, n) -> new Config(rs.getObject(1, Long.class), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4)),
                projectId).stream().findFirst();
    }

    void request(TaskItem task, TeamMember actor, Long approverId) {
        access.requireAssignable(task.getProjectId(), approverId, actor.getTeamId());
        db.update("INSERT INTO approval_instance(task_id, approver_id, requester_id, requested_at) VALUES (?, ?, ?, ?)",
                task.getId(), approverId, actor.getAccountId(), calendar.now());
        var approval = history(task.getId()).getFirst();
        // An approval request is actionable even if the optional status reminders are muted.
        event(task.getId(), approverId, "TASK_REVIEW_REQUESTED", approval.id().toString());
    }

    List<Approval> history(Long taskId) {
        return db.query("""
                SELECT a.id, a.approver_id, a.requester_id, a.requested_at, d.approved, d.comment, d.decided_at
                FROM approval_instance a LEFT JOIN approval_decision d ON d.approval_id=a.id
                WHERE a.task_id=? ORDER BY a.id DESC
                """, (rs, n) -> {
                    var decided = rs.getObject("decided_at", LocalDateTime.class);
                    return new Approval(rs.getLong("id"), rs.getLong("approver_id"), rs.getLong("requester_id"),
                            decided == null ? "PENDING" : rs.getBoolean("approved") ? "APPROVED" : "REJECTED",
                            rs.getObject("requested_at", LocalDateTime.class), decided, rs.getString("comment"));
                }, taskId);
    }

    void reassign(Long taskId, Long approvalId, Long approverId) {
        db.update("UPDATE approval_instance SET approver_id=? WHERE id=? AND task_id=?",
                approverId, approvalId, taskId);
        event(taskId, approverId, "TASK_REVIEW_REQUESTED", approvalId + ":" + approverId);
    }

    void decide(Long approvalId, boolean approved, String comment) {
        db.update("INSERT INTO approval_decision(approval_id, approved, comment, decided_at) VALUES (?, ?, ?, ?)",
                approvalId, approved, comment, calendar.now());
    }

    void event(Long task, Long recipient, String action, String key) {
        db.update("""
                INSERT INTO task_automation_event(task_id, recipient_id, action_type, dedup_key)
                VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE id=id
                """, task, recipient, action, key);
    }

    public record Config(Long approverId, boolean assignmentReminder, boolean statusReminder, boolean dueReminder) {}
    public record Approval(Long id, Long approverId, Long requesterId, String status,
                           LocalDateTime requestedAt, LocalDateTime decidedAt, String comment) {}
}
