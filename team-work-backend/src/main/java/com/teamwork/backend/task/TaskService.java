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
import com.teamwork.backend.team.TeamCalendar;
import com.teamwork.backend.team.TeamMember;
import com.teamwork.backend.team.TeamMemberMapper;
import com.teamwork.backend.team.TeamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.teamwork.backend.planning.TaskDependencyMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskMapper tasks;
    private final TaskDependencyMapper dependencies;
    private final TaskCommentMapper comments;
    private final TaskActivityMapper activities;
    private final AccountMapper accounts;
    private final TeamMemberMapper members;
    private final ProjectMapper projects;
    private final ProjectService projectService;
    private final TeamService teamService;
    private final TeamCalendar teamCalendar;
    private final NotificationService notificationService;
    private final TaskWorkflow workflow;

    public TaskService(TaskMapper tasks, TaskCommentMapper comments, TaskActivityMapper activities, AccountMapper accounts,
                       TeamMemberMapper members, ProjectMapper projects, ProjectService projectService,
                       TeamService teamService, NotificationService notificationService, TaskDependencyMapper dependencies,
                       TeamCalendar teamCalendar, TaskWorkflow workflow) {
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.comments = comments;
        this.activities = activities;
        this.accounts = accounts;
        this.members = members;
        this.projects = projects;
        this.projectService = projectService;
        this.teamService = teamService;
        this.teamCalendar = teamCalendar;
        this.notificationService = notificationService;
        this.workflow = workflow;
    }

    public List<TaskView> list(TeamMember member, Long projectId) {
        Project project = projectService.requireAccessible(projectId, member);
        List<TaskItem> rows = tasks.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getProjectId, project.getId())
                .orderByAsc(TaskItem::getId));
        return toViews(rows);
    }

    public TaskView get(TeamMember member, Long taskId) {
        return toView(requireTask(member, taskId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(TeamMember creator, Long taskId) {
        TaskItem task = lockTask(creator, taskId);
        projectService.requireWritable(task.getProjectId(), creator);
        tasks.deleteById(taskId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskView create(TeamMember creator, Long projectId, String title, String description,
                           Long assigneeId, Long stageId, String priority, String dueDate, String startDate) {
        Project project = projectService.requireAccessible(projectId, creator);
        projects.lock(projectId);
        projectService.requireWritable(projectId, creator);
        Stage stage = projectService.requireStage(creator, stageId);
        if (!stage.getProjectId().equals(project.getId())) {
            throw new ApiException(400, "阶段不属于该项目");
        }
        projectService.requireAssignable(project.getId(), assigneeId, creator.getTeamId());
        TaskItem task = new TaskItem();
        task.setProjectId(project.getId());
        task.setStageId(stage.getId());
        task.setTitle(title);
        task.setDescription(description == null ? "" : description);
        task.setAssigneeId(assigneeId);
        task.setStatus(TaskItem.TODO);
        task.setPriority("NONE");
        applyPlanning(task, priority, dueDate, startDate);
        task.setCreatedAt(LocalDateTime.now());
        tasks.insert(task);
        notifyAssignment(task, creator);
        return toView(task);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskView update(TeamMember actor, Long taskId, String title, String description,
                           Long assigneeId, Long stageId, String priority, String dueDate, String startDate) {
        TaskItem task = lockTask(actor, taskId);
        projectService.requireWritable(task.getProjectId(), actor);
        if (!TeamMember.CREATOR.equals(actor.getRole())) {
            if (!task.getAssigneeId().equals(actor.getAccountId())) {
                throw new ApiException(403, "只能补充自己负责任务的说明");
            }
            if (title != null || assigneeId != null || stageId != null || priority != null || dueDate != null || startDate != null) {
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
        String oldDueDate = java.util.Objects.toString(task.getDueDate(), null);
        String oldStartDate = java.util.Objects.toString(task.getStartDate(), null);
        applyPlanning(task, priority, dueDate, startDate);
        Long previousAssignee = task.getAssigneeId();
        if (assigneeId != null) {
            projectService.requireAssignable(task.getProjectId(), assigneeId, actor.getTeamId());
            task.setAssigneeId(assigneeId);
        }
        tasks.updateById(task);
        if (!java.util.Objects.equals(previousAssignee, task.getAssigneeId())) recordActivity(actor, task, "ASSIGNEE_CHANGED", previousAssignee.toString(), task.getAssigneeId().toString());
        String newDueDate = task.getDueDate() == null ? null : task.getDueDate().toString();
        if (!java.util.Objects.equals(oldDueDate, newDueDate)) recordActivity(actor, task, "DUE_DATE_CHANGED", oldDueDate, newDueDate);
        String newStartDate = java.util.Objects.toString(task.getStartDate(), null);
        if (!java.util.Objects.equals(oldStartDate, newStartDate)) {
            recordActivity(actor, task, "START_DATE_CHANGED", oldStartDate, newStartDate);
        }
        if (assigneeId != null && !assigneeId.equals(previousAssignee)) {
            notifyAssignment(task, actor);
        }
        return toView(task);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskView start(TeamMember member, Long taskId) {
        TaskItem task = requireOwnTask(member, taskId);
        projectService.requireWritable(task.getProjectId(), member);
        if (!TaskItem.TODO.equals(task.getStatus())) {
            throw new ApiException(409, "只有待开始的任务可以开始");
        }
        requireCompletedPredecessors(task);
        transition(member, task, TaskItem.DOING);
        if (workflow.config(task.getProjectId()).statusReminder())
            notifyCreator(member, task, Notification.TASK_STARTED);
        return toView(task);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskView complete(TeamMember member, Long taskId) {
        TaskItem task = requireOwnTask(member, taskId);
        projectService.requireWritable(task.getProjectId(), member);
        if (!TaskItem.DOING.equals(task.getStatus())) {
            throw new ApiException(409, "只有进行中的任务可以结束");
        }
        var config = workflow.config(task.getProjectId());
        if (config.approverId() != null) {
            workflow.request(task, member, config.approverId());
            transition(member, task, TaskWorkflow.REVIEW);
        } else {
            transition(member, task, TaskItem.DONE);
            if (config.statusReminder()) notifyCreator(member, task, Notification.TASK_COMPLETED);
        }
        return toView(task);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskView correct(TeamMember creator, Long taskId, String status) {
        if (!TaskItem.valid(status)) {
            throw new ApiException(400, "状态只能是待开始、进行中或已完成");
        }
        TaskItem task = lockTask(creator, taskId);
        projectService.requireWritable(task.getProjectId(), creator);
        if (!status.equals(task.getStatus())) {
            String previousStatus = task.getStatus();
            if (!TaskItem.TODO.equals(status) && TaskItem.TODO.equals(previousStatus)) {
                requireCompletedPredecessors(task);
            }
            var config = workflow.config(task.getProjectId());
            if (TaskWorkflow.REVIEW.equals(previousStatus) || (config.approverId() != null && TaskItem.DONE.equals(status))) {
                throw new ApiException(409, "请通过审批完成或退回任务");
            }
            transition(creator, task, status);
        }
        return toView(task);
    }

    public List<TaskWorkflow.Approval> approvals(TeamMember member, Long taskId) {
        requireTask(member, taskId);
        return workflow.history(taskId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskView decide(TeamMember actor, Long taskId, Long approvalId, boolean approved, String comment) {
        TaskItem task = lockTask(actor, taskId);
        projectService.requireWritable(task.getProjectId(), actor);
        var approval = requireApproval(taskId, approvalId);
        if (!approval.approverId().equals(actor.getAccountId())) throw new ApiException(403, "仅指定审批人可以处理");
        String outcome = approved ? "APPROVED" : "REJECTED";
        String reason = comment == null ? "" : comment.strip();
        if (!approval.status().equals("PENDING")) {
            if (outcome.equals(approval.status()) && reason.equals(approval.comment())) return toView(task);
            throw new ApiException(409, "该审批已处理");
        }
        if (!TaskWorkflow.REVIEW.equals(task.getStatus())) throw new ApiException(409, "任务不在待验收状态");
        workflow.decide(approvalId, approved, reason);
        recordActivity(actor, task, "APPROVAL_" + outcome, null, Long.toString(approvalId));
        transition(actor, task, approved ? TaskItem.DONE : TaskItem.DOING);
        return toView(task);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskView reassignApproval(TeamMember actor, Long taskId, Long approvalId) {
        TaskItem task = lockTask(actor, taskId);
        var project = projectService.requireAccessible(task.getProjectId(), actor);
        String role = projectService.role(project, actor);
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) {
            throw new ApiException(403, "只有项目所有者或管理员可以转交审批");
        }
        var approval = requireApproval(taskId, approvalId);
        if (!TaskWorkflow.REVIEW.equals(task.getStatus()) || !"PENDING".equals(approval.status())) {
            throw new ApiException(409, "只能转交待处理的审批");
        }
        Long approver = workflow.config(task.getProjectId()).approverId();
        if (approver == null) throw new ApiException(409, "请先在项目设置中指定新的审批人");
        projectService.requireAssignable(task.getProjectId(), approver, actor.getTeamId());
        if (!approver.equals(approval.approverId())) {
            workflow.reassign(taskId, approvalId, approver);
            recordActivity(actor, task, "APPROVAL_REASSIGNED", approval.approverId().toString(), approver.toString());
        }
        return toView(task);
    }

    private TaskWorkflow.Approval requireApproval(Long taskId, Long approvalId) {
        return workflow.history(taskId).stream().filter(row -> row.id().equals(approvalId)).findFirst()
                .orElseThrow(() -> new ApiException(404, "审批不存在"));
    }

    private void transition(TeamMember actor, TaskItem task, String status) {
        String previous = task.getStatus();
        task.setStatus(status);
        if (tasks.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TaskItem>()
                .eq(TaskItem::getId, task.getId()).eq(TaskItem::getStatus, previous)
                .set(TaskItem::getStatus, status)) != 1) throw new ApiException(409, "任务状态已变化，请刷新后重试");
        recordActivity(actor, task, "STATUS_CHANGED", previous, status);
        notifyAutomation(task, task.getAssigneeId(), actor, Notification.TASK_STATUS_CHANGED, false);
    }

    private void notifyAssignment(TaskItem task, TeamMember actor) {
        notifyAutomation(task, task.getAssigneeId(), actor, Notification.TASK_ASSIGNED, true);
    }

    public List<CommentView> comments(TeamMember member, Long taskId) {
        requireTask(member, taskId);
        List<TaskComment> rows = comments.selectList(new LambdaQueryWrapper<TaskComment>()
                .eq(TaskComment::getTaskId, taskId)
                .orderByAsc(TaskComment::getId));
        Map<Long, Account> authors = loadAccounts(rows.stream().map(TaskComment::getAuthorId).collect(Collectors.toSet()));
        return rows.stream().map(row -> CommentView.from(row, authors.get(row.getAuthorId()))).toList();
    }

    public List<ActivityView> activity(TeamMember member, Long taskId) {
        requireTask(member, taskId);
        List<TaskActivity> rows = activities.selectList(new LambdaQueryWrapper<TaskActivity>()
                .eq(TaskActivity::getTaskId, taskId).orderByAsc(TaskActivity::getCreatedAt).orderByAsc(TaskActivity::getId));
        Map<Long, Account> people = loadAccounts(rows.stream().map(TaskActivity::getActorId).collect(Collectors.toSet()));
        return rows.stream().map(a -> new ActivityView(a.getId(), a.getTaskId(), a.getActorId(),
                people.containsKey(a.getActorId()) ? people.get(a.getActorId()).getDisplayName() : "", a.getAction(),
                a.getBeforeValue(), a.getAfterValue(), a.getCreatedAt())).toList();
    }

    private void recordActivity(TeamMember actor, TaskItem task, String action, String before, String after) {
        TaskActivity row = new TaskActivity(); row.setTaskId(task.getId()); row.setActorId(actor.getAccountId());
        row.setAction(action); row.setBeforeValue(before); row.setAfterValue(after); row.setCreatedAt(teamCalendar.now());
        activities.insert(row);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommentView comment(TeamMember member, Long taskId, String content) {
        TaskItem task = requireTask(member, taskId);
        projectService.requireWritable(task.getProjectId(), member);
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
        List<Long> projectIds = projectService.list(member).stream().map(ProjectService.ProjectView::id).toList();
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

    public long inProgressProjectCount(TeamMember member) {
        List<Long> projectIds = projectService.list(member).stream().map(ProjectService.ProjectView::id).toList();
        if (projectIds.isEmpty()) {
            return 0;
        }
        return tasks.selectList(new LambdaQueryWrapper<TaskItem>()
                        .in(TaskItem::getProjectId, projectIds)
                        .in(TaskItem::getStatus, TaskItem.DOING, TaskWorkflow.REVIEW))
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
        notifyAutomation(task, team.getCreatedBy(), actor, type, false);
    }

    private void notifyAutomation(TaskItem task, Long recipient, TeamMember actor, String type, boolean assignment) {
        if (recipient.equals(actor.getAccountId())) return;
        var config = workflow.storedConfig(task.getProjectId());
        if (config.isEmpty()) {
            notificationService.notify(recipient, actor.getAccountId(), task.getId(), type);
        } else if (assignment ? config.get().assignmentReminder() : config.get().statusReminder()) {
            workflow.event(task.getId(), recipient, type, java.util.UUID.randomUUID().toString());
        }
    }

    private TaskItem requireOwnTask(TeamMember member, Long taskId) {
        TaskItem task = lockTask(member, taskId);
        if (!task.getAssigneeId().equals(member.getAccountId())) {
            throw new ApiException(403, "只能处理自己负责的任务");
        }
        return task;
    }

    private void requireCompletedPredecessors(TaskItem task) {
        if (dependencies.incompletePredecessors(task.getId()) > 0) {
            throw new ApiException(409, "请先完成前置任务");
        }
    }

    private TaskItem lockTask(TeamMember member, Long taskId) {
        TaskItem task = requireTask(member, taskId);
        projects.lock(task.getProjectId());
        return requireTask(member, taskId);
    }

    private TaskItem requireTask(TeamMember member, Long taskId) {
        TaskItem task = tasks.selectById(taskId);
        if (task == null) {
            throw new ApiException(404, "任务不存在");
        }
        projectService.requireAccessible(task.getProjectId(), member);
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

    private void applyPlanning(TaskItem task, String priority, String dueDate, String startDate) {
        if (priority != null) {
            switch (priority) {
                case "NONE", "LOW", "MEDIUM", "HIGH", "URGENT" -> task.setPriority(priority);
                default -> throw new ApiException(400, "无效的优先级");
            }
        }
        if (dueDate != null) {
            task.setDueDate(parseDate(dueDate, "截止日期"));
        }
        if (startDate != null) {
            task.setStartDate(parseDate(startDate, "开始日期"));
        }
        if (task.getStartDate() != null && task.getDueDate() != null
                && task.getDueDate().isBefore(task.getStartDate())) {
            throw new ApiException(400, "截止日期不能早于开始日期");
        }
    }

    private LocalDate parseDate(String value, String label) {
        try {
            return value.isEmpty() ? null : LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ApiException(400, label + "格式应为 YYYY-MM-DD");
        }
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
            String priority,
            LocalDate dueDate,
            LocalDate startDate,
            LocalDateTime createdAt
    ) {
        static TaskView from(TaskItem task, Account assignee) {
            return new TaskView(task.getId(), task.getProjectId(), task.getStageId(), task.getTitle(),
                    task.getDescription(), task.getAssigneeId(),
                    assignee == null ? "" : assignee.getDisplayName(), task.getStatus(), task.getPriority(), task.getDueDate(), task.getStartDate(), task.getCreatedAt());
        }
    }

    public record CommentView(Long id, Long taskId, Long authorId, String authorName, String content,
                              LocalDateTime createdAt) {
        static CommentView from(TaskComment comment, Account author) {
            return new CommentView(comment.getId(), comment.getTaskId(), comment.getAuthorId(),
                    author == null ? "" : author.getDisplayName(), comment.getContent(), comment.getCreatedAt());
        }
    }
    public record ActivityView(Long id, Long taskId, Long actorId, String actorName, String action,
                               String beforeValue, String afterValue, LocalDateTime createdAt) {}
}
