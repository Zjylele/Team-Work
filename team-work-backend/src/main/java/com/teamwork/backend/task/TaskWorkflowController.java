package com.teamwork.backend.task;

import com.teamwork.backend.common.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TaskWorkflowController {
    private final CurrentAccount account;
    private final TaskWorkflow workflow;
    private final TaskService tasks;

    TaskWorkflowController(CurrentAccount account, TaskWorkflow workflow, TaskService tasks) {
        this.account = account;
        this.workflow = workflow;
        this.tasks = tasks;
    }

    @GetMapping("/api/projects/{projectId}/workflow")
    public TaskWorkflow.Config get(@PathVariable Long projectId) {
        return workflow.get(account.requireMember(), projectId);
    }

    @PutMapping("/api/projects/{projectId}/workflow")
    public TaskWorkflow.Config save(@PathVariable Long projectId, @Valid @RequestBody ConfigRequest request) {
        return workflow.save(account.requireMember(), projectId, new TaskWorkflow.Config(request.approverId(),
                request.assignmentReminder(), request.statusReminder(), request.dueReminder()));
    }

    @GetMapping("/api/tasks/{taskId}/approvals")
    public List<TaskWorkflow.Approval> approvals(@PathVariable Long taskId) {
        return tasks.approvals(account.requireMember(), taskId);
    }

    @PostMapping("/api/tasks/{taskId}/approvals/{approvalId}/decision")
    public TaskService.TaskView decide(@PathVariable Long taskId, @PathVariable Long approvalId,
                                       @Valid @RequestBody DecisionRequest request) {
        return tasks.decide(account.requireMember(), taskId, approvalId, request.approved(), request.comment());
    }

    @PostMapping("/api/tasks/{taskId}/approvals/{approvalId}/reassign")
    public TaskService.TaskView reassign(@PathVariable Long taskId, @PathVariable Long approvalId) {
        return tasks.reassignApproval(account.requireMember(), taskId, approvalId);
    }

    public record ConfigRequest(Long approverId, @NotNull Boolean assignmentReminder, @NotNull Boolean statusReminder,
                                @NotNull Boolean dueReminder) {}
    public record DecisionRequest(@NotNull Boolean approved, @Size(max = 1000) String comment) {}
}
