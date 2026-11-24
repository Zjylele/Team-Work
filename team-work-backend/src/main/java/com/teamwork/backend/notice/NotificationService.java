package com.teamwork.backend.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.task.TaskItem;
import com.teamwork.backend.task.TaskMapper;
import com.teamwork.backend.team.TeamMember;
import com.teamwork.backend.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationMapper notifications;
    private final TaskMapper tasks;
    private final ProjectService projects;
    private final WebhookChannel webhook;

    public NotificationService(NotificationMapper notifications, TaskMapper tasks, ProjectService projects, WebhookChannel webhook) {
        this.notifications = notifications;
        this.tasks = tasks;
        this.projects = projects;
        this.webhook = webhook;
    }

    @Transactional
    public void notify(Long recipientId, Long actorId, Long taskId, String type) {
        if (recipientId == null || recipientId.equals(actorId)) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setTaskId(taskId);
        notification.setType(type);
        notification.setReadFlag(0);
        notification.setCreatedAt(LocalDateTime.now());
        notifications.insert(notification);
        webhook.afterNotification(recipientId);
    }

    public List<NotificationView> list(Long recipientId) {
        return notifications.selectList(new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getRecipientId, recipientId)
                        .orderByDesc(Notification::getCreatedAt)
                        .orderByDesc(Notification::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    public List<NotificationView> list(TeamMember member) {
        return list(member.getAccountId()).stream().filter(n -> {
            TaskItem task = tasks.selectById(n.taskId());
            if (task == null) return false;
            try { projects.requireAccessible(task.getProjectId(), member); return true; }
            catch (ApiException ex) { return false; }
        }).toList();
    }

    public List<NotificationView> recent(Long recipientId, int limit) {
        return list(recipientId).stream().limit(limit).toList();
    }

    @Transactional
    public NotificationView open(Long recipientId, Long notificationId) {
        Notification notification = notifications.selectById(notificationId);
        if (notification == null || !notification.getRecipientId().equals(recipientId)) {
            throw new ApiException(404, "通知不存在");
        }
        notification.setReadFlag(1);
        notifications.updateById(notification);
        return toView(notification);
    }

    @Transactional
    public NotificationView open(TeamMember member, Long notificationId) {
        Notification n = notifications.selectById(notificationId);
        if (n == null || !n.getRecipientId().equals(member.getAccountId())) throw new ApiException(404, "通知不存在");
        TaskItem task = tasks.selectById(n.getTaskId());
        if (task == null) throw new ApiException(404, "通知不存在");
        projects.requireAccessible(task.getProjectId(), member);
        return open(member.getAccountId(), notificationId);
    }

    @Transactional
    public void markAllRead(Long recipientId) {
        notifications.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getRecipientId, recipientId)
                .set(Notification::getReadFlag, 1));
    }

    @Transactional
    public void markAllRead(TeamMember member) {
        for (Notification n : notifications.selectList(new LambdaQueryWrapper<Notification>().eq(Notification::getRecipientId, member.getAccountId()).eq(Notification::getReadFlag, 0))) {
            TaskItem task = tasks.selectById(n.getTaskId());
            if (task == null) continue;
            try { projects.requireAccessible(task.getProjectId(), member); }
            catch (ApiException ex) { continue; }
            n.setReadFlag(1); notifications.updateById(n);
        }
    }

    private NotificationView toView(Notification notification) {
        TaskItem task = tasks.selectById(notification.getTaskId());
        String title = task == null ? "" : task.getTitle();
        return new NotificationView(
                notification.getId(),
                notification.getTaskId(),
                notification.getType(),
                summary(notification.getType(), title),
                notification.getReadFlag() != null && notification.getReadFlag() == 1,
                notification.getCreatedAt());
    }

    static String summary(String type, String title) {
        return switch (type) {
            case Notification.TASK_ASSIGNED -> "你被分配了任务「" + title + "」";
            case Notification.TASK_COMMENTED -> "任务「" + title + "」有新评论";
            case Notification.TASK_STATUS_CHANGED -> "任务「" + title + "」的状态已变更";
            case "TASK_REVIEW_REQUESTED" -> "任务「" + title + "」等待你验收";
            case "TASK_DUE_REMINDER" -> "任务「" + title + "」已到截止日期";
            case Notification.TASK_STARTED -> "成员开始了任务「" + title + "」";
            case Notification.TASK_COMPLETED -> "成员完成了任务「" + title + "」";
            default -> "任务「" + title + "」有新动态";
        };
    }

    public record NotificationView(
            Long id,
            Long taskId,
            String type,
            String summary,
            boolean read,
            LocalDateTime createdAt
    ) {
    }
}
