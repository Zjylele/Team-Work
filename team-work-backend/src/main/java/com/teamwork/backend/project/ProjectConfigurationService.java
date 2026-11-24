package com.teamwork.backend.project;

import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.task.TaskItem;
import com.teamwork.backend.task.TaskMapper;
import com.teamwork.backend.team.TeamMember;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ProjectConfigurationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProjectService projects;
    private final TaskMapper tasks;

    public ProjectConfigurationService(JdbcTemplate jdbc, ObjectMapper json, ProjectService projects, TaskMapper tasks) {
        this.jdbc = jdbc;
        this.json = json;
        this.projects = projects;
        this.tasks = tasks;
    }

    public List<SavedView> views(long projectId, TeamMember actor) {
        projects.requireAccessible(projectId, actor);
        return jdbc.query("""
                select id, owner_id, name, scope, config_json from project_view
                where project_id = ? and (scope = 'SHARED' or owner_id = ?) order by id
                """, (rs, row) -> new SavedView(rs.getLong("id"), rs.getString("name"), rs.getString("scope"),
                rs.getString("config_json"), rs.getLong("owner_id")), projectId, actor.getAccountId());
    }

    @Transactional
    public SavedView createView(long projectId, TeamMember actor, ViewRequest request) {
        Project project = projects.requireAccessible(projectId, actor);
        String scope = scope(request.scope());
        if ("SHARED".equals(scope)) requireAdmin(project, actor);
        String name = name(request.name());
        String config = config(request.configJson());
        long id = insert("insert into project_view (project_id, owner_id, name, scope, config_json) values (?, ?, ?, ?, ?)",
                projectId, actor.getAccountId(), name, scope, config);
        return new SavedView(id, name, scope, config, actor.getAccountId());
    }

    private SavedView mutableView(long projectId, long viewId, TeamMember actor) {
        Project project = projects.requireAccessible(projectId, actor);
        SavedView view = jdbc.query("""
                select id, owner_id, name, scope, config_json from project_view
                where project_id = ? and id = ? and (scope = 'SHARED' or owner_id = ?) for update
                """, (rs, row) -> new SavedView(rs.getLong("id"), rs.getString("name"), rs.getString("scope"),
                rs.getString("config_json"), rs.getLong("owner_id")), projectId, viewId, actor.getAccountId())
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(404, "视图不存在"));
        if ("SHARED".equals(view.scope())) requireAdmin(project, actor);
        return view;
    }

    @Transactional
    public SavedView updateView(long projectId, long viewId, TeamMember actor, ViewRequest request) {
        SavedView view = mutableView(projectId, viewId, actor);
        String scope = request.scope() == null ? view.scope() : scope(request.scope());
        if ("SHARED".equals(scope)) requireAdmin(projects.requireAccessible(projectId, actor), actor);
        String name = request.name() == null ? view.name() : name(request.name());
        String config = request.configJson() == null ? view.configJson() : config(request.configJson());
        long owner = !scope.equals(view.scope()) && "PERSONAL".equals(scope) ? actor.getAccountId() : view.ownerId();
        jdbc.update("update project_view set name = ?, scope = ?, config_json = ?, owner_id = ? where project_id = ? and id = ?",
                name, scope, config, owner, projectId, viewId);
        return new SavedView(viewId, name, scope, config, owner);
    }

    @Transactional
    public void deleteView(long projectId, long viewId, TeamMember actor) {
        mutableView(projectId, viewId, actor);
        jdbc.update("delete from project_view where project_id = ? and id = ?", projectId, viewId);
    }

    public List<Field> fields(long projectId, TeamMember actor) {
        projects.requireAccessible(projectId, actor);
        return fields(projectId);
    }

    private List<Field> fields(long projectId) {
        return jdbc.query("select * from project_field where project_id = ? order by sort_order, id",
                (rs, row) -> new Field(rs.getLong("id"), rs.getString("name"), rs.getString("field_type"),
                        json.readValue(rs.getString("options_json"), new TypeReference<List<Option>>() {}),
                        rs.getBoolean("is_required"), rs.getBoolean("is_visible"), rs.getInt("sort_order")), projectId);
    }

    @Transactional
    public Field addField(long projectId, TeamMember actor, FieldRequest request) {
        requireAdmin(projects.requireAccessible(projectId, actor), actor);
        jdbc.queryForObject("select id from project where id = ? for update", Long.class, projectId);
        String name = name(request.name());
        String type = request.type();
        if (type == null || !Set.of("TEXT", "SINGLE_SELECT", "DATE").contains(type)) throw bad("无效字段类型");
        List<Option> options = new ArrayList<>();
        if ("SINGLE_SELECT".equals(type)) {
            if (request.options() == null || request.options().isEmpty() || request.options().size() > 50) {
                throw bad("单选字段需要 1 到 50 个选项");
            }
            Set<String> labels = new HashSet<>();
            for (OptionRequest option : request.options()) {
                String label = name(option == null ? null : option.label());
                if (!labels.add(label)) throw bad("选项不可重复");
                options.add(new Option(options.size() + 1, label));
            }
        } else if (request.options() != null && !request.options().isEmpty()) {
            throw bad("只有单选字段支持选项");
        }
        if (jdbc.queryForObject("select count(*) from project_field where project_id = ? and name = ?",
                Integer.class, projectId, name) > 0) throw bad("字段名称已存在");
        int order = jdbc.queryForObject("select coalesce(max(sort_order), 0) + 1 from project_field where project_id = ?",
                Integer.class, projectId);
        boolean required = Boolean.TRUE.equals(request.required());
        boolean visible = !Boolean.FALSE.equals(request.visible());
        long id = insert("""
                insert into project_field (project_id, name, field_type, options_json, is_required, is_visible, sort_order)
                values (?, ?, ?, ?, ?, ?, ?)
                """, projectId, name, type, json.writeValueAsString(options), required, visible, order);
        return new Field(id, name, type, options, required, visible, order);
    }

    private TaskItem task(long id, TeamMember actor) {
        TaskItem task = tasks.selectById(id);
        if (task == null) throw new ApiException(404, "任务不存在");
        projects.requireAccessible(task.getProjectId(), actor);
        return task;
    }

    public Map<String, String> values(long id, TeamMember actor) {
        TaskItem task = task(id, actor);
        return values(id, fields(task.getProjectId()));
    }

    private Map<String, String> values(long id, List<Field> fields) {
        Map<Long, String> stored = jdbc.query("select field_id, field_value from task_field_value where task_id = ?",
                rs -> {
                    Map<Long, String> rows = new HashMap<>();
                    while (rs.next()) rows.put(rs.getLong(1), rs.getString(2));
                    return rows;
                }, id);
        Map<String, String> result = new LinkedHashMap<>();
        for (Field field : fields) {
            String value = stored.get(field.id());
            if (!field.visible() || value == null) continue;
            if ("SINGLE_SELECT".equals(field.type())) {
                String storedId = value;
                value = field.options().stream().filter(o -> Long.toString(o.id()).equals(storedId))
                        .map(Option::label).findFirst().orElse(null);
            }
            if (value != null) result.put(Long.toString(field.id()), value);
        }
        return result;
    }

    @Transactional
    public Map<String, String> saveValues(long id, TeamMember actor, Map<String, String> values) {
        TaskItem task = task(id, actor);
        projects.requireWritable(task.getProjectId(), actor);
        Project project = projects.requireAccessible(task.getProjectId(), actor);
        if (!Objects.equals(task.getAssigneeId(), actor.getAccountId())) requireAdmin(project, actor);
        if (values == null) throw bad("缺少字段值");
        jdbc.queryForObject("select id from task where id = ? for update", Long.class, id);
        List<Field> fields = fields(task.getProjectId());
        Map<String, Field> allowed = new HashMap<>();
        fields.stream().filter(Field::visible).forEach(field -> allowed.put(Long.toString(field.id()), field));
        if (!allowed.keySet().containsAll(values.keySet())) throw bad("字段不属于本项目或不可见");
        for (Map.Entry<String, Field> entry : allowed.entrySet()) {
            Field field = entry.getValue();
            String input = values.get(entry.getKey());
            if (input == null || input.isBlank()) {
                if (field.required()) throw bad("请填写必填字段：" + field.name());
                jdbc.update("delete from task_field_value where task_id = ? and field_id = ?", id, field.id());
                continue;
            }
            String normalized = switch (field.type()) {
                case "SINGLE_SELECT" -> field.options().stream().filter(o -> o.label().equals(input))
                        .map(o -> Long.toString(o.id())).findFirst().orElseThrow(() -> bad("无效的单选值"));
                case "DATE" -> date(input);
                default -> input;
            };
            if (normalized.length() > 2000) throw bad("字段值最长 2000 个字符");
            jdbc.update("""
                    insert into task_field_value (task_id, field_id, field_value) values (?, ?, ?)
                    on duplicate key update field_value = ?
                    """, id, field.id(), normalized, normalized);
        }
        return values(id, fields);
    }

    private static String date(String input) {
        try {
            if (!input.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw bad("日期格式必须为 YYYY-MM-DD");
            return LocalDate.parse(input).toString();
        } catch (DateTimeParseException ex) {
            throw bad("日期无效");
        }
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private void requireAdmin(Project project, TeamMember actor) {
        if (!Set.of("OWNER", "ADMIN").contains(projects.role(project, actor))) {
            throw new ApiException(403, "需要项目管理员权限");
        }
    }

    private static String scope(String scope) {
        if (!"PERSONAL".equals(scope) && !"SHARED".equals(scope)) throw bad("无效的视图范围");
        return scope;
    }

    private static String name(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 64) throw bad("名称必须为 1 到 64 个字符");
        return name.strip();
    }

    private String config(String input) {
        if (input == null || input.length() > 4096) throw bad("无效的视图配置");
        try {
            var tree = json.readTree(input);
            if (tree == null || !tree.isObject()) throw bad("无效的视图配置");
            tree.properties().forEach(entry -> {
                if (!entry.getValue().isTextual()) throw bad("无效的视图筛选值");
                String value = entry.getValue().asText();
                boolean allowed = switch (entry.getKey()) {
                    case "mode" -> Set.of("list", "board", "stage").contains(value);
                    case "search" -> value.length() <= 128;
                    case "status" -> Set.of("", "TODO", "DOING", "REVIEW", "DONE").contains(value);
                    case "priority" -> Set.of("", "NONE", "LOW", "MEDIUM", "HIGH", "URGENT").contains(value);
                    default -> false;
                };
                if (!allowed) throw bad("无效的视图筛选配置");
            });
            return json.writeValueAsString(tree);
        } catch (tools.jackson.core.JacksonException ex) {
            throw bad("无效的视图配置");
        }
    }

    private static ApiException bad(String message) { return new ApiException(400, message); }

    public record SavedView(long id, String name, String scope, String configJson, long ownerId) {}
    public record ViewRequest(String name, String scope, String configJson) {}
    public record Field(long id, String name, String type, List<Option> options, boolean required, boolean visible, int sortOrder) {}
    public record Option(long id, String label) {}
    public record OptionRequest(String label) {}
    public record FieldRequest(String name, String type, List<OptionRequest> options, Boolean required, Boolean visible) {}
}
