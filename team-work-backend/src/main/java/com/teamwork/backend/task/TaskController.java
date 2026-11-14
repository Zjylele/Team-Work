package com.teamwork.backend.task;

import com.teamwork.backend.common.CurrentAccount;
import com.teamwork.backend.team.TeamMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TaskController {

    private final TaskService taskService;
    private final CurrentAccount currentAccount;

    public TaskController(TaskService taskService, CurrentAccount currentAccount) {
        this.taskService = taskService;
        this.currentAccount = currentAccount;
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    public List<TaskService.TaskView> list(@PathVariable Long projectId) {
        return taskService.list(currentAccount.requireMember(), projectId);
    }

    @PostMapping("/api/projects/{projectId}/tasks")
    public TaskService.TaskView create(@PathVariable Long projectId, @Valid @RequestBody TaskRequest request) {
        return taskService.create(currentAccount.requireCreator(), projectId, request.title(), request.description(),
                request.assigneeId(), request.stageId(), request.priority(), request.dueDate());
    }

    @GetMapping("/api/tasks/{taskId}")
    public TaskService.TaskView get(@PathVariable Long taskId) {
        return taskService.get(currentAccount.requireMember(), taskId);
    }

    @DeleteMapping("/api/tasks/{taskId}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long taskId) {
        taskService.delete(currentAccount.requireCreator(), taskId);
    }

    @PatchMapping("/api/tasks/{taskId}")
    public TaskService.TaskView update(@PathVariable Long taskId, @RequestBody TaskUpdate request) {
        return taskService.update(currentAccount.requireMember(), taskId, request.title(), request.description(),
                request.assigneeId(), request.stageId(), request.priority(), request.dueDate());
    }

    @PostMapping("/api/tasks/{taskId}/start")
    public TaskService.TaskView start(@PathVariable Long taskId) {
        return taskService.start(currentAccount.requireMember(), taskId);
    }

    @PostMapping("/api/tasks/{taskId}/complete")
    public TaskService.TaskView complete(@PathVariable Long taskId) {
        return taskService.complete(currentAccount.requireMember(), taskId);
    }

    @PutMapping("/api/tasks/{taskId}/status")
    public TaskService.TaskView correct(@PathVariable Long taskId, @Valid @RequestBody StatusRequest request) {
        return taskService.correct(currentAccount.requireCreator(), taskId, request.status());
    }

    @GetMapping("/api/tasks/{taskId}/comments")
    public List<TaskService.CommentView> comments(@PathVariable Long taskId) {
        return taskService.comments(currentAccount.requireMember(), taskId);
    }

    @PostMapping("/api/tasks/{taskId}/comments")
    public TaskService.CommentView comment(@PathVariable Long taskId, @Valid @RequestBody CommentRequest request) {
        return taskService.comment(currentAccount.requireMember(), taskId, request.content());
    }

    public record TaskRequest(
            @NotBlank(message = "请填写标题")
            @Size(max = 128, message = "标题最长 128 个字符")
            String title,
            @Size(max = 2000, message = "说明最长 2000 个字符")
            String description,
            @NotNull(message = "请指定负责人") Long assigneeId,
            @NotNull(message = "请指定阶段") Long stageId, String priority, String dueDate
    ) {
    }

    public record TaskUpdate(String title, String description, Long assigneeId, Long stageId, String priority, String dueDate) {
    }

    public record StatusRequest(@NotBlank(message = "请填写状态") String status) {
    }

    public record CommentRequest(
            @NotBlank(message = "请填写评论")
            @Size(max = 2000, message = "评论最长 2000 个字符")
            String content
    ) {
    }
}
