package com.teamwork.backend.project;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teamwork.backend.common.CurrentAccount;
import com.teamwork.backend.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/pages")
public class ProjectPageController {
    private final ProjectPageMapper pages;
    private final ProjectService projects;
    private final CurrentAccount account;

    public ProjectPageController(ProjectPageMapper pages, ProjectService projects, CurrentAccount account) {
        this.pages = pages;
        this.projects = projects;
        this.account = account;
    }

    @GetMapping
    public List<ProjectPage> list(@PathVariable Long projectId) {
        projects.requireInTeam(projectId, account.requireMember().getTeamId());
        return pages.selectList(Wrappers.lambdaQuery(ProjectPage.class)
                .eq(ProjectPage::getProjectId, projectId).orderByAsc(ProjectPage::getId));
    }

    @PostMapping
    public ProjectPage create(@PathVariable Long projectId, @Valid @RequestBody PageRequest body) {
        projects.requireInTeam(projectId, account.requireCreator().getTeamId());
        ProjectPage page = new ProjectPage();
        page.setProjectId(projectId);
        page.setTitle(body.title().strip());
        page.setContent(body.content());
        pages.insert(page);
        return page;
    }

    @PutMapping("/{pageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable Long projectId, @PathVariable Long pageId, @Valid @RequestBody PageRequest body) {
        projects.requireInTeam(projectId, account.requireCreator().getTeamId());
        if (pages.update(Wrappers.lambdaUpdate(ProjectPage.class)
                .eq(ProjectPage::getId, pageId).eq(ProjectPage::getProjectId, projectId)
                .set(ProjectPage::getTitle, body.title().strip()).set(ProjectPage::getContent, body.content())) == 0) {
            throw new ApiException(404, "文档不存在");
        }
    }

    @DeleteMapping("/{pageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long projectId, @PathVariable Long pageId) {
        projects.requireInTeam(projectId, account.requireCreator().getTeamId());
        if (pages.delete(Wrappers.lambdaQuery(ProjectPage.class)
                .eq(ProjectPage::getId, pageId).eq(ProjectPage::getProjectId, projectId)) == 0) {
            throw new ApiException(404, "文档不存在");
        }
    }

    public record PageRequest(@NotBlank @Size(max = 120) String title,
                              @NotNull @Size(max = 16000) String content) {}
}
