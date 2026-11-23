package com.teamwork.backend.task;

import com.teamwork.backend.team.TeamCalendar;
import com.teamwork.backend.notice.NotificationService;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

@Component
public class TaskAutomationDelivery {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final TeamCalendar calendar;
    private final NotificationService notifications;

    TaskAutomationDelivery(JdbcTemplate db, TransactionTemplate transactions, TeamCalendar calendar,
                           NotificationService notifications) {
        this.db = db;
        this.transactions = transactions;
        this.calendar = calendar;
        this.notifications = notifications;
    }

    @Scheduled(fixedDelayString = "${app.workflow.delivery-delay-ms:1000}")
    public void deliver() {
        transactions.executeWithoutResult(ignored -> {
            var batch = db.query("""
                    SELECT id, recipient_id, task_id, action_type FROM task_automation_event WHERE delivered=FALSE
                    ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED
                    """, (rs, n) -> new Delivery(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4)));
            for (Delivery event : batch) {
                notifications.notify(event.recipientId(), null, event.taskId(), event.type());
                db.update("UPDATE task_automation_event SET delivered=TRUE WHERE id=?", event.id());
            }
        });
    }

    private record Delivery(long id, long recipientId, long taskId, String type) {}

    @Scheduled(fixedDelayString = "${app.workflow.due-scan-delay-ms:60000}")
    public void enqueueDueReminders() {
        LocalDate today = calendar.context().today();
        db.update("""
                INSERT INTO task_automation_event(task_id, recipient_id, action_type, dedup_key)
                SELECT t.id, t.assignee_id, 'TASK_DUE_REMINDER', CONCAT(t.due_date, ':', t.assignee_id)
                FROM task t JOIN project_workflow w ON w.project_id=t.project_id
                WHERE w.due_reminder=TRUE AND t.status<>'DONE' AND t.due_date<=?
                ON DUPLICATE KEY UPDATE delivered=task_automation_event.delivered
                """, today);
    }
}

@Configuration
@EnableScheduling
class TaskAutomationScheduling {}
