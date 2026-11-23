package com.teamwork.backend.team;

import com.teamwork.backend.common.CurrentAccount;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeamCalendarController {
    private final CurrentAccount account;
    private final TeamCalendar calendar;

    public TeamCalendarController(CurrentAccount account, TeamCalendar calendar) {
        this.account = account;
        this.calendar = calendar;
    }

    @GetMapping("/api/team/calendar-context")
    public TeamCalendar.Context context() {
        account.requireMember();
        return calendar.context();
    }
}
