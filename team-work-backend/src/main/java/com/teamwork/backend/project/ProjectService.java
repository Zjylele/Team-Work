package com.teamwork.backend.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.task.TaskItem;
import com.teamwork.backend.task.TaskMapper;
import com.teamwork.backend.team.TeamMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectMapper projects;
    private final StageMapper stages;
    private final TaskMapper tasks;

    public ProjectService(ProjectMapper projects, StageMapper stages, TaskMapper tasks) {
        this.projects = projects;
        this.stages = stages;
        this.tasks = tasks;
    }

    public List<ProjectView> list(TeamMember member) {
        return projects.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getTeamId, member.getTeamId())
                        .orderByAsc(Project::getId))
                .stream()
                .map(ProjectView::from)
                .toList();
    }

    @Transactional
    public ProjectView create(TeamMember creator, String name) {
        Project project = new Project();
        project.setTeamId(creator.getTeamId());
        project.setName(name);
        project.setCreatedAt(LocalDateTime.now());
        projects.insert(project);
        return ProjectView.from(project);
    }

    public Project requireInTeam(Long projectId, Long teamId) {
        Project project = projects.selectById(projectId);
        if (project == null || !project.getTeamId().equals(teamId)) {
            throw new ApiException(404, "项目不存在");
        }
        return project;
    }

    public List<StageView> stages(Project project) {
        return stages.selectList(new LambdaQueryWrapper<Stage>()
                        .eq(Stage::getProjectId, project.getId())
                        .orderByAsc(Stage::getSortOrder)
                        .orderByAsc(Stage::getId))
                .stream()
                .map(StageView::from)
                .toList();
    }

    @Transactional
    public StageView addStage(TeamMember creator, Long projectId, String name) {
        Project project = requireInTeam(projectId, creator.getTeamId());
        Integer maxOrder = stages.selectList(new LambdaQueryWrapper<Stage>().eq(Stage::getProjectId, project.getId()))
                .stream()
                .map(Stage::getSortOrder)
                .max(Integer::compareTo)
                .orElse(0);
        Stage stage = new Stage();
        stage.setProjectId(project.getId());
        stage.setName(name);
        stage.setSortOrder(maxOrder + 1);
        stages.insert(stage);
        return StageView.from(stage);
    }

    @Transactional
    public StageView updateStage(TeamMember creator, Long stageId, String name, Integer sortOrder) {
        Stage stage = requireStage(creator, stageId);
        if (name == null && sortOrder == null) {
            throw new ApiException(400, "请提供阶段名称或顺序");
        }
        if (name != null) {
            if (name.isBlank() || name.length() > 64) {
                throw new ApiException(400, "阶段名称最长 64 个字符");
            }
            stage.setName(name);
        }
        if (sortOrder != null) {
            stage.setSortOrder(sortOrder);
        }
        stages.updateById(stage);
        return StageView.from(stage);
    }

    @Transactional
    public void deleteStage(TeamMember creator, Long stageId) {
        Stage stage = requireStage(creator, stageId);
        Long taskCount = tasks.selectCount(new LambdaQueryWrapper<TaskItem>().eq(TaskItem::getStageId, stage.getId()));
        if (taskCount != null && taskCount > 0) {
            throw new ApiException(409, "阶段下还有任务，不能删除");
        }
        stages.deleteById(stage.getId());
    }

    public Stage requireStage(TeamMember member, Long stageId) {
        Stage stage = stages.selectById(stageId);
        if (stage == null) {
            throw new ApiException(404, "阶段不存在");
        }
        requireInTeam(stage.getProjectId(), member.getTeamId());
        return stage;
    }

    public record ProjectView(Long id, String name) {
        static ProjectView from(Project project) {
            return new ProjectView(project.getId(), project.getName());
        }
    }

    public record StageView(Long id, Long projectId, String name, Integer sortOrder) {
        static StageView from(Stage stage) {
            return new StageView(stage.getId(), stage.getProjectId(), stage.getName(), stage.getSortOrder());
        }
    }
}
