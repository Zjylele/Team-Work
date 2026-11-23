package com.teamwork.backend.project;

import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.common.CurrentAccount;
import com.teamwork.backend.task.TaskMapper;
import com.teamwork.backend.team.TeamMember;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
public class ProjectResourcesController {
    private final CurrentAccount account;
    private final ProjectService projects;
    private final TaskMapper tasks;
    private final JdbcTemplate jdbc;

    public ProjectResourcesController(CurrentAccount account, ProjectService projects, TaskMapper tasks, JdbcTemplate jdbc) {
        this.account = account;
        this.projects = projects;
        this.tasks = tasks;
        this.jdbc = jdbc;
    }

    public record Entry(long id, long accountId, LocalDate workDate, int minutes, String note) {}
    public record PlannedMinutes(Integer plannedMinutes) {}
    public record EntryRequest(LocalDate workDate, Integer minutes, String note) {}
    public record Capacity(long accountId, long taskCount, long loggedMinutes, long capacityMinutes, long plannedMinutes) {}
    public record Baseline(long id, String name, LocalDateTime createdAt) {}
    public record BaselineRequest(String name) {}
    public record Snapshot(long taskId, String title, LocalDate startDate, LocalDate dueDate) {}

    private long project(long taskId) {
        var task = tasks.selectById(taskId);
        if (task == null) throw new ApiException(404, "任务不存在");
        return task.getProjectId();
    }

    @GetMapping("/api/tasks/{taskId}/planned-minutes")
    public PlannedMinutes plannedMinutes(@PathVariable long taskId) {
        projects.requireAccessible(project(taskId), account.requireMember());
        return new PlannedMinutes(jdbc.queryForObject("SELECT planned_minutes FROM task WHERE id=?", Integer.class, taskId));
    }

    @PutMapping("/api/tasks/{taskId}/planned-minutes")
    public PlannedMinutes savePlannedMinutes(@PathVariable long taskId, @RequestBody PlannedMinutes request) {
        projects.requireWritable(project(taskId), account.requireMember());
        Integer minutes = request.plannedMinutes();
        if (minutes != null && (minutes < 0 || minutes > 525600))
            throw new ApiException(400, "计划工时必须是 0–525600 分钟");
        if (jdbc.update("UPDATE task SET planned_minutes=? WHERE id=?", minutes, taskId) == 0)
            throw new ApiException(404, "任务不存在");
        return request;
    }

    @GetMapping("/api/tasks/{taskId}/time-entries")
    public List<Entry> entries(@PathVariable long taskId) {
        projects.requireAccessible(project(taskId), account.requireMember());
        return jdbc.query("SELECT * FROM task_time_entry WHERE task_id=? ORDER BY work_date DESC,id DESC",
                (r,i) -> new Entry(r.getLong("id"), r.getLong("account_id"), r.getObject("work_date", LocalDate.class),
                        r.getInt("minutes"), r.getString("note")), taskId);
    }

    @PostMapping("/api/tasks/{taskId}/time-entries")
    @ResponseStatus(HttpStatus.CREATED)
    public Entry addEntry(@PathVariable long taskId, @RequestBody EntryRequest request) {
        TeamMember member = account.requireMember();
        projects.requireWritable(project(taskId), member);
        String note = validateEntry(request);
        long id = insert("INSERT INTO task_time_entry (task_id,account_id,work_date,minutes,note) VALUES (?,?,?,?,?)",
                taskId, member.getAccountId(), request.workDate(), request.minutes(), note);
        return new Entry(id, member.getAccountId(), request.workDate(), request.minutes(), note);
    }

    @PutMapping("/api/tasks/{taskId}/time-entries/{entryId}")
    public Entry editEntry(@PathVariable long taskId, @PathVariable long entryId, @RequestBody EntryRequest request) {
        TeamMember member = account.requireMember();
        projects.requireWritable(project(taskId), member);
        String note = validateEntry(request);
        if (jdbc.update("UPDATE task_time_entry SET work_date=?,minutes=?,note=? WHERE id=? AND task_id=? AND account_id=?",
                request.workDate(), request.minutes(), note, entryId, taskId, member.getAccountId()) == 0)
            throw new ApiException(404, "工时记录不存在");
        return new Entry(entryId, member.getAccountId(), request.workDate(), request.minutes(), note);
    }

    private String validateEntry(EntryRequest request) {
        String note = request.note() == null ? "" : request.note().trim();
        if (request.minutes() == null || request.minutes() < 1 || request.minutes() > 1440 ||
                request.workDate() == null || request.workDate().getYear() < 1000 ||
                request.workDate().isAfter(LocalDate.now()) || note.length() > 500) {
            throw new ApiException(400, "请输入有效工时、日期和备注");
        }
        return note;
    }

