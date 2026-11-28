package com.ctts;

import com.ctts.entity.Project;
import com.ctts.entity.ProjectMember;
import com.ctts.entity.Task;
import com.ctts.entity.User;
import com.ctts.mapper.ProjectMapper;
import com.ctts.mapper.ProjectMemberMapper;
import com.ctts.mapper.TaskMapper;
import com.ctts.mapper.UserMapper;
import com.ctts.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目成员管理：把"哪些学生属于这个项目"从只能用 SQL 维护，变成有接口可管。
 *
 * <p>这个接口不只是一个 CRUD 补充——成员数是提交率的分母。所以它有三条比其他模块更严的规则：
 * <ol>
 *   <li>只能由<b>项目负责人</b>改（不是"任何教师"）：塞一个人进来，全项目的提交率立刻失真</li>
 *   <li>只能加<b>学生</b>：教师进成员表会把老师算进分母，比率系统性偏低</li>
 *   <li>不能<b>重复加</b>：`uk_member_project_user` 保证，Service 再给可读提示</li>
 * </ol>
 *
 * <p>最后一条用例专门验证"接口 → 统计"的闭环：加入成员后，统计里的 memberCount /
 * expectedCount / submitRate 必须跟着变。只测接口返回 200 是不够的——数据进了表但没进统计，
 * 等于白加。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectMemberApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private ProjectMemberMapper projectMemberMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private ObjectMapper objectMapper;

    /** 项目负责人：唯一有权管理成员的人 */
    private Long ownerTeacherId;
    /** 另一个教师：用来验"教师 ≠ 负责人"，权限来自项目归属而不是角色 */
    private Long otherTeacherId;
    private Long studentId;
    private Long projectId;

    @BeforeEach
    void 造一个项目() {
        ownerTeacherId = 新建用户("zz_pm_teacher", 2);
        otherTeacherId = 新建用户("zz_pm_teacher2", 2);
        studentId = 新建用户("zz_pm_student", 1);

        projectId = 新建项目("成员管理测试项目", ownerTeacherId);
        // 有任务才能算出应交份数，统计闭环那条用例要用
        新建任务(projectId, "成员管理测试任务");
    }

    @Test
    @DisplayName("负责人加入学生 → 成功，joinTime 由数据库生成；列表里能查到")
    void addThenList() throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + token(ownerTeacherId, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + studentId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(studentId))
                // joinTime 不在 INSERT 列表里，是库默认值。返回空说明没回查
                .andExpect(jsonPath("$.data.joinTime").isNotEmpty());

        mockMvc.perform(get("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + token(studentId, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(studentId));
    }

    @Test
    @DisplayName("重复加入 → 400，提示「已是项目成员」，库里仍只有 1 条")
    void duplicateAddIsRejected() throws Exception {
        加入成员(studentId);

        mockMvc.perform(post("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + token(ownerTeacherId, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + studentId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("已是项目成员")));

        assertEquals(1, projectMemberMapper.selectByProjectId(projectId).size());
    }

    @Test
    @DisplayName("把教师加进项目 → 400：教师进成员表会污染提交率的分母")
    void teacherCannotBeMember() throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + token(ownerTeacherId, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + otherTeacherId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("只能把学生")));

        assertEquals(0, projectMemberMapper.selectByProjectId(projectId).size());
    }

    @Test
    @DisplayName("加入不存在的用户 → 400，不是 500（外键报错对调用方毫无信息量）")
    void unknownUserIsRejected() throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + token(ownerTeacherId, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":99999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("用户不存在")));
    }

    @Test
    @DisplayName("别的教师（角色对但项目不是他的）加入成员 → 403")
    void otherTeacherIsForbidden() throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + token(otherTeacherId, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + studentId + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("学生调成员写接口 → 403：拦截器按 /projects 前缀判定，轮不到 Service")
    void studentIsForbiddenByInterceptor() throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + token(studentId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + studentId + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("权限不足")));
    }

    @Test
    @DisplayName("项目不存在 → 404（而不是「加了 0 人」这种假成功）")
    void unknownProjectReturns404() throws Exception {
        mockMvc.perform(post("/projects/99999999/members")
                        .header("Authorization", "Bearer " + token(ownerTeacherId, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + studentId + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("项目不存在")));
    }

    @Test
    @DisplayName("请求体缺 userId → 400，提示字段名")
    void missingUserIdReturns400() throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + token(ownerTeacherId, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("学生 ID")));
    }

    @Test
    @DisplayName("移出成员 → 列表变空；再移一次 → 404（不是静默成功）")
    void removeThenRemoveAgain() throws Exception {
        加入成员(studentId);

        mockMvc.perform(delete("/projects/" + projectId + "/members/" + studentId)
                        .header("Authorization", "Bearer " + token(ownerTeacherId, 2)))
                .andExpect(status().isOk());

        assertTrue(projectMemberMapper.selectByProjectId(projectId).isEmpty(), "移出后不应再有成员");

        mockMvc.perform(delete("/projects/" + projectId + "/members/" + studentId)
                        .header("Authorization", "Bearer " + token(ownerTeacherId, 2)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("不是项目成员")));
    }

    @Test
    @DisplayName("闭环：加入成员后统计的分母立刻变化（接口 → 统计，不是只写进表）")
    void memberCountFeedsStats() throws Exception {
        // 加入前：0 成员 × 1 任务 = 应交 0，提交率没有意义（null）
        JsonNode before = 取统计项(projectId);
        assertEquals(0, before.get("memberCount").asInt());
        assertEquals(0, before.get("expectedCount").asInt());
        assertTrue(before.get("submitRate").isNull(), "分母为 0 时提交率应为 null");

        加入成员(studentId);

        // 加入后：1 成员 × 1 任务 = 应交 1；没人交 → 提交率 0.0（这次不是 null，分母是真的 1）
        JsonNode after = 取统计项(projectId);
        assertEquals(1, after.get("memberCount").asInt(), "memberCount 必须跟着成员接口变");
        assertEquals(1, after.get("expectedCount").asInt());
        assertEquals(0.0, after.get("submitRate").asDouble(), 0.0001);
    }

    @Test
    @DisplayName("绕过 Service 直接插重复成员 → DuplicateKeyException，证明 uk_member_project_user 存在")
    void uniqueIndexExistsAtDatabaseLevel() {
        insertRawMember(projectId, studentId);
        assertThrows(DuplicateKeyException.class, () -> insertRawMember(projectId, studentId),
                "唯一索引 uk_member_project_user 缺失：Service 判重挡不住并发加入");
    }

    // ---------- 辅助方法 ----------

    private void 加入成员(Long userId) throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + token(ownerTeacherId, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + userId + "}"))
                .andExpect(status().isOk());
    }

    private JsonNode 取统计项(Long targetProjectId) throws Exception {
        String body = mockMvc.perform(get("/stats/projects")
                        .header("Authorization", "Bearer " + token(ownerTeacherId, 2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode data = objectMapper.readTree(body).get("data");
        for (JsonNode node : data) {
            if (node.get("projectId").asLong() == targetProjectId) {
                return node;
            }
        }
        assertNotNull(null, "统计结果里没有找到项目 " + targetProjectId);
        return null;
    }

    private void insertRawMember(Long pid, Long uid) {
        ProjectMember m = new ProjectMember();
        m.setProjectId(pid);
        m.setUserId(uid);
        projectMemberMapper.insert(m);
    }

    private String token(Long userId, Integer role) {
        return jwtUtil.generate(userId, role);
    }

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("成员管理测试");
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }

    private Long 新建项目(String title, Long teacherId) {
        Project p = new Project();
        p.setTitle(title);
        p.setTeacherId(teacherId);
        p.setStartDate(LocalDate.of(2026, 9, 1));
        p.setEndDate(LocalDate.of(2026, 12, 31));
        projectMapper.insert(p);
        return p.getId();
    }

    private Long 新建任务(Long pid, String title) {
        Task t = new Task();
        t.setProjectId(pid);
        t.setTitle(title);
        t.setDeadline(LocalDateTime.of(2026, 12, 1, 18, 0));
        t.setTotalScore(100);
        taskMapper.insert(t);
        return t.getId();
    }
}
