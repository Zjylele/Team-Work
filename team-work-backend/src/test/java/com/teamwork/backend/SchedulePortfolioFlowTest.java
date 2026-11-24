package com.teamwork.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SchedulePortfolioFlowTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsDatesRejectsCyclesAndKeepsPortfolioDrilldownsPermissionScoped() throws Exception {
        MockHttpSession owner = register("p2scheduleowner");
        MockHttpSession viewer = register("p2scheduleviewer");
        id(postJson(owner, "/api/team", """
                {"name":"P2 团队"}
                """), "$.id");
        String invite = id(mvc.perform(get("/api/team").session(owner)), "$.inviteCode");
        postJson(viewer, "/api/team/join", """
                {"code":"%s"}
                """.formatted(invite)).andExpect(status().isOk());
        String assignee = id(mvc.perform(get("/api/auth/me").session(viewer)), "$.id");
        String privateId = id(postJson(owner, "/api/projects", """
                {"name":"私有项目","visibility":"PRIVATE"}
                """), "$.id");
        String sharedId = id(postJson(owner, "/api/projects", """
                {"name":"可见项目"}
                """), "$.id");
        String privateUrl = "/api/projects/" + privateId;
        String sharedUrl = "/api/projects/" + sharedId;
        String privateStage = id(postJson(owner, privateUrl + "/stages", """
                {"name":"开发"}
                """), "$.id");
        String sharedStage = id(postJson(owner, sharedUrl + "/stages", """
                {"name":"开发"}
                """), "$.id");
        String ownerId = id(mvc.perform(get("/api/auth/me").session(owner)), "$.id");
        createTask(owner, privateUrl, privateStage, ownerId, "私有任务");
        String first = createTask(owner, sharedUrl, sharedStage, assignee, "前置任务");
        String last = createTask(owner, sharedUrl, sharedStage, assignee, "后续任务");
        mvc.perform(patch("/api/tasks/" + first).session(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"startDate":"2026-12-03","dueDate":"2026-12-02"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/tasks/" + first).session(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"startDate":"2026-12-01","dueDate":"2026-12-02"}
                                """))
                .andExpect(status().isOk());
        String edges = sharedUrl + "/dependencies";
        postJson(owner, edges, dependency(first, last)).andExpect(status().isOk());
        postJson(owner, edges, dependency(last, first)).andExpect(status().isConflict());
        postJson(viewer, "/api/tasks/" + last + "/start", "{}").andExpect(status().isConflict());
        mvc.perform(put("/api/tasks/" + first + "/status").session(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"status":"DONE"}
                                """))
                .andExpect(status().isOk());
        postJson(viewer, "/api/tasks/" + last + "/start", "{}").andExpect(status().isOk());
        mvc.perform(get("/api/team/calendar-context").session(viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.zoneId").value("Australia/Perth"));
        postJson(owner, sharedUrl + "/planning", """
                {"kind":"MILESTONE","name":"验收","description":"","endDate":"2026-12-04"}
                """).andExpect(status().isOk());
        mvc.perform(get(sharedUrl + "/timeline").session(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].startDate").value("2026-12-01"))
                .andExpect(jsonPath("$.dependencies", hasSize(1)))
                .andExpect(jsonPath("$.milestones[0].name").value("验收"));

        String portfolio = "/api/portfolios/" + id(postJson(owner, "/api/portfolios", """
                {"name":"项目集","projectIds":[%s,%s]}
                """.formatted(privateId, sharedId)), "$.id");
        mvc.perform(get(portfolio + "/summary").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
        mvc.perform(get(portfolio + "/summary").session(viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.completedThisWeek").value(1));
        mvc.perform(get(portfolio + "/tasks").param("metric", "completedThisWeek").session(viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("前置任务"))
                .andExpect(jsonPath("$[0].projectName").value("可见项目"));
        mvc.perform(get(portfolio + "/tasks").param("metric", "memberLoad").param("accountId", assignee)
                        .param("bucket", "withoutDueDate").session(viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("后续任务"));

        jdbc.update("UPDATE project SET owner_id = ? WHERE id = ?", assignee, privateId);
        mvc.perform(put(portfolio).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"更新的项目集","projectIds":[%s]}
                                """.formatted(sharedId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectIds", hasSize(1)));
        jdbc.update("UPDATE project SET owner_id = ? WHERE id = ?", ownerId, privateId);
        mvc.perform(get(portfolio + "/summary").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
    }

    private String createTask(MockHttpSession session, String url, String stage, String assignee, String title) throws Exception {
        return id(postJson(session, url + "/tasks", """
                {"title":"%s","stageId":%s,"assigneeId":%s}
                """.formatted(title, stage, assignee)), "$.id");
    }

    private String dependency(String predecessor, String successor) {
        return """
                {"predecessorTaskId":%s,"successorTaskId":%s}
                """.formatted(predecessor, successor);
    }

    private MockHttpSession register(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"username":"%s","password":"password1","displayName":"测试用户"}
                                """.formatted(username)))
                .andExpect(status().isOk()).andReturn().getRequest().getSession();
    }

    private ResultActions postJson(MockHttpSession session, String path, String json) throws Exception {
        return mvc.perform(post(path).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String id(ResultActions result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), path).toString();
    }
}
