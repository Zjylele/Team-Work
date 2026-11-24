package com.teamwork.backend.planning;

import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.common.CurrentAccount;
import com.teamwork.backend.project.ProjectMapper;
import com.teamwork.backend.project.ProjectService;
import com.teamwork.backend.task.TaskItem;
import com.teamwork.backend.task.TaskMapper;
import com.teamwork.backend.task.TaskService;
import com.teamwork.backend.team.TeamMember;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class TimelineController {
    private final CurrentAccount account;
    private final ProjectService projects;
    private final ProjectMapper projectRows;
    private final TaskService tasks;
    private final TaskMapper taskRows;
    private final PlanningMapper plans;
    private final TaskDependencyMapper dependencies;

    public TimelineController(CurrentAccount account, ProjectService projects, ProjectMapper projectRows,
                              TaskService tasks, TaskMapper taskRows, PlanningMapper plans, TaskDependencyMapper dependencies) {
        this.account = account;
        this.projects = projects;
        this.projectRows = projectRows;
        this.tasks = tasks;
        this.taskRows = taskRows;
        this.plans = plans;
        this.dependencies = dependencies;
    }

    @GetMapping("/timeline")
    @Transactional(readOnly = true)
    public Timeline timeline(@PathVariable long projectId) {
        TeamMember member = account.requireMember();
        var project = projects.requireAccessible(projectId, member);
        var planning = plans.list(projectId);
        return new Timeline(projects.stages(project), tasks.list(member, projectId),
                planning.stream().filter(plan -> "MILESTONE".equals(plan.kind())).toList(),
                dependencies.list(projectId),
                planning.stream().map(plan -> new PlanningController.Entry(plan, plans.tasks(plan.id()))).toList());
    }

    @PostMapping("/dependencies")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void add(@PathVariable long projectId, @RequestBody TaskDependencyMapper.Dependency dependency) {
        lockProject(projectId);
        TaskItem predecessor = requireProjectTask(projectId, dependency.predecessorTaskId());
        TaskItem successor = requireProjectTask(projectId, dependency.successorTaskId());
        if (predecessor.getId().equals(successor.getId())) {
            throw new ApiException(400, "任务不能依赖自身");
        }
        List<TaskDependencyMapper.Dependency> edges = dependencies.list(projectId);
        if (edges.contains(dependency)) {
            return;
        }
        if (createsCycle(dependency, edges)) {
            throw new ApiException(409, "此依赖会形成循环");
        }
        if (!TaskItem.DONE.equals(predecessor.getStatus()) && !TaskItem.TODO.equals(successor.getStatus())) {
            throw new ApiException(409, "后续任务已经开始，请先完成前置任务");
        }
        dependencies.insert(dependency);
    }

    @DeleteMapping("/dependencies")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(@PathVariable long projectId, @RequestBody TaskDependencyMapper.Dependency dependency) {
        lockProject(projectId);
        requireProjectTask(projectId, dependency.predecessorTaskId());
        requireProjectTask(projectId, dependency.successorTaskId());
        dependencies.delete(dependency);
    }

    static boolean createsCycle(TaskDependencyMapper.Dependency candidate, List<TaskDependencyMapper.Dependency> edges) {
        Map<Long, List<Long>> outgoing = new HashMap<>();
        for (var edge : edges) {
            outgoing.computeIfAbsent(edge.predecessorTaskId(), ignored -> new ArrayList<>()).add(edge.successorTaskId());
        }
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(candidate.successorTaskId());
        while (!pending.isEmpty()) {
            Long next = pending.removeFirst();
            if (next.equals(candidate.predecessorTaskId())) {
                return true;
            }
            if (visited.add(next)) {
                pending.addAll(outgoing.getOrDefault(next, List.of()));
            }
        }
        return false;
    }

    private void lockProject(long projectId) {
        TeamMember member = account.requireCreator();
        projects.requireWritable(projectId, member);
        if (projectRows.lock(projectId) == null) {
            throw new ApiException(404, "项目不存在");
        }
    }

    private TaskItem requireProjectTask(long projectId, Long id) {
        TaskItem task = id == null ? null : taskRows.selectById(id);
        if (task == null || !task.getProjectId().equals(projectId)) {
            throw new ApiException(400, "依赖两端必须是当前项目的任务");
        }
        return task;
    }

    public record Timeline(List<ProjectService.StageView> stages, List<TaskService.TaskView> tasks,
                           List<PlanningMapper.Plan> milestones, List<TaskDependencyMapper.Dependency> dependencies,
                           List<PlanningController.Entry> plans) {}
}
