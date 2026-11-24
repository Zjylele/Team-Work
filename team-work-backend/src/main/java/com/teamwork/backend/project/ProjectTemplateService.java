package com.teamwork.backend.project;

import com.teamwork.backend.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectTemplateService {
    private final JdbcTemplate jdbc;
    private static final List<Template> TEMPLATES = List.of(
            new Template(1, "通用项目", "适合日常协作和行动跟踪"),
            new Template(2, "研发交付", "按研发交付阶段管理任务"));

    public ProjectTemplateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Template> list() {
        return TEMPLATES;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyTemplate(long projectId, long templateId) {
        if (TEMPLATES.stream().noneMatch(template -> template.id() == templateId)) {
            throw new ApiException(400, "无效的项目模板");
        }
        jdbc.update("update project set template_id = ?, template_version = 1 where id = ?", templateId, projectId);
        List<String> stages = templateId == 1 ? List.of("待办", "进行中", "已完成")
                : List.of("需求分析", "开发", "验证", "交付");
        jdbc.update("delete from stage where project_id = ?", projectId);
        for (int i = 0; i < stages.size(); i++) {
            jdbc.update("insert into stage (project_id, name, sort_order) values (?, ?, ?)", projectId, stages.get(i), i + 1);
        }
        jdbc.update("""
                insert into project_view (project_id, owner_id, name, scope, config_json)
                select id, owner_id, ?, 'SHARED', ? from project where id = ?
                """, templateId == 1 ? "任务列表" : "交付看板",
                templateId == 1 ? "{\"mode\":\"list\"}" : "{\"mode\":\"stage\"}", projectId);
        if (templateId == 2) {
            jdbc.update("""
                    insert into project_field (project_id, name, field_type, options_json, is_required, is_visible, sort_order)
                    values (?, '交付版本', 'TEXT', '[]', false, true, 1)
                    """, projectId);
        }
    }

    public record Template(long id, String name, String description) {}
}
