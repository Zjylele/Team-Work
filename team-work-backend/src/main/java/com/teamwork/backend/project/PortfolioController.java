package com.teamwork.backend.project;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.common.CurrentAccount;
import com.teamwork.backend.task.TaskItem;
import com.teamwork.backend.task.TaskService;
import com.teamwork.backend.team.TeamMember;
import com.teamwork.backend.team.TeamCalendar;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {
    private final CurrentAccount account;
    private final PortfolioMapper portfolios;
    private final ProjectService projects;
    private final TaskService tasks;
    private final TeamCalendar calendar;

    public PortfolioController(CurrentAccount account, PortfolioMapper portfolios, ProjectService projects,
                               TaskService tasks, TeamCalendar calendar) {
        this.account = account;
        this.portfolios = portfolios;
        this.projects = projects;
        this.tasks = tasks;
        this.calendar = calendar;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PortfolioView> list() {
        TeamMember member = account.requireMember();
        Set<Long> accessible = projects.list(member).stream().map(ProjectService.ProjectView::id).collect(Collectors.toSet());
        return portfolios.list(member.getTeamId()).stream().map(row -> toView(row, accessible)).toList();
    }

    @PostMapping
    @Transactional
    public PortfolioView create(@Valid @RequestBody Form form) {
        TeamMember creator = account.requireCreator();
        validateProjects(creator, form.projectIds());
        PortfolioMapper.NewPortfolio row = new PortfolioMapper.NewPortfolio();
        row.setTeamId(creator.getTeamId());
        row.setName(form.name().strip());
        portfolios.insert(row);
        form.projectIds().forEach(projectId -> portfolios.addProject(row.getId(), projectId));
        return new PortfolioView(row.getId(), row.getName(), portfolios.projectIds(row.getId()));
    }

    @PutMapping("/{id}")
    @Transactional
    public PortfolioView update(@PathVariable long id, @Valid @RequestBody Form form) {
        TeamMember creator = account.requireCreator();
        if (portfolios.lock(creator.getTeamId(), id) == null) {
            throw new ApiException(404, "项目集不存在");
        }
        Set<Long> accessible = projects.list(creator).stream().map(ProjectService.ProjectView::id).collect(Collectors.toSet());
        Set<Long> existing = Set.copyOf(portfolios.projectIds(id));
        Set<Long> additions = form.projectIds().stream().filter(projectId -> !existing.contains(projectId))
                .collect(Collectors.toSet());
        validateProjects(creator, additions);
        String name = form.name().strip();
        portfolios.rename(id, name);
        existing.stream().filter(accessible::contains).filter(projectId -> !form.projectIds().contains(projectId))
                .sorted().forEach(projectId -> {
                    projects.requireWritable(projectId, creator);
                    portfolios.removeProject(id, projectId);
                });
        additions.stream().sorted().forEach(projectId -> portfolios.addProject(id, projectId));
        return toView(new PortfolioMapper.Portfolio(id, name), accessible);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable long id) {
        portfolios.delete(account.requireCreator().getTeamId(), id);
    }

    @GetMapping("/{id}/summary")
    @Transactional(readOnly = true)
    public Summary summary(@PathVariable long id) {
        Scope scope = scope(id);
        Map<Long, List<TaskService.TaskView>> byMember = scope.rows().stream().map(PortfolioTask::task)
                .filter(task -> !completed(task)).collect(Collectors.groupingBy(TaskService.TaskView::assigneeId));
        List<MemberLoad> memberLoads = byMember.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> memberLoad(entry.getKey(), entry.getValue(), scope)).toList();
        return new Summary(scope.rows().size(), scope.count("completed"), scope.count("overdue"),
                scope.count("completedThisWeek"), memberLoads);
    }

    @GetMapping("/{id}/tasks")
    @Transactional(readOnly = true)
    public List<PortfolioTask> tasks(@PathVariable long id,
                                     @RequestParam(defaultValue = "all") String metric,
                                     @RequestParam(required = false) Long accountId,
                                     @RequestParam(required = false) String bucket) {
        Scope scope = scope(id);
        return scope.rows().stream().filter(scope.filter(metric, accountId, bucket)).toList();
    }

    private Scope scope(long id) {
        TeamMember member = account.requireMember();
        if (portfolios.get(member.getTeamId(), id) == null) {
            throw new ApiException(404, "项目集不存在");
        }
        Set<Long> configured = Set.copyOf(portfolios.projectIds(id));
        var visibleProjects = projects.list(member).stream().filter(p -> configured.contains(p.id())).toList();
        List<PortfolioTask> rows = visibleProjects.stream()
                .flatMap(project -> tasks.list(member, project.id()).stream()
                        .map(task -> new PortfolioTask(task, project.name()))).toList();
        TeamCalendar.Context dates = calendar.context();
        Set<Long> completedThisWeek = visibleProjects.isEmpty() ? Set.of() : portfolios.completions(
                        visibleProjects.stream().map(ProjectService.ProjectView::id).toList(),
                        dates.weekStart().atStartOfDay(), dates.weekEnd().atStartOfDay())
                .stream().map(PortfolioMapper.Completion::taskId).collect(Collectors.toSet());
        return new Scope(rows, dates.today(), dates.weekEnd(), completedThisWeek);
    }

    private static MemberLoad memberLoad(long id, List<TaskService.TaskView> rows, Scope scope) {
        long overdue = rows.stream().filter(scope.memberDueFilter("overdue")).count();
        long withoutDueDate = rows.stream().filter(scope.memberDueFilter("withoutDueDate")).count();
        long upcoming = rows.stream().filter(scope.memberDueFilter("upcoming")).count();
        return new MemberLoad(id, rows.getFirst().assigneeName(), rows.size(), overdue,
                rows.size() - overdue - upcoming - withoutDueDate, upcoming, withoutDueDate);
    }

    private static boolean completed(TaskService.TaskView task) {
        return TaskItem.DONE.equals(task.status());
    }

    private static boolean overdue(TaskService.TaskView task, LocalDate today) {
        return !completed(task) && task.dueDate() != null && task.dueDate().isBefore(today);
    }

    private void validateProjects(TeamMember creator, Set<Long> projectIds) {
        projectIds.stream().sorted().forEach(projectId -> projects.requireWritable(projectId, creator));
    }

    private PortfolioView toView(PortfolioMapper.Portfolio row, Set<Long> accessible) {
        return new PortfolioView(row.id(), row.name(),
                portfolios.projectIds(row.id()).stream().filter(accessible::contains).toList());
    }

    private record Scope(List<PortfolioTask> rows, LocalDate today, LocalDate nextMonday, Set<Long> completedThisWeek) {
        Predicate<PortfolioTask> filter(String metric, Long accountId, String bucket) {
            return switch (metric) {
                case "all" -> row -> true;
                case "completed" -> row -> completed(row.task());
                case "overdue" -> row -> overdue(row.task(), today);
                case "completedThisWeek" -> row -> completedThisWeek.contains(row.task().id());
                case "memberLoad" -> {
                    if (accountId == null) throw new ApiException(400, "请提供成员账号");
                    Predicate<TaskService.TaskView> dueFilter = memberDueFilter(bucket);
                    yield row -> !completed(row.task()) && accountId.equals(row.task().assigneeId())
                            && dueFilter.test(row.task());
                }
                default -> throw new ApiException(400, "无效的项目集指标");
            };
        }

        Predicate<TaskService.TaskView> memberDueFilter(String bucket) {
            if (bucket == null) return task -> true;
            return switch (bucket) {
                case "overdue" -> task -> overdue(task, today);
                case "dueThisWeek" -> task -> task.dueDate() != null && !task.dueDate().isBefore(today)
                        && task.dueDate().isBefore(nextMonday);
                case "upcoming" -> task -> task.dueDate() != null && !task.dueDate().isBefore(nextMonday);
                case "withoutDueDate" -> task -> task.dueDate() == null;
                default -> throw new ApiException(400, "无效的到期区间");
            };
        }

        long count(String metric) {
            return rows.stream().filter(filter(metric, null, null)).count();
        }
    }

    public record Form(@NotBlank @Size(max = 64) String name, @NotNull @Size(max = 1000) Set<@NotNull Long> projectIds) {}
    public record PortfolioView(long id, String name, List<Long> projectIds) {}
    public record PortfolioTask(@JsonUnwrapped TaskService.TaskView task, String projectName) {}
    public record Summary(long total, long completed, long overdue, long completedThisWeek, List<MemberLoad> memberLoads) {}
    public record MemberLoad(long accountId, String accountName, long total, long overdue, long dueThisWeek,
                             long upcoming, long withoutDueDate) {}
}
