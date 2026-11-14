package com.teamwork.backend.planning;

import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.common.CurrentAccount;
import com.teamwork.backend.project.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/projects/{projectId}/planning")
public class PlanningController {
    private final CurrentAccount account;
    private final ProjectService projects;
    private final PlanningMapper plans;

    public PlanningController(CurrentAccount account, ProjectService projects, PlanningMapper plans) {
        this.account = account;
        this.projects = projects;
        this.plans = plans;
    }

    @GetMapping
    public List<Entry> list(@PathVariable long projectId) {
        projects.requireInTeam(projectId, account.requireMember().getTeamId());
        return plans.list(projectId).stream().map(plan -> new Entry(plan, plans.tasks(plan.id()))).toList();
    }

    @PostMapping
    @Transactional
    public void create(@PathVariable long projectId, @Valid @RequestBody Form form) {
        authorize(projectId);
        validateDates(form);
        plans.create(projectId, form);
    }

    @PutMapping("/{planId}")
    @Transactional
    public void update(@PathVariable long projectId, @PathVariable long planId, @Valid @RequestBody Change change) {
        authorize(projectId);
        validateDates(change.form());
        if (plans.lock(projectId, planId) == null || plans.update(projectId, planId, change.form()) == 0) {
            throw new ApiException(404, "计划不存在");
        }
        plans.clearTasks(planId);
        for (Long taskId : change.taskIds()) {
            if (plans.addTask(projectId, planId, taskId) == 0) {
                throw new ApiException(400, "任务不属于当前项目或已删除");
            }
        }
    }

    @DeleteMapping("/{planId}")
    @Transactional
    public void delete(@PathVariable long projectId, @PathVariable long planId) {
        authorize(projectId);
        plans.delete(projectId, planId);
    }

    private void authorize(long projectId) {
        projects.requireInTeam(projectId, account.requireCreator().getTeamId());
    }

    private void validateDates(Form form) {
        if ("CYCLE".equals(form.kind()) && (form.startDate() == null || form.endDate() == null)) {
            throw new ApiException(400, "迭代需要开始和结束日期");
        }
        if (form.startDate() != null && form.endDate() != null && form.endDate().isBefore(form.startDate())) {
            throw new ApiException(400, "结束日期不能早于开始日期");
        }
    }

    public record Entry(PlanningMapper.Plan plan, List<Long> taskIds) {}
    public record Form(@NotNull @Pattern(regexp = "CYCLE|MODULE") String kind,
                       @NotBlank @Size(max = 80) String name,
                       @NotNull @Size(max = 1000) String description,
                       LocalDate startDate, LocalDate endDate) {}
    public record Change(@NotNull @Valid Form form, @NotNull @Size(max = 1000) Set<@NotNull Long> taskIds) {}
}
