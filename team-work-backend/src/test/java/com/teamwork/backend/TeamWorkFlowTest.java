package com.teamwork.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Transactional
class TeamWorkFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void privateProjectsRequireMembershipAndViewerCannotWrite() throws Exception {
        MockHttpSession owner = register("p1owner", "password1", "所有者");
        mockMvc.perform(post("/api/team").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"P1团队\"}"))
                .andExpect(status().isOk());
        String invite = read(mockMvc.perform(get("/api/team").session(owner)).andReturn(), "$.inviteCode");
        MockHttpSession viewer = register("p1viewer", "password1", "只读成员");
        mockMvc.perform(post("/api/team/join").session(viewer).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + invite + "\"}"))
                .andExpect(status().isOk());

        String privateProject = read(mockMvc.perform(post("/api/projects").session(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"私有项目\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk()).andReturn(), "$.id");
        mockMvc.perform(get("/api/projects").session(viewer)).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/projects/" + privateProject + "/tasks").session(viewer)).andExpect(status().isNotFound());

        mockMvc.perform(put("/api/projects/" + privateProject + "/members/" + read(mockMvc.perform(get("/api/auth/me").session(viewer)).andReturn(), "$.id"))
                        .session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/projects").session(viewer)).andExpect(jsonPath("$[0].name").value("私有项目"));
        mockMvc.perform(get("/api/projects/" + privateProject + "/members").session(viewer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/" + privateProject + "/stages").session(viewer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"不允许\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void templateProjectsKeepIndependentViewsAndValidateTaskFields() throws Exception {
        MockHttpSession owner = register("configowner", "password1", "配置所有者");
        mockMvc.perform(post("/api/team").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"配置测试团队\"}"))
                .andExpect(status().isOk());
        String invite = read(mockMvc.perform(get("/api/team").session(owner)).andReturn(), "$.inviteCode");
        MockHttpSession member = register("configmember", "password1", "配置成员");
        mockMvc.perform(post("/api/team/join").session(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + invite + "\"}"))
                .andExpect(status().isOk());
        String memberId = read(mockMvc.perform(get("/api/auth/me").session(member)).andReturn(), "$.id");
        mockMvc.perform(get("/api/project-templates").session(owner)).andExpect(jsonPath("$", hasSize(2)));

        String first = createTemplateProject(owner, "交付甲");
        String second = createTemplateProject(owner, "交付乙");
        String firstProject = "/api/projects/" + first;
        String secondProject = "/api/projects/" + second;
        String stage = read(mockMvc.perform(get(firstProject + "/stages").session(owner))
                .andExpect(jsonPath("$", hasSize(4))).andReturn(), "$[0].id");
        mockMvc.perform(get(secondProject + "/stages").session(owner)).andExpect(jsonPath("$", hasSize(4)));
        mockMvc.perform(get(firstProject + "/fields").session(owner))
                .andExpect(jsonPath("$[0].name").value("交付版本"));

        String personalView = read(mockMvc.perform(post(firstProject + "/views").session(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"自己的列表\",\"scope\":\"PERSONAL\",\"configJson\":\"{\\\"mode\\\":\\\"list\\\",\\\"status\\\":\\\"DOING\\\"}\"}"))
                .andExpect(status().isOk()).andReturn(), "$.id");
        mockMvc.perform(get(firstProject + "/views").session(owner)).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get(firstProject + "/views").session(member)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].scope").value("SHARED"));
        mockMvc.perform(get(secondProject + "/views").session(owner)).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(patch(firstProject + "/views/" + personalView).session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"不允许\"}"))
                .andExpect(status().isNotFound());

        String field = read(mockMvc.perform(post(firstProject + "/fields").session(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"发布渠道\",\"type\":\"SINGLE_SELECT\",\"required\":true,\"options\":[{\"label\":\"预览\"}]}"))
                .andExpect(status().isOk()).andReturn(), "$.id");
        mockMvc.perform(get(secondProject + "/fields").session(owner)).andExpect(jsonPath("$", hasSize(1)));
        String taskId = read(mockMvc.perform(post(firstProject + "/tasks").session(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"确认版本\",\"assigneeId\":" + memberId + ",\"stageId\":" + stage + "}"))
                .andExpect(status().isOk()).andReturn(), "$.id");
        String valuesUrl = "/api/tasks/" + taskId + "/fields";
        mockMvc.perform(put(valuesUrl).session(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"" + field + "\":\"预览\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$['" + field + "']").value("预览"));
        mockMvc.perform(put(valuesUrl).session(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"" + field + "\":\"未知\"}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(valuesUrl).session(owner)).andExpect(jsonPath("$['" + field + "']").value("预览"));
    }

    private String createTemplateProject(MockHttpSession owner, String name) throws Exception {
        return read(mockMvc.perform(post("/api/projects").session(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"templateId\":2}"))
                .andExpect(status().isOk()).andReturn(), "$.id");
    }

    @Test
    void creatorAssignsAndMemberMovesTaskThroughNotifications() throws Exception {
        MockHttpSession creator = register("creator1", "password1", "创建者");
        mockMvc.perform(get("/api/team").session(creator))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/team").session(creator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"课程小组\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CREATOR"))
                .andExpect(jsonPath("$.inviteCode").isString());

        String inviteCode = read(mockMvc.perform(get("/api/team").session(creator))
                .andExpect(status().isOk())
                .andReturn(), "$.inviteCode");

        mockMvc.perform(post("/api/team").session(creator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"另一个团队\"}"))
                .andExpect(status().isConflict());

        MockHttpSession member = register("member1", "password1", "成员");
        mockMvc.perform(post("/api/team/join").session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + inviteCode.toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.inviteCode").value(nullValue()));

        mockMvc.perform(get("/api/team/members").session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id").value(hasItem(Integer.parseInt(
                        read(mockMvc.perform(get("/api/auth/me").session(member)).andReturn(), "$.id")))))
                .andExpect(jsonPath("$[*].displayName").value(hasItem("创建者")))
                .andExpect(jsonPath("$[*].displayName").value(hasItem("成员")))
                .andExpect(jsonPath("$[*].role").value(hasItem("CREATOR")))
                .andExpect(jsonPath("$[*].role").value(hasItem("MEMBER")));
        mockMvc.perform(get("/api/team/members").session(register("outsider1", "password1", "未入团")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects").session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不该成功\"}"))
                .andExpect(status().isForbidden());

        MvcResult project = mockMvc.perform(post("/api/projects").session(creator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"协作系统\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = read(project, "$.id");

        MvcResult stage = mockMvc.perform(post("/api/projects/" + projectId + "/stages").session(creator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"开发\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String stageId = read(stage, "$.id");

        MvcResult emptyStage = mockMvc.perform(post("/api/projects/" + projectId + "/stages").session(creator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"空阶段\"}"))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/stages/" + read(emptyStage, "$.id")).session(creator).with(csrf()))
                .andExpect(status().isOk());

        String memberId = read(mockMvc.perform(get("/api/auth/me").session(member)).andReturn(), "$.id");
        MvcResult task = mockMvc.perform(post("/api/projects/" + projectId + "/tasks").session(creator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"实现登录\",\"description\":\"先做注册\",\"assigneeId\":" + memberId
                                + ",\"stageId\":" + stageId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andReturn();
        String taskId = read(task, "$.id");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/stages/" + stageId).session(creator).with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/tasks/" + taskId + "/start").session(member).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOING"));
        mockMvc.perform(patch("/api/tasks/" + taskId).session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"补充了实现说明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("补充了实现说明"));
        mockMvc.perform(patch("/api/tasks/" + taskId).session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"不能改标题\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tasks/" + taskId + "/start").session(member).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/tasks/" + taskId + "/comments").session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"开始做了\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tasks/" + taskId + "/comments").session(creator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"注意会话\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tasks/" + taskId + "/complete").session(member).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        mockMvc.perform(get("/api/notifications").session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='TASK_ASSIGNED')]").exists())
                .andExpect(jsonPath("$[?(@.type=='TASK_COMMENTED')]").exists());
        mockMvc.perform(get("/api/notifications").session(creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='TASK_STARTED')]").exists())
                .andExpect(jsonPath("$[?(@.type=='TASK_COMMENTED')]").exists())
                .andExpect(jsonPath("$[?(@.type=='TASK_COMPLETED')]").exists());

        mockMvc.perform(put("/api/tasks/" + taskId + "/status").session(creator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DOING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOING"));
        mockMvc.perform(put("/api/tasks/" + taskId + "/status").session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TODO\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/notifications").session(member))
                .andExpect(jsonPath("$[?(@.type=='TASK_STATUS_CHANGED')]").exists());

        mockMvc.perform(get("/api/workbench").session(creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inProgressProjectCount").value(1));
        mockMvc.perform(get("/api/workbench").session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openTasks", hasSize(1)))
                .andExpect(jsonPath("$.openTasks[0].status").value("DOING"));

        mockMvc.perform(patch("/api/me").session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"成员乙\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("成员乙"));
        mockMvc.perform(get("/api/tasks/" + taskId).session(creator))
                .andExpect(jsonPath("$.assigneeName").value("成员乙"));

        mockMvc.perform(post("/api/notifications/read-all").session(creator).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications").session(creator))
                .andExpect(jsonPath("$[?(@.read==false)]").doesNotExist());

        String oldCode = read(mockMvc.perform(get("/api/team").session(creator)).andReturn(), "$.inviteCode");
        String newCode = read(mockMvc.perform(post("/api/team/invite").session(creator).with(csrf()))
                .andExpect(status().isOk()).andReturn(), "$.inviteCode");
        MockHttpSession late = register("member2", "password1", "后来者");
        mockMvc.perform(post("/api/team/join").session(late).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + oldCode + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/team/join").session(late).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + newCode + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/projects/" + projectId).session(member).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tasks/" + taskId).session(member).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/projects/" + projectId).session(creator))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/projects/" + projectId).session(creator).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tasks/" + taskId).session(member))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects").session(creator))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/notifications").session(member))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/workbench").session(member))
                .andExpect(jsonPath("$.openTasks", hasSize(0)));
    }

    @Test
    void inviteAttemptsAreLimited() throws Exception {
        MockHttpSession creator = register("creator2", "password1", "创建者");
        mockMvc.perform(post("/api/team").session(creator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"限流小组\"}"))
                .andExpect(status().isOk());
        MockHttpSession stranger = register("stranger", "password1", "外人");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/team/join").session(stranger).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"ZZZZZ\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/team/join").session(stranger).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ZZZZZ\"}"))
                .andExpect(status().isTooManyRequests());
    }

    private MockHttpSession register(String username, String password, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password
                                + "\",\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    private String read(MvcResult result, String expression) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), expression).toString();
    }
}
