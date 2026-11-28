package com.ctts;

import com.ctts.entity.Project;
import com.ctts.entity.ProjectMember;
import com.ctts.entity.Task;
import com.ctts.entity.TaskReview;
import com.ctts.entity.TaskSubmission;
import com.ctts.entity.User;
import com.ctts.mapper.ProjectMapper;
import com.ctts.mapper.ProjectMemberMapper;
import com.ctts.mapper.TaskMapper;
import com.ctts.mapper.TaskReviewMapper;
import com.ctts.mapper.TaskSubmissionMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统计接口测试：锁住「JOIN 不会把 COUNT 放大」这条不变量。
 *
 * 造数刻意设为 2 个任务 + 3 条提交：
 * 若 StatsMapper.xml 里的 COUNT(DISTINCT t.id) 退化成 COUNT(t.id)，
 * taskCount 会变成 3，本测试立刻变红——这是手工调接口最不易察觉的错误。
 *
 * 类上 @Transactional：每个方法结束后强制回滚，不污染开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatsApiTest {

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
    private TaskReviewMapper taskReviewMapper;
    @Autowired
    private ProjectMemberMapper projectMemberMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private ObjectMapper objectMapper;

    private Long teacherId;
    private Long studentId;
    /** 2 个任务 / 3 条提交 / 2 条评审 */
    private Long projectId;
    /** 零任务的空项目 */
    private Long emptyProjectId;

    @BeforeEach
    void 造两个项目() {
        teacherId = 新建用户("zz_st_teacher", 2);
        studentId = 新建用户("zz_st_student", 1);

        projectId = 新建项目("统计测试项目");

        // 三个成员：s1 交了 A/B 两份，s2 交了 A 一份，s3 一份都没交。
        // s3 是必需的角色——只统计"交过的人"，分母会永远等于分子，提交率恒为 100%。
        // 同一条提交不能由两个学生共用（uk_submission_task_user），
        // 所以"多条提交"只能靠"多个学生"造，不能再让同一学生重复交同一任务。
        Long secondStudentId = 新建用户("zz_st_student2", 1);
        Long idleStudentId = 新建用户("zz_st_student3", 1);
        新建成员(projectId, studentId);
        新建成员(projectId, secondStudentId);
        新建成员(projectId, idleStudentId);

        Long taskA = 新建任务(projectId, "任务A");
        Long taskB = 新建任务(projectId, "任务B");

        // 任务A 两条（两个学生各一条），任务B 一条 → 共 3 条，且 3 个 (任务,学生) 组合互不相同
        Long sub1 = 新建提交(taskA, studentId);
        Long sub2 = 新建提交(taskA, secondStudentId);
        新建提交(taskB, studentId);

        // 前两条提交各一条评审，分数 80/90 → 平均 85
        新建评审(sub1, 80);
        新建评审(sub2, 90);

        emptyProjectId = 新建项目("统计测试空项目");
    }

    @Test
    @DisplayName("按项目统计：2 个任务配 3 条提交，taskCount 必须是 2 而非 3")
    void taskCountIsNotInflatedByJoin() throws Exception {
        JsonNode item = 取统计项(projectId);

        // 核心断言：JOIN 放大后 COUNT(t.id) 会得到 3，DISTINCT 才回到 2
        assertEquals(2, item.get("taskCount").asInt(),
                "2 个任务被 3 条提交放大后应为 2，出现 3 说明 COUNT 少了 DISTINCT");
        assertEquals(3, item.get("submissionCount").asInt(), "提交数应为 3");
        assertEquals(2, item.get("reviewedCount").asInt(), "已评审数应为 2");
        assertEquals(85.0, item.get("avgScore").asDouble(), 0.001, "平均分应为 (80+90)/2");
    }

    @Test
    @DisplayName("零任务的项目仍出现在列表里，且平均分为 null 而不是 0")
    void emptyProjectReportsZeroAndNullAverage() throws Exception {
        JsonNode item = 取统计项(emptyProjectId);

        assertEquals(0, item.get("taskCount").asInt(), "空项目任务数应为 0（若写成 COUNT(*) 会得到 1）");
        assertEquals(0, item.get("submissionCount").asInt());
        assertEquals(0, item.get("reviewedCount").asInt());
        assertTrue(item.get("avgScore").isNull(), "没有评审时平均分应为 null，0 分会与\"真的得了 0 分\"混淆");
    }

    @Test
    @DisplayName("条数与份数在唯一约束下恒等：重复提交已不可能，两个字段留着防将来放开重交")
    void submissionCountAndPairCountAreEqual() throws Exception {
        JsonNode item = 取统计项(projectId);

        // 3 条提交恰好是 3 个不同的 (任务,学生) 组合
        assertEquals(3, item.get("submissionCount").asInt(), "条数按记录算");
        assertEquals(3, item.get("submittedPairCount").asInt(),
                "uk_submission_task_user 落地后，同一学生对同一任务只能有一份，条数与份数必然相等。"
                        + "这两个字段仍然分开保留：若将来允许重交（保留历史版本），它们会重新分叉，"
                        + "而提交率的分子必须始终取份数，否则比率会超过 100%");
    }

    @Test
    @DisplayName("提交率的分母是「成员数 × 任务数」，没交的人也要算进分母")
    void submitRateUsesMemberCountAsDenominator() throws Exception {
        JsonNode item = 取统计项(projectId);

        // 3 个成员（其中 s3 一份没交）× 2 个任务 = 应交 6 份，实交 3 份
        assertEquals(3, item.get("memberCount").asInt(), "成员数应含一份都没交的那个学生");
        assertEquals(6, item.get("expectedCount").asInt(), "应交份数 = 3 人 × 2 任务");
        assertEquals(0.5, item.get("submitRate").asDouble(), 0.0001,
                "3/6 = 0.5。若分母只数\"交过的人\"，这里会算成 3/4 = 0.75，提交率永远好看");
    }

    @Test
    @DisplayName("没有成员的项目：应交 0 份，提交率为 null 而不是 0")
    void emptyProjectHasNullSubmitRate() throws Exception {
        JsonNode item = 取统计项(emptyProjectId);

        assertEquals(0, item.get("memberCount").asInt());
        assertEquals(0, item.get("expectedCount").asInt());
        assertTrue(item.get("submitRate").isNull(),
                "分母为 0 时提交率应为 null——没有成员的项目报\"0%\"是误导，它没有这个属性");
    }

    @Test
    @DisplayName("未携带 token 访问统计接口 → 401")
    void requiresToken() throws Exception {
        mockMvc.perform(get("/stats/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ---------- 辅助方法 ----------

    /** 发一次请求，从 data 数组里挑出指定项目的统计项 */
    private JsonNode 取统计项(Long targetProjectId) throws Exception {
        String token = jwtUtil.generate(studentId, 1);
        String body = mockMvc.perform(get("/stats/projects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode data = objectMapper.readTree(body).get("data");
        assertTrue(data.isArray() && !data.isEmpty(), "统计结果不应为空数组");
        for (JsonNode node : data) {
            if (node.get("projectId").asLong() == targetProjectId) {
                return node;
            }
        }
        assertNotNull(null, "结果里没有找到项目 " + targetProjectId);
        return null;
    }

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("统计测试");
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }

    private Long 新建项目(String title) {
        Project p = new Project();
        p.setTitle(title);
        p.setTeacherId(teacherId);
        p.setStartDate(LocalDate.of(2026, 9, 1));
        p.setEndDate(LocalDate.of(2026, 12, 31));
        projectMapper.insert(p);
        return p.getId();
    }

    private void 新建成员(Long pid, Long uid) {
        ProjectMember m = new ProjectMember();
        m.setProjectId(pid);
        m.setUserId(uid);
        projectMemberMapper.insert(m);
    }

    private Long 新建任务(Long pid, String title) {
        Task t = new Task();
        t.setProjectId(pid);
        t.setTitle(title);
        // deadline 是 NOT NULL 列，必须给值
        t.setDeadline(LocalDateTime.of(2026, 12, 1, 18, 0));
        t.setTotalScore(100);
        taskMapper.insert(t);
        return t.getId();
    }

    private Long 新建提交(Long taskId, Long userId) {
        TaskSubmission s = new TaskSubmission();
        s.setTaskId(taskId);
        s.setUserId(userId);
        s.setContent("统计测试提交");
        taskSubmissionMapper.insert(s);
        return s.getId();
    }

    private void 新建评审(Long submissionId, int score) {
        TaskReview r = new TaskReview();
        r.setSubmissionId(submissionId);
        r.setReviewerId(teacherId);
        r.setScore(score);
        r.setComment("统计测试评语");
        taskReviewMapper.insert(r);
    }
}
