package com.teamwork.backend.project;

import com.teamwork.backend.common.CurrentAccount;
import com.teamwork.backend.team.TeamMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ProjectController {

    private final ProjectService projectService;
    private final CurrentAccount currentAccount;

    public ProjectController(ProjectService projectService, CurrentAccount currentAccount) {
        this.projectService = projectService;
        this.currentAccount = currentAccount;
    }

    @GetMapping("/api/projects")
    public List<ProjectService.ProjectView> list() {
        return projectService.list(currentAccount.requireMember());
    }

    @PostMapping("/api/projects")
    public ProjectService.ProjectView create(@Valid @RequestBody NameRequest request) {
        return projectService.create(currentAccount.requireCreator(), request.name());
    }

    @GetMapping("/api/projects/{projectId}/stages")
    public List<ProjectService.StageView> stages(@PathVariable Long projectId) {
        TeamMember member = currentAccount.requireMember();
        return projectService.stages(projectService.requireInTeam(projectId, member.getTeamId()));
    }

    @PostMapping("/api/projects/{projectId}/stages")
    public ProjectService.StageView addStage(@PathVariable Long projectId, @Valid @RequestBody NameRequest request) {
        return projectService.addStage(currentAccount.requireCreator(), projectId, request.name());
    }

    @PatchMapping("/api/stages/{stageId}")
    public ProjectService.StageView updateStage(@PathVariable Long stageId, @RequestBody StageUpdate request) {
        return projectService.updateStage(currentAccount.requireCreator(), stageId, request.name(), request.sortOrder());
    }

    @DeleteMapping("/api/stages/{stageId}")
    public Map<String, String> deleteStage(@PathVariable Long stageId) {
        projectService.deleteStage(currentAccount.requireCreator(), stageId);
        return Map.of("message", "阶段已删除");
    }

    public record NameRequest(
            @NotBlank(message = "请填写名称")
            @Size(max = 64, message = "名称最长 64 个字符")
            String name
    ) {
    }

    public record StageUpdate(String name, Integer sortOrder) {
    }
}
