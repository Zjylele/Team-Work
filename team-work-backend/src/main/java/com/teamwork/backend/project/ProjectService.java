package com.teamwork.backend.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.task.TaskItem;
import com.teamwork.backend.task.TaskMapper;
import com.teamwork.backend.team.TeamMember;
import com.teamwork.backend.team.TeamMemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectMapper projects;
    private final StageMapper stages;
    private final TaskMapper tasks;
    private final ProjectMemberMapper projectMembers;
    private final TeamMemberMapper teamMembers;
    private final ProjectTemplateService templates;

    public ProjectService(ProjectMapper projects, StageMapper stages, TaskMapper tasks, ProjectMemberMapper projectMembers, TeamMemberMapper teamMembers, ProjectTemplateService templates) {
        this.projects = projects;
        this.stages = stages;
        this.tasks = tasks;
        this.projectMembers = projectMembers;
        this.teamMembers = teamMembers;
        this.templates = templates;
    }

    public List<ProjectView> list(TeamMember member) {
        return projects.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getTeamId, member.getTeamId())
                        .orderByAsc(Project::getId))
                .stream()
                .filter(p -> !"PRIVATE".equals(p.getVisibility()) || member.getAccountId().equals(p.getOwnerId()) || projectMembers.role(p.getId(), member.getAccountId()) != null)
                .map(p -> new ProjectView(p.getId(), p.getName(), p.getVisibility(), role(p, member)))
                .toList();
    }

    @Transactional
    public ProjectView rename(TeamMember creator, Long projectId, String name) {
        Project project = requireAccessible(projectId, creator);
        requireWritable(projectId, creator);
        project.setName(name);
        if (project.getVisibility() != null && "PRIVATE".equals(project.getVisibility()) && !creator.getAccountId().equals(project.getOwnerId())) throw new ApiException(403, "无权修改该项目");
        projects.updateById(project);
        return ProjectView.from(project);
    }

    @Transactional
    public void delete(TeamMember creator, Long projectId) {
        requireAccessible(projectId, creator);
        requireWritable(projectId, creator);
        projects.deleteById(projectId);
    }

    @Transactional
    public ProjectView create(TeamMember creator, String name) {
        return create(creator, name, null, "SHARED");
    }

    @Transactional
    public ProjectView create(TeamMember creator, String name, Long templateId, String visibility) {
        if (!"PRIVATE".equals(visibility) && !"SHARED".equals(visibility)) throw new ApiException(400, "visibility 只能是 PRIVATE 或 SHARED");
        Project project = new Project();
        project.setTeamId(creator.getTeamId());
        project.setName(name);
        project.setVisibility(visibility);
        project.setOwnerId(creator.getAccountId());
        project.setCreatedAt(LocalDateTime.now());
        projects.insert(project);
        if (templateId != null) templates.applyTemplate(project.getId(), templateId);
        return ProjectView.from(project);
    }

    public Project requireInTeam(Long projectId, Long teamId) {
        Project project = projects.selectById(projectId);
        if (project == null || !project.getTeamId().equals(teamId)) {
            throw new ApiException(404, "项目不存在");
        }
        return project;
    }

    public Project requireAccessible(Long projectId, TeamMember member) {
        Project p = requireInTeam(projectId, member.getTeamId());
        if ("PRIVATE".equals(p.getVisibility()) && !member.getAccountId().equals(p.getOwnerId()) && projectMembers.role(projectId, member.getAccountId()) == null) throw new ApiException(404, "项目不存在");
        return p;
    }

    @Transactional
    public ProjectView updateVisibility(TeamMember actor, Long projectId, String visibility) {
        if (!"PRIVATE".equals(visibility) && !"SHARED".equals(visibility)) throw new ApiException(400, "visibility 只能是 PRIVATE 或 SHARED");
        Project p = requireInTeam(projectId, actor.getTeamId());
        if (!actor.getAccountId().equals(p.getOwnerId()) && !TeamMember.CREATOR.equals(actor.getRole())) throw new ApiException(403, "无权修改项目可见性");
        p.setVisibility(visibility); projects.updateById(p);
        return new ProjectView(p.getId(), p.getName(), p.getVisibility(), actor.getAccountId().equals(p.getOwnerId()) ? "OWNER" : "ADMIN");
    }

    @Transactional
    public void setMemberRole(TeamMember actor, Long projectId, Long accountId, String role) {
        Project p = requireInTeam(projectId, actor.getTeamId());
        if (!actor.getAccountId().equals(p.getOwnerId())) throw new ApiException(403, "仅项目所有者可管理项目成员");
        if (accountId.equals(p.getOwnerId())) throw new ApiException(400, "不能修改项目所有者角色");
        if (!List.of("ADMIN", "MEMBER", "VIEWER").contains(role)) throw new ApiException(400, "无效的项目角色");
        if (teamMembers.selectOne(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, p.getTeamId()).eq(TeamMember::getAccountId, accountId)) == null) throw new ApiException(400, "成员必须属于该团队");
        projectMembers.put(projectId, accountId, role);
    }

    public List<ProjectMemberMapper.Member> members(TeamMember actor, long projectId) {
        requireAccessible(projectId, actor);
        Project p = requireInTeam(projectId, actor.getTeamId());
        List<ProjectMemberMapper.Member> rows = new java.util.ArrayList<>(projectMembers.list(projectId));
        if (rows.stream().noneMatch(m -> java.util.Objects.equals(m.accountId(), p.getOwnerId()))) rows.add(new ProjectMemberMapper.Member(p.getOwnerId(), "项目所有者", "OWNER"));
        return rows;
    }

    public String role(Project p, TeamMember member) {
        if (member.getAccountId().equals(p.getOwnerId())) return "OWNER";
        String role = projectMembers.role(p.getId(), member.getAccountId());
        return role == null ? "MEMBER" : role;
    }

    public void requireWritable(Long projectId, TeamMember member) {
        Project p = requireAccessible(projectId, member);
        if ("VIEWER".equals(role(p, member))) throw new ApiException(403, "只读项目成员不能修改内容");
    }

    public void requireAssignable(Long projectId, Long accountId, Long teamId) {
        Project p = requireInTeam(projectId, teamId);
        TeamMember target = teamMembers.selectOne(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId).eq(TeamMember::getAccountId, accountId));
        if (target == null) throw new ApiException(400, "负责人必须是团队成员");
        if ("PRIVATE".equals(p.getVisibility()) && !accountId.equals(p.getOwnerId()) && projectMembers.role(projectId, accountId) == null) throw new ApiException(400, "负责人必须有权访问该私有项目");
        if ("VIEWER".equals(projectMembers.role(projectId, accountId))) throw new ApiException(400, "只读成员不能被分配任务");
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
        Project project = requireAccessible(projectId, creator);
        requireWritable(projectId, creator);
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
        requireWritable(stage.getProjectId(), creator);
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
        Stage stage = stages.selectOne(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getId, stageId).last("FOR UPDATE"));
        if (stage == null) {
            throw new ApiException(404, "阶段不存在");
        }
        requireInTeam(stage.getProjectId(), creator.getTeamId());
        requireWritable(stage.getProjectId(), creator);
        if (tasks.selectOne(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getStageId, stageId).last("LIMIT 1 FOR UPDATE")) != null) {
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

    public record ProjectView(Long id, String name, String visibility, String role) {
        static ProjectView from(Project project) {
            return new ProjectView(project.getId(), project.getName(), project.getVisibility(), "MEMBER");
        }
    }

    public record StageView(Long id, Long projectId, String name, Integer sortOrder) {
        static StageView from(Stage stage) {
            return new StageView(stage.getId(), stage.getProjectId(), stage.getName(), stage.getSortOrder());
        }
    }
}
