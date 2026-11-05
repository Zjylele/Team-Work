package com.teamwork.backend.workbench;

import com.teamwork.backend.common.CurrentAccount;
import com.teamwork.backend.notice.NotificationService;
import com.teamwork.backend.task.TaskService;
import com.teamwork.backend.team.TeamMember;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class WorkbenchController {

    private final CurrentAccount currentAccount;
    private final TaskService taskService;
    private final NotificationService notificationService;

    public WorkbenchController(CurrentAccount currentAccount, TaskService taskService,
                               NotificationService notificationService) {
        this.currentAccount = currentAccount;
        this.taskService = taskService;
        this.notificationService = notificationService;
    }

    @GetMapping("/api/workbench")
    public WorkbenchView workbench() {
        TeamMember member = currentAccount.requireMember();
        return new WorkbenchView(
                taskService.openTasks(member),
                taskService.inProgressProjectCount(member.getTeamId()),
                notificationService.recent(member.getAccountId(), 10));
    }

    public record WorkbenchView(
            List<TaskService.TaskView> openTasks,
            long inProgressProjectCount,
            List<NotificationService.NotificationView> recentNotifications
    ) {
    }
}
