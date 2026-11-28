package com.ctts;

import com.ctts.entity.Project;
import com.ctts.entity.Task;
import com.ctts.entity.TaskSubmission;
import com.ctts.entity.User;
import com.ctts.mapper.ProjectMapper;
import com.ctts.mapper.ProjectMemberMapper;
import com.ctts.mapper.TaskMapper;
import com.ctts.mapper.TaskSubmissionMapper;
import com.ctts.mapper.UserMapper;
import com.ctts.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 逻辑删除（#10）：DELETE 不再真的删行，而是把 {@code deleted} 置为本行 id。
 *
 * <p><b>改这个的起因</b>：物理删除下，"删一个有项目/有提交的用户"会撞外键（errno 1451），
 * 接口只能返回一个和业务无关的数据库错误；而"删除"在业务上往往只是"以后别显示了"，
 * 历史记录本身不该消失。
 *
 * <p><b>软删除的代价必须说清</b>：它把一条 SQL 保证（外键不允许孤儿）换成了 N 处人工约定
 * （每个 SELECT 都要带 {@code deleted = 0}）。所以本类的用例不是"测删除成功"，
 * 而是<b>逐个入口验证被删的数据确实回不来了</b>：
 * <ul>
 *   <li>列表、详情、分页 total 各自独立验证——{@code selectCount} 漏过滤是最隐蔽的一种，
 *       会表现为"共 5 条，只显示 3 条"</li>
 *   <li>统计单独验证——它是一条手写 JOIN，没有任何地方提醒你加条件</li>
 *   <li>用户名/任务提交这些唯一索引，验证"删了能重建"而不是"永远占位"</li>
 * </ul>
 *
 * <p>另一半用例验证相反的方向：<b>规则没丢</b>。软删除后外键不再拦"删父留子"，
 * 所以"项目下还有任务不能删"必须由 Service 显式判断，这里把两条守卫都钉住。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SoftDeleteTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private TaskSubmissionMapper taskSubmissionMapper;
    @Autowired
    private ProjectMemberMapper projectMemberMapper;
    @Autowired
    private com.ctts.mapper.TaskReviewMapper taskReviewMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long adminId;
    private Long teacherId;
    private Long studentId;
    private Long projectId;
    private Long taskId;

    @BeforeEach
    void 造一条完整的链() {
        adminId = 新建用户("zz_sd_admin", 3);
        teacherId = 新建用户("zz_sd_teacher", 2);
        studentId = 新建用户("zz_sd_student", 1);

        projectId = 新建项目("软删除测试项目", teacherId);
        taskId = 新建任务(projectId, "软删除测试任务");
    }

    // ---------------- 一、被引用的数据可以删了（这才是 #10 要解决的） ----------------

    @Test
    @DisplayName("删有项目在用的教师 → 200。物理删除时代这里是外键 1451，业务上根本删不掉")
    void canDeleteUserReferencedByProject() throws Exception {
        // 前置确认：这个用户在库里确实还被项目引用着
        assertEquals(teacherId, projectMapper.selectById(projectId).getTeacherId(),
                "造数前提：项目应归属这个教师，否则这条用例测不到外键");

        mockMvc.perform(delete("/users/" + teacherId)
                        .header("Authorization", "Bearer " + token(adminId, 3)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("删掉的学生：列表查不到、详情 404、旧 token 立刻 401")
    void deletedUserDisappearsEverywhere() throws Exception {
        String oldToken = token(studentId, 1);
        String adminToken = token(adminId, 3);

        // 删除前 token 可用
        mockMvc.perform(get("/submissions").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/users/" + studentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // ① 列表里没有了（selectByRole 必须带 deleted = 0）
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data[?(@.id == " + studentId + ")]").isEmpty());

        // ② 详情 404（selectById 必须带 deleted = 0）
        mockMvc.perform(get("/users/" + studentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // ③ 旧 token 失效：拦截器的 selectById 查不到这个人了
        mockMvc.perform(get("/submissions").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("同名用户删除后可以重新注册 —— 唯一索引含 deleted，不是永久占位")
    void usernameCanBeReusedAfterDelete() throws Exception {
        String adminToken = token(adminId, 3);

        mockMvc.perform(delete("/users/" + studentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zz_sd_student\",\"password\":\"123456\",\"realName\":\"同名新学生\",\"role\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("zz_sd_student"));
    }

    // ---------------- 二、被删的数据从每个入口都回不来 ----------------

    @Test
    @DisplayName("删除项目 → 列表与详情都查不到")
    void deletedProjectDisappears() throws Exception {
        // 删除是写操作，@RequireRole({2}) 只给教师；管理员也删不了，这不是遗漏而是设计
        String token = token(teacherId, 2);

        // 先删任务，否则会被"项目下还有任务"的守卫拦住（下面单独测这个守卫）
        mockMvc.perform(delete("/tasks/" + taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/projects/" + projectId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[?(@.id == " + projectId + ")]").isEmpty());
        mockMvc.perform(get("/projects/" + projectId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("删除任务 → 列表、详情、以及分页的 total 同步减少")
    void deletedTaskDisappearsFromListAndCount() throws Exception {
        String token = token(teacherId, 2);

        // 分页 total 是独立 SQL（selectCount），必须单独验证：
        // 只测 list 的话，count 漏过滤仍然会通过，前端表现为"共 N 条，只显示 N-1 条"
        long before = taskMapper.selectCount(projectId);
        assertEquals(1, before, "造数前提：该项目应只有 1 个任务");

        mockMvc.perform(delete("/tasks/" + taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertEquals(0, taskMapper.selectCount(projectId), "分页 total 必须排除已删任务");
        assertNull(taskMapper.selectById(taskId), "详情必须排除已删任务");
        assertTrue(taskMapper.selectByCondition(projectId).isEmpty(), "列表必须排除已删任务");
    }

    @Test
    @DisplayName("删除提交 → 统计里 submissionCount 减少（统计是手写 JOIN，最容易漏过滤）")
    void deletedSubmissionLeavesStatistics() throws Exception {
        Long submissionId = 新建提交(taskId, studentId);

        Integer before = 统计项(projectId).get("submissionCount").asInt();
        assertEquals(1, before, "造数前提：该项目应有 1 条提交");

        mockMvc.perform(delete("/submissions/" + submissionId)
                        .header("Authorization", "Bearer " + token(studentId, 1)))
                .andExpect(status().isOk());

        Integer after = 统计项(projectId).get("submissionCount").asInt();
        assertEquals(0, after,
                "已删提交仍被计入统计：StatsMapper 的 LEFT JOIN 少了 AND s.deleted = 0");
    }

    @Test
    @DisplayName("移出成员 → 统计里 memberCount 减少，提交率的分母跟着变")
    void removedMemberLeavesStatistics() throws Exception {
        新建成员(projectId, studentId);
        assertEquals(1, 统计项(projectId).get("memberCount").asInt());

        mockMvc.perform(delete("/projects/" + projectId + "/members/" + studentId)
                        .header("Authorization", "Bearer " + token(teacherId, 2)))
                .andExpect(status().isOk());

        assertEquals(0, 统计项(projectId).get("memberCount").asInt(),
                "已移出的成员仍被计入分母：memberCount 子查询少了 AND m.deleted = 0");
    }

    @Test
    @DisplayName("删除有评审的提交 → 评审一起消失，不留孤儿")
    void deletingSubmissionCascadesToReview() throws Exception {
        Long sid = 新建提交(taskId, studentId);
        Long rid = 新建评审(sid, 90);
        assertNotNull(taskReviewMapper.selectById(rid), "造数前提：评审应已存在");

        mockMvc.perform(delete("/submissions/" + sid)
                        .header("Authorization", "Bearer " + token(studentId, 1)))
                .andExpect(status().isOk());

        // 物理删除时代，这里会被外键拦住（删不掉），所以根本不存在"孤儿评审"这个问题。
        // 软删除不触发外键，级联必须写在 Service 里——漏了就是一条评了却不存在的作业
        assertNull(taskReviewMapper.selectById(rid),
                "评审成了孤儿：TaskSubmissionServiceImpl.delete 少了 softDeleteBySubmissionId");
        assertEquals(0, 统计项(projectId).get("reviewedCount").asInt(), "统计不该再数到它");
    }

    // ---------------- 三、行其实还在（软删除与物理删除的分界） ----------------

    @Test
    @DisplayName("删除只是标记：行还在，deleted 被置为本行 id（不是 1）")
    void rowStillExistsWithDeletedEqualsId() {
        projectMapper.softDelete(projectId);

        // 绕过 Mapper 直接查原表：Mapper 的 selectById 已经过滤了，看不见才正常
        List<Long> markers = jdbcTemplate.queryForList(
                "SELECT deleted FROM training_project WHERE id = ?", Long.class, projectId);

        assertEquals(1, markers.size(), "行被物理删掉了：软删除不该真的 DELETE");
        assertNotNull(markers.get(0), "deleted 不该为 NULL");
        assertEquals(projectId, markers.get(0),
                "deleted 应等于本行 id。写固定值 1 的话，同一条唯一键删两次就会撞索引");
        assertNotEquals(0L, markers.get(0), "deleted 为 0 说明根本没删");

        // 而通过 Mapper 看，它已经"不存在"了
        assertNull(projectMapper.selectById(projectId));
    }

    // ---------------- 四、规则没丢：外键不再兜底，Service 必须自己判 ----------------

    @Test
    @DisplayName("项目下还有任务 → 400。物理删除靠外键拦，软删除只能靠 Service 数一遍")
    void cannotDeleteProjectWithTasks() throws Exception {
        mockMvc.perform(delete("/projects/" + projectId)
                        .header("Authorization", "Bearer " + token(teacherId, 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("还有 1 个任务")));
    }

    @Test
    @DisplayName("任务下还有提交 → 400。同理，这是软删除后必须补上的第二道守卫")
    void cannotDeleteTaskWithSubmissions() throws Exception {
        新建提交(taskId, studentId);

        mockMvc.perform(delete("/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token(teacherId, 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("还有 1 份提交")));
    }

    @Test
    @DisplayName("已删的任务不再阻止删项目 —— 守卫数的应该是「未删除」的任务")
    void deletedTaskDoesNotBlockProjectDeletion() throws Exception {
        taskMapper.softDelete(taskId);

        mockMvc.perform(delete("/projects/" + projectId)
                        .header("Authorization", "Bearer " + token(teacherId, 2)))
                .andExpect(status().isOk());
    }

    // ---------------- 工具方法 ----------------

    private String token(Long userId, Integer role) {
        return jwtUtil.generate(userId, role);
    }

    private com.fasterxml.jackson.databind.JsonNode 统计项(Long pid) throws Exception {
        String body = mockMvc.perform(get("/stats/projects")
                        .header("Authorization", "Bearer " + token(adminId, 3)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        com.fasterxml.jackson.databind.JsonNode arr =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("data");
        for (com.fasterxml.jackson.databind.JsonNode item : arr) {
            if (item.get("projectId").asLong() == pid) {
                return item;
            }
        }
        throw new AssertionError("统计结果里没有项目 " + pid);
    }

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("软删除测试");
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }

    private Long 新建项目(String title, Long teacher) {
        Project p = new Project();
        p.setTitle(title);
        p.setTeacherId(teacher);
        p.setStartDate(LocalDate.of(2026, 9, 1));
        p.setEndDate(LocalDate.of(2026, 12, 31));
        projectMapper.insert(p);
        return p.getId();
    }

    private Long 新建任务(Long pid, String title) {
        Task t = new Task();
        t.setProjectId(pid);
        t.setTitle(title);
        // deadline 是 NOT NULL：不传会撞 errno 1048，与"不写这一列让默认值生效"无关
        t.setDeadline(java.time.LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        t.setTotalScore(100);
        taskMapper.insert(t);
        return t.getId();
    }

    private Long 新建提交(Long tid, Long uid) {
        TaskSubmission s = new TaskSubmission();
        s.setTaskId(tid);
        s.setUserId(uid);
        s.setContent("软删除测试提交");
        taskSubmissionMapper.insert(s);
        return s.getId();
    }

    private Long 新建评审(Long submissionId, int score) {
        com.ctts.entity.TaskReview r = new com.ctts.entity.TaskReview();
        r.setSubmissionId(submissionId);
        r.setReviewerId(teacherId);
        r.setScore(score);
        r.setComment("软删除测试评语");
        taskReviewMapper.insert(r);
        return r.getId();
    }

    private void 新建成员(Long pid, Long uid) {
        com.ctts.entity.ProjectMember m = new com.ctts.entity.ProjectMember();
        m.setProjectId(pid);
        m.setUserId(uid);
        projectMemberMapper.insert(m);
    }
}
