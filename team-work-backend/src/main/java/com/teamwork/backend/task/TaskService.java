package com.teamwork.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamwork.backend.account.Account;
import com.teamwork.backend.account.AccountMapper;
import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.notice.Notification;
import com.teamwork.backend.notice.NotificationService;
import com.teamwork.backend.project.Project;
import com.teamwork.backend.project.ProjectMapper;
import com.teamwork.backend.project.ProjectService;
import com.teamwork.backend.project.Stage;
import com.teamwork.backend.team.Team;
import com.teamwork.backend.team.TeamMember;
import com.teamwork.backend.team.TeamMemberMapper;
import com.teamwork.backend.team.TeamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskMapper tasks;
    private final TaskCommentMapper comments;
    private final AccountMapper accounts;
    private final TeamMemberMapper members;
    private final ProjectMapper projects;
    private final ProjectService projectService;
    private final TeamService teamService;
    private final NotificationService notificationService;

    public TaskService(TaskMapper tasks, TaskCommentMapper comments, AccountMapper accounts,
                       TeamMemberMapper members, ProjectMapper projects, ProjectService projectService,
                       TeamService teamService, NotificationService notificationService) {
        this.tasks = tasks;
        this.comments = comments;
        this.accounts = accounts;
        this.members = members;
        this.projects = projects;
        this.projectService = projectService;
        this.teamService = teamService;
        this.notificationService = notificationService;
    }

    public List<TaskView> list(TeamMember member, Long projectId) {
        Project project = projectService.requireInTeam(projectId, member.getTeamId());
        List<TaskItem> rows = tasks.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getProjectId, project.getId())
                .orderByAsc(TaskItem::getId));
        return toViews(rows);
    }

    public TaskView get(TeamMember member, Long taskId) {
        return toView(requireTask(member, taskId));
    }

    @Transactional
    public TaskView create(TeamMember creator, Long projectId, String title, String description,
                           Long assigneeId, Long stageId) {
        Project project = projectService.requireInTeam(projectId, creator.getTeamId());
        Stage stage = projectService.requireStage(creator, stageId);
        if (!stage.getProjectId().equals(project.getId())) {
            throw new ApiException(400, "阶段不属于该项目");
        }
        requireAssignee(creator.getTeamId(), assigneeId);
        TaskItem task = new TaskItem();
        task.setProjectId(project.getId());
        task.setStageId(stage.getId());
        task.setTitle(title);
        task.setDescription(description == null ? "" : description);
        task.setAssigneeId(assigneeId);
        task.setStatus(TaskItem.TODO);
        task.setCreatedAt(LocalDateTime.now());
        tasks.insert(task);
        notificationService.notify(assigneeId, creator.getAccountId(), task.getId(), Notification.TASK_ASSIGNED);
        return toView(task);
    }

    @Transactional
    public TaskView update(TeamMember actor, Long taskId, String title, String description,
                           Long assigneeId, Long stageId) {
        TaskItem task = requireTask(actor, taskId);
        if (!TeamMember.CREATOR.equals(actor.getRole())) {
            if (!task.getAssigneeId().equals(actor.getAccountId())) {
                throw new ApiException(403, "只能补充自己负责任务的说明");
            }
            if (title != null || assigneeId != null || stageId != null) {
                throw new ApiException(403, "成员不能修改标题、负责人或阶段");
            }
            if (description == null) {
                throw new ApiException(400, "请填写说明");
            }
            if (description.length() > 2000) {
                throw new ApiException(400, "说明最长 2000 个字符");
            }
            task.setDescription(description);
            tasks.updateById(task);
            return toView(task);
        }
        if (title != null) {
            if (title.isBlank() || title.length() > 128) {
                throw new ApiException(400, "标题最长 128 个字符");
            }
            task.setTitle(title);
        }
        if (description != null) {
            if (description.length() > 2000) {
                throw new ApiException(400, "说明最长 2000 个字符");
            }
            task.setDescription(description);
        }
        if (stageId != null) {
            Stage stage = projectService.requireStage(actor, stageId);
            if (!stage.getProjectId().equals(task.getProjectId())) {
                throw new ApiException(400, "阶段不属于该项目");
            }
            task.setStageId(stage.getId());
        }
        Long previousAssignee = task.getAssigneeId();
        if (assigneeId != null) {
            requireAssignee(actor.getTeamId(), assigneeId);
            task.setAssigneeId(assigneeId);
        }
        tasks.updateById(task);
        if (assigneeId != null && !assigneeId.equals(previousAssignee)) {
            notificationService.notify(assigneeId, actor.getAccountId(), task.getId(), Notification.TASK_ASSIGNED);
        }
        return toView(task);
    }

    @Transactional
    public TaskView start(TeamMember member, Long taskId) {
        TaskItem task = requireOwnTask(member, taskId);
        if (!TaskItem.TODO.equals(task.getStatus())) {
            throw new ApiException(409, "只有待开始的任务可以开始");
        }
        task.setStatus(TaskItem.DOING);
        tasks.updateById(task);
        notifyCreator(member, task, Notification.TASK_STARTED);
        return toView(task);
    }

    @Transactional
    public TaskView complete(TeamMember member, Long taskId) {
        TaskItem task = requireOwnTask(member, taskId);
        if (!TaskItem.DOING.equals(task.getStatus())) {
            throw new ApiException(409, "只有进行中的任务可以结束");
        }
        task.setStatus(TaskItem.DONE);
        tasks.updateById(task);
        notifyCreator(member, task, Notification.TASK_COMPLETED);
        return toView(task);
    }

    @Transactional
    public TaskView correct(TeamMember creator, Long taskId, String status) {
        if (!TaskItem.valid(status)) {
            throw new ApiException(400, "状态只能是待开始、进行中或已完成");
        }
        TaskItem task = requireTask(creator, taskId);
        if (!status.equals(task.getStatus())) {
            task.setStatus(status);
            tasks.updateById(task);
            notificationService.notify(task.getAssigneeId(), creator.getAccountId(), task.getId(),
                    Notification.TASK_STATUS_CHANGED);
        }
        return toView(task);
    }

    public List<CommentView> comments(TeamMember member, Long taskId) {
        requireTask(member, taskId);
        List<TaskComment> rows = comments.selectList(new LambdaQueryWrapper<TaskComment>()
                .eq(TaskComment::getTaskId, taskId)
                .orderByAsc(TaskComment::getId));
        Map<Long, Account> authors = loadAccounts(rows.stream().map(TaskComment::getAuthorId).collect(Collectors.toSet()));
        return rows.stream().map(row -> CommentView.from(row, authors.get(row.getAuthorId()))).toList();
    }

    @Transactional
    public CommentView comment(TeamMember member, Long taskId, String content) {
        TaskItem task = requireTask(member, taskId);
        TaskComment comment = new TaskComment();
        comment.setTaskId(task.getId());
        comment.setAuthorId(member.getAccountId());
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now());
        comments.insert(comment);
        Set<Long> recipients = new LinkedHashSet<>();
        recipients.add(task.getAssigneeId());
        if (!TeamMember.CREATOR.equals(member.getRole())) {
            Team team = teamService.requireTeam(member.getTeamId());
            recipients.add(team.getCreatedBy());
        }
        recipients.remove(member.getAccountId());
        for (Long recipientId : recipients) {
            notificationService.notify(recipientId, member.getAccountId(), task.getId(), Notification.TASK_COMMENTED);
        }
        Account author = accounts.selectById(member.getAccountId());
        return CommentView.from(comment, author);
    }

    public List<TaskView> openTasks(TeamMember member) {
        List<Long> projectIds = projectIds(member.getTeamId());
        if (projectIds.isEmpty()) {
            return List.of();
        }
        List<TaskItem> rows = tasks.selectList(new LambdaQueryWrapper<TaskItem>()
                .in(TaskItem::getProjectId, projectIds)
                .eq(TaskItem::getAssigneeId, member.getAccountId())
                .ne(TaskItem::getStatus, TaskItem.DONE)
                .orderByAsc(TaskItem::getId));
        return toViews(rows);
    }

    public long inProgressProjectCount(Long teamId) {
        List<Long> projectIds = projectIds(teamId);
        if (projectIds.isEmpty()) {
            return 0;
        }
        return tasks.selectList(new LambdaQueryWrapper<TaskItem>()
                        .in(TaskItem::getProjectId, projectIds)
                        .eq(TaskItem::getStatus, TaskItem.DOING))
                .stream()
                .map(TaskItem::getProjectId)
                .distinct()
                .count();
    }

    private void notifyCreator(TeamMember actor, TaskItem task, String type) {
        if (TeamMember.CREATOR.equals(actor.getRole())) {
            return;
        }
        Team team = teamService.requireTeam(actor.getTeamId());
        notificationService.notify(team.getCreatedBy(), actor.getAccountId(), task.getId(), type);
    }

    private TaskItem requireOwnTask(TeamMember member, Long taskId) {
        TaskItem task = requireTask(member, taskId);
        if (!task.getAssigneeId().equals(member.getAccountId())) {
            throw new ApiException(403, "只能处理自己负责的任务");
        }
        return task;
    }

    private TaskItem requireTask(TeamMember member, Long taskId) {
        TaskItem task = tasks.selectById(taskId);
        if (task == null) {
            throw new ApiException(404, "任务不存在");
        }
        projectService.requireInTeam(task.getProjectId(), member.getTeamId());
        return task;
    }

    private void requireAssignee(Long teamId, Long assigneeId) {
        TeamMember assignee = members.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getAccountId, assigneeId)
                .eq(TeamMember::getTeamId, teamId));
        if (assignee == null) {
            throw new ApiException(400, "负责人必须是团队成员");
        }
    }

    private List<Long> projectIds(Long teamId) {
        return projects.selectList(new LambdaQueryWrapper<Project>().eq(Project::getTeamId, teamId))
                .stream()
                .map(Project::getId)
                .toList();
    }

    private List<TaskView> toViews(List<TaskItem> rows) {
        Map<Long, Account> people = loadAccounts(rows.stream().map(TaskItem::getAssigneeId).collect(Collectors.toSet()));
        return rows.stream().map(row -> TaskView.from(row, people.get(row.getAssigneeId()))).toList();
    }

    private TaskView toView(TaskItem task) {
        return TaskView.from(task, accounts.selectById(task.getAssigneeId()));
    }

    private Map<Long, Account> loadAccounts(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return accounts.selectByIds(ids).stream().collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    public record TaskView(
            Long id,
            Long projectId,
            Long stageId,
            String title,
            String description,
            Long assigneeId,
            String assigneeName,
            String status,
            LocalDateTime createdAt
    ) {
        static TaskView from(TaskItem task, Account assignee) {
            return new TaskView(task.getId(), task.getProjectId(), task.getStageId(), task.getTitle(),
                    task.getDescription(), task.getAssigneeId(),
                    assignee == null ? "" : assignee.getDisplayName(), task.getStatus(), task.getCreatedAt());
        }
    }

    public record CommentView(Long id, Long taskId, Long authorId, String authorName, String content,
                              LocalDateTime createdAt) {
        static CommentView from(TaskComment comment, Account author) {
            return new CommentView(comment.getId(), comment.getTaskId(), comment.getAuthorId(),
                    author == null ? "" : author.getDisplayName(), comment.getContent(), comment.getCreatedAt());
        }
    }
}
