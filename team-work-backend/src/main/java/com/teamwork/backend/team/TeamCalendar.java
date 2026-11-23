package com.teamwork.backend.team;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

@Service
public class TeamCalendar {
    private final ZoneId zone;

    public TeamCalendar(@Value("${app.team.time-zone:Australia/Perth}") String zoneId) {
        this.zone = ZoneId.of(zoneId);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(zone);
    }

    public Context context() {
        LocalDate today = LocalDate.now(zone);
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new Context(today, start, start.plusWeeks(1), zone.getId());
    }

    public record Context(LocalDate today, LocalDate weekStart, LocalDate weekEnd, String zoneId) {}
}
