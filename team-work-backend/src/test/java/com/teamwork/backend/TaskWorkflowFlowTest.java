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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskWorkflowFlowTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.teamwork.backend.task.TaskAutomationDelivery delivery;

    @Test
    void enforcesReviewAndDeduplicatesDecisionsAndReminders() throws Exception {
        var owner = register("p3workflowowner");
        var member = register("p3workflowassignee");
        postJson(owner, "/api/team", "{\"name\":\"P3 Review\"}").andExpect(status().isOk());
        String invite = id(mvc.perform(get("/api/team").session(owner)), "$.inviteCode");
        postJson(member, "/api/team/join", "{\"code\":\"%s\"}".formatted(invite)).andExpect(status().isOk());
        String assignee = id(mvc.perform(get("/api/auth/me").session(member)), "$.id");
        String approver = id(mvc.perform(get("/api/auth/me").session(owner)), "$.id");
        String project = "/api/projects/" + id(postJson(owner, "/api/projects", "{\"name\":\"Review project\"}"), "$.id");
        String stage = id(postJson(owner, project + "/stages", "{\"name\":\"Development\"}"), "$.id");
        String config = """
                {"approverId":%s,"assignmentReminder":true,"statusReminder":true,"dueReminder":true}
                """.formatted(approver);
        mvc.perform(put(project + "/workflow").session(member).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(config))
                .andExpect(status().isForbidden());
        mvc.perform(put(project + "/workflow").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(config))
                .andExpect(status().isOk());
        String taskId = id(postJson(owner, project + "/tasks", """
                {"title":"Review task","stageId":%s,"assigneeId":%s,"dueDate":"2020-01-01"}
                """.formatted(stage, assignee)), "$.id");
        String task = "/api/tasks/" + taskId;
        postJson(member, task + "/start", "{}").andExpect(status().isOk());
        mvc.perform(put(task + "/status").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}")).andExpect(status().isConflict());
        postJson(member, task + "/complete", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEW"));
        postJson(member, task + "/complete", "{}").andExpect(status().isConflict());
        var history = mvc.perform(get(task + "/approvals").session(member)).andExpect(jsonPath("$", hasSize(1)));
        String approval = id(history, "$[0].id");
        String decision = task + "/approvals/" + approval + "/decision";
        postJson(member, decision, "{\"approved\":true}").andExpect(status().isForbidden());

        delivery.enqueueDueReminders();
        delivery.enqueueDueReminders();
        delivery.deliver();
        delivery.deliver();
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE task_id=? AND type='TASK_DUE_REMINDER'", Integer.class, taskId));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE task_id=? AND type='TASK_REVIEW_REQUESTED'", Integer.class, taskId));

        postJson(owner, decision, "{\"approved\":false,\"comment\":\"Try again\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DOING"));
        postJson(member, task + "/complete", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEW"));
        String next = id(mvc.perform(get(task + "/approvals").session(owner)).andExpect(jsonPath("$", hasSize(2))), "$[0].id");
        var approve = task + "/approvals/" + next + "/decision";
        postJson(owner, approve, "{\"approved\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        postJson(owner, approve, "{\"approved\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        postJson(owner, approve, "{\"approved\":false}").andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM approval_decision d JOIN approval_instance a ON a.id=d.approval_id WHERE a.task_id=?
                """, Integer.class, taskId));
    }

    @Test
    void ownerCanRecoverPendingApprovalAfterReviewerBecomesReadOnly() throws Exception {
        var owner = register("recoveryowner");
        var reviewer = register("recoveryreviewer");
        postJson(owner, "/api/team", "{\"name\":\"Recovery\"}").andExpect(status().isOk());
        String invite = id(mvc.perform(get("/api/team").session(owner)), "$.inviteCode");
        postJson(reviewer, "/api/team/join", "{\"code\":\"%s\"}".formatted(invite)).andExpect(status().isOk());
        String reviewerId = id(mvc.perform(get("/api/auth/me").session(reviewer)), "$.id");
        String ownerId = id(mvc.perform(get("/api/auth/me").session(owner)), "$.id");
        String projectId = id(postJson(owner, "/api/projects", "{\"name\":\"Recovery project\"}"), "$.id");
        String project = "/api/projects/" + projectId;
        String stage = id(postJson(owner, project + "/stages", "{\"name\":\"Development\"}"), "$.id");
        String config = """
                {"approverId":%s,"assignmentReminder":false,"statusReminder":false,"dueReminder":false}
                """;
        mvc.perform(put(project + "/workflow").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(config.formatted(reviewerId))).andExpect(status().isOk());
        String task = "/api/tasks/" + id(postJson(owner, project + "/tasks", """
                {"title":"Recoverable approval","stageId":%s,"assigneeId":%s}
                """.formatted(stage, ownerId)), "$.id");
        postJson(owner, task + "/start", "{}").andExpect(status().isOk());
        postJson(owner, task + "/complete", "{}").andExpect(status().isOk());
        String approval = task + "/approvals/" + id(mvc.perform(get(task + "/approvals").session(owner)), "$[0].id");
        jdbc.update("INSERT INTO project_member(project_id, account_id, role) VALUES (?, ?, 'VIEWER')", projectId, reviewerId);
        postJson(reviewer, approval + "/decision", "{\"approved\":true}").andExpect(status().isForbidden());
        postJson(reviewer, approval + "/reassign", "{}").andExpect(status().isForbidden());
        mvc.perform(put(project + "/workflow").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(config.formatted(ownerId))).andExpect(status().isOk());
        postJson(owner, approval + "/reassign", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEW"));
        postJson(owner, approval + "/reassign", "{}").andExpect(status().isOk());
        mvc.perform(get(task + "/activity").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='APPROVAL_REASSIGNED')]", hasSize(1)));
        postJson(owner, approval + "/decision", "{\"approved\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
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