    @DeleteMapping("/api/tasks/{taskId}/time-entries/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@PathVariable long taskId, @PathVariable long entryId) {
        var member = account.requireMember();
        projects.requireWritable(project(taskId), member);
        if (jdbc.update("DELETE FROM task_time_entry WHERE id=? AND task_id=? AND account_id=?", entryId, taskId, member.getAccountId()) == 0) {
            throw new ApiException(404, "工时记录不存在");
        }
    }

    @GetMapping("/api/projects/{projectId}/capacity")
    public List<Capacity> capacity(@PathVariable long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var project = projects.requireAccessible(projectId, account.requireMember());
        long days = ChronoUnit.DAYS.between(from, to);
        if (days < 0 || days > 366 || from.getYear() < 1000 || to.getYear() > 9999)
            throw new ApiException(400, "容量查询区间无效或过长");
        long minutes = from.datesUntil(to.plusDays(1)).filter(d -> d.getDayOfWeek().compareTo(DayOfWeek.FRIDAY) <= 0).count() * 480;
        return jdbc.query("""
                SELECT tm.account_id, COALESCE(t.task_count,0), COALESCE(e.logged,0), COALESCE(t.planned,0)
                FROM team_member tm
                LEFT JOIN project_member pm ON pm.project_id=? AND pm.account_id=tm.account_id
                LEFT JOIN (SELECT assignee_id, COUNT(*) task_count, SUM(planned_minutes) planned FROM task WHERE project_id=?
                    AND (start_date IS NOT NULL OR due_date IS NOT NULL)
                    AND COALESCE(start_date,due_date)<=? AND COALESCE(due_date,start_date)>=?
                    GROUP BY assignee_id) t ON t.assignee_id=tm.account_id
                LEFT JOIN (SELECT x.account_id, SUM(x.minutes) logged FROM task_time_entry x JOIN task tx ON tx.id=x.task_id
                    WHERE tx.project_id=? AND x.work_date BETWEEN ? AND ? GROUP BY x.account_id) e ON e.account_id=tm.account_id
                WHERE tm.team_id=? AND ((?='SHARED' AND COALESCE(pm.role,'MEMBER')<>'VIEWER')
                    OR pm.role IN ('ADMIN','MEMBER') OR tm.account_id=? OR e.logged IS NOT NULL)
                ORDER BY tm.account_id
                """, (r,i) -> new Capacity(r.getLong(1), r.getLong(2), r.getLong(3), minutes, r.getLong(4)),
                projectId, projectId, to, from, projectId, from, to, project.getTeamId(), project.getVisibility(), project.getOwnerId());
    }

    @GetMapping("/api/projects/{projectId}/baselines")
    public List<Baseline> baselines(@PathVariable long projectId) {
        projects.requireAccessible(projectId, account.requireMember());
        return jdbc.query("SELECT id,name,created_at FROM schedule_baseline WHERE project_id=? ORDER BY id DESC",
                (r,i) -> new Baseline(r.getLong(1),r.getString(2),r.getObject(3, LocalDateTime.class)), projectId);
    }

    @PostMapping("/api/projects/{projectId}/baselines")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Baseline addBaseline(@PathVariable long projectId, @RequestBody BaselineRequest request) {
        var member = account.requireMember();
        var project = projects.requireAccessible(projectId, member);
        if (!List.of("OWNER", "ADMIN").contains(projects.role(project, member)))
            throw new ApiException(403, "仅项目管理员可保存基线");
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > 100) throw new ApiException(400, "请输入基线名称（1–100 字）");
        long id = insert("INSERT INTO schedule_baseline (project_id,name) VALUES (?,?)", projectId, name);
        jdbc.update("""
                INSERT INTO schedule_baseline_task (baseline_id,task_id,title,start_date,due_date)
                SELECT ?,id,title,start_date,due_date FROM task WHERE project_id=?
                """, id, projectId);
        return jdbc.queryForObject("SELECT created_at FROM schedule_baseline WHERE id=?",
                (r,i) -> new Baseline(id,name,r.getObject(1, LocalDateTime.class)), id);
    }

    @GetMapping("/api/projects/{projectId}/baselines/{baselineId}")
    public List<Snapshot> snapshot(@PathVariable long projectId, @PathVariable long baselineId) {
        projects.requireAccessible(projectId, account.requireMember());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM schedule_baseline WHERE project_id=? AND id=?",
                Integer.class, projectId, baselineId) == 0) throw new ApiException(404, "基线不存在");
        return jdbc.query("SELECT task_id,title,start_date,due_date FROM schedule_baseline_task WHERE baseline_id=? ORDER BY task_id",
                (r,i) -> new Snapshot(r.getLong(1), r.getString(2), r.getObject(3, LocalDate.class), r.getObject(4, LocalDate.class)),
                baselineId);
    }

    private long insert(String sql, Object... params) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var stmt = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i=0; i<params.length; i++) stmt.setObject(i+1, params[i]);
            return stmt;
        }, keys);
        return java.util.Objects.requireNonNull(keys.getKey()).longValue();
    }
}
