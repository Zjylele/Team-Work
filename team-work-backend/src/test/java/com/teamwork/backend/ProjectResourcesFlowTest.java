package com.teamwork.backend;

import org.junit.jupiter.api.Test;
import com.teamwork.backend.notice.WebhookChannel;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectResourcesFlowTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void tracksOnlyOwnTimeAndPreservesScheduleSnapshot() throws Exception {
        MockHttpSession owner = register("resourceowner");
        MockHttpSession peer = register("resourcepeer");
        postJson(owner, "/api/team", "{\"name\":\"Resources\"}").andExpect(status().isOk());
        String invite = id(mvc.perform(get("/api/team").session(owner)), "$.inviteCode");
        postJson(peer, "/api/team/join", "{\"code\":\"%s\"}".formatted(invite)).andExpect(status().isOk());
        String ownerId = id(mvc.perform(get("/api/auth/me").session(owner)), "$.id");
        String project = "/api/projects/" + id(postJson(owner, "/api/projects", "{\"name\":\"Resources\"}"), "$.id");
        String stage = id(postJson(owner, project + "/stages", "{\"name\":\"Dev\"}"), "$.id");
        String task = "/api/tasks/" + createTask(owner, project, stage, ownerId, "First");
        String entries = task + "/time-entries";
        postJson(owner, entries, "{\"workDate\":\"2025-01-06\",\"minutes\":0}").andExpect(status().isBadRequest());
        String entry = id(postJson(owner, entries, "{\"workDate\":\"2025-01-06\",\"minutes\":45,\"note\":\"Build\"}")
                .andExpect(status().isCreated()), "$.id");
        mvc.perform(delete(entries + "/" + entry).session(peer).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get(project + "/capacity").param("from","2025-01-06").param("to","2025-01-06").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].loggedMinutes").value(45))
                .andExpect(jsonPath("$[0].capacityMinutes").value(480));
        mvc.perform(patch(task).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2025-01-06\",\"dueDate\":\"2025-01-07\"}")).andExpect(status().isOk());
        String plan = task + "/planned-minutes";
        mvc.perform(put(plan).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedMinutes\":-1}")).andExpect(status().isBadRequest());
        mvc.perform(put(plan).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedMinutes\":90}")).andExpect(status().isOk());
        mvc.perform(get(plan).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedMinutes").value(90));
        mvc.perform(get(project + "/capacity").param("from","2025-01-06").param("to","2025-01-06").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].plannedMinutes").value(90))
                .andExpect(jsonPath("$[0].loggedMinutes").value(45));
        mvc.perform(put(plan).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedMinutes\":null}")).andExpect(status().isOk());
        mvc.perform(get(project + "/capacity").param("from","2025-01-06").param("to","2025-01-06").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].plannedMinutes").value(0));
        String baselines = project + "/baselines";
        postJson(peer, baselines, "{\"name\":\"Initial\"}").andExpect(status().isForbidden());
        String baseline = id(postJson(owner, baselines, "{\"name\":\"Initial\"}").andExpect(status().isCreated()), "$.id");
        mvc.perform(patch(task).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dueDate\":\"2025-01-08\"}")).andExpect(status().isOk());
        mvc.perform(get(baselines + "/" + baseline).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dueDate").value("2025-01-07"));
        mvc.perform(get("/api/notifications/channels").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookEnabled").value(false));
    }

    @Test
    void retriesFailedWebhookWithSameEventIdAndStopsAfterSuccess() throws Exception {
        MockHttpSession session = register("resourcewebhook");
        long accountId = Long.parseLong(id(mvc.perform(get("/api/auth/me").session(session)), "$.id"));
        jdbc.update("INSERT INTO notification_webhook_preference (account_id) VALUES (?)", accountId);
        jdbc.update("INSERT INTO notification_webhook_delivery (id,account_id) VALUES (?,?)",
                "8e22b50b-18c1-47ba-be88-843febad320a", accountId);
        var seen = new java.util.ArrayList<String>();
        var channel = new WebhookChannel(jdbc, null, "https://example.invalid/hooks", "x".repeat(32)) {
            @Override protected boolean send(String id) {
                seen.add(id);
                return seen.size() >= 2;
            }
        };
        channel.drain();
        channel.drain();
        assertEquals(1, seen.size());
        jdbc.update("UPDATE notification_webhook_delivery SET next_attempt=CURRENT_TIMESTAMP WHERE account_id=?", accountId);
        channel.drain();
        assertEquals(2, seen.size());
        assertEquals(seen.get(0), seen.get(1));
        assertEquals("DELIVERED", jdbc.queryForObject(
                "SELECT status FROM notification_webhook_delivery WHERE account_id=?", String.class, accountId));
    }

    private String createTask(MockHttpSession session, String url, String stage, String assignee, String title) throws Exception {
        return id(postJson(session, url + "/tasks", """
                {"title":"%s","stageId":%s,"assigneeId":%s}
                """.formatted(title, stage, assignee)), "$.id");
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
                result.andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString(), path).toString();
    }
}
