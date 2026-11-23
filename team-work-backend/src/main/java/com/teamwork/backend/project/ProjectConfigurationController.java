package com.teamwork.backend.project;

import com.teamwork.backend.common.CurrentAccount;
import com.teamwork.backend.project.ProjectConfigurationService.Field;
import com.teamwork.backend.project.ProjectConfigurationService.FieldRequest;
import com.teamwork.backend.project.ProjectConfigurationService.SavedView;
import com.teamwork.backend.project.ProjectConfigurationService.ViewRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ProjectConfigurationController {
    private final CurrentAccount account;
    private final ProjectConfigurationService config;
    private final ProjectTemplateService templates;

    public ProjectConfigurationController(CurrentAccount account, ProjectConfigurationService config,
                                          ProjectTemplateService templates) {
        this.account = account;
        this.config = config;
        this.templates = templates;
    }

    @GetMapping("/api/project-templates")
    public List<ProjectTemplateService.Template> templates() {
        account.requireMember();
        return templates.list();
    }

    @GetMapping("/api/projects/{projectId}/views")
    public List<SavedView> views(@PathVariable long projectId) {
        return config.views(projectId, account.requireMember());
    }

    @PostMapping("/api/projects/{projectId}/views")
    public SavedView createView(@PathVariable long projectId, @RequestBody ViewRequest request) {
        return config.createView(projectId, account.requireMember(), request);
    }

    @PatchMapping("/api/projects/{projectId}/views/{viewId}")
    public SavedView updateView(@PathVariable long projectId, @PathVariable long viewId, @RequestBody ViewRequest request) {
        return config.updateView(projectId, viewId, account.requireMember(), request);
    }

    @DeleteMapping("/api/projects/{projectId}/views/{viewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteView(@PathVariable long projectId, @PathVariable long viewId) {
        config.deleteView(projectId, viewId, account.requireMember());
    }

    @GetMapping("/api/projects/{projectId}/fields")
    public List<Field> fields(@PathVariable long projectId) {
        return config.fields(projectId, account.requireMember());
    }

    @PostMapping("/api/projects/{projectId}/fields")
    public Field addField(@PathVariable long projectId, @RequestBody FieldRequest request) {
        return config.addField(projectId, account.requireMember(), request);
    }

    @GetMapping("/api/tasks/{taskId}/fields")
    public Map<String, String> taskFields(@PathVariable long taskId) {
        return config.values(taskId, account.requireMember());
    }

    @PutMapping("/api/tasks/{taskId}/fields")
    public Map<String, String> saveTaskFields(@PathVariable long taskId, @RequestBody ValuesRequest request) {
        return config.saveValues(taskId, account.requireMember(), request.values());
    }

    public record ValuesRequest(Map<String, String> values) {}
}
