package com.teamwork.backend.notice;

import com.teamwork.backend.common.CurrentAccount;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentAccount currentAccount;

    public NotificationController(NotificationService notificationService, CurrentAccount currentAccount) {
        this.notificationService = notificationService;
        this.currentAccount = currentAccount;
    }

    @GetMapping("/api/notifications")
    public List<NotificationService.NotificationView> list() {
        return notificationService.list(currentAccount.requireMember());
    }

    @PostMapping("/api/notifications/{id}/read")
    public NotificationService.NotificationView open(@PathVariable Long id) {
        return notificationService.open(currentAccount.requireMember(), id);
    }

    @PostMapping("/api/notifications/read-all")
    public Map<String, String> readAll() {
        notificationService.markAllRead(currentAccount.requireMember());
        return Map.of("message", "已全部标为已读");
    }
}
