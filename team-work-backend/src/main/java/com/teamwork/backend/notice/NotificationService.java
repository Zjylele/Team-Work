package com.teamwork.backend.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.task.TaskItem;
import com.teamwork.backend.task.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationMapper notifications;
    private final TaskMapper tasks;

    public NotificationService(NotificationMapper notifications, TaskMapper tasks) {
        this.notifications = notifications;
        this.tasks = tasks;
    }

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
    public void markAllRead(Long recipientId) {
        notifications.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getRecipientId, recipientId)
                .set(Notification::getReadFlag, 1));
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
            case Notification.TASK_STATUS_CHANGED -> "创建者修改了任务「" + title + "」的状态";
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
