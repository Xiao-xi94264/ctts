package com.ctts;

import com.ctts.entity.Project;
import com.ctts.entity.Task;
import com.ctts.entity.TaskReview;
import com.ctts.entity.TaskSubmission;
import com.ctts.entity.User;
import com.ctts.mapper.ProjectMapper;
import com.ctts.mapper.TaskMapper;
import com.ctts.mapper.TaskReviewMapper;
import com.ctts.mapper.TaskSubmissionMapper;
import com.ctts.mapper.UserMapper;
import com.ctts.service.TaskReviewService;
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
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评审列表的筛选条件测试。
 *
 * 起因是一次真实事故：调用方以为 {@code GET /reviews?submissionId=X} 会过滤，
 * 但接口当时根本没有这个参数——Spring MVC 对未声明的 query 参数**静默忽略**，
 * 于是返回了全表，调用方取 data[0] 拿到的是别人的记录，误删了一条历史评审。
 *
 * 因此本类守两件事：
 * 1. 声明出来的参数必须真的下推到 SQL（第 2、3、5 条）；
 * 2. 参数名拼错时依旧会退化成查全表，这是框架行为改不了，只能在文档里写清（第 6 条）。
 *
 * 类上 @Transactional：每个方法结束后强制回滚，不污染开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewFilterTest {

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
    private TaskReviewService taskReviewService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private ObjectMapper objectMapper;

    private Long teacherA;
    private Long teacherB;
    private Long studentId;
    /** 管理员：本类验证的是"筛选条件是否下推到 SQL"，需要一个不受可见范围收窄的视角 */
    private Long adminId;
    /** subA 有 1 条评审，subB 有 2 条评审，subC 一条都没有 */
    private Long subA;
    private Long subB;
    private Long subC;

    @BeforeEach
    void 造三条提交与三条评审() {
        teacherA = 新建用户("zz_rf_teacher_a", 2);
        teacherB = 新建用户("zz_rf_teacher_b", 2);
        studentId = 新建用户("zz_rf_student", 1);
        adminId = 新建用户("zz_rf_admin", 3);

        Project project = new Project();
        project.setTitle("评审筛选测试项目");
        project.setTeacherId(teacherA);
        project.setStartDate(LocalDate.of(2026, 9, 1));
        project.setEndDate(LocalDate.of(2026, 12, 31));
        projectMapper.insert(project);

        Task task = new Task();
        task.setProjectId(project.getId());
        task.setTitle("评审筛选测试任务");
        // deadline 是 NOT NULL 列，不给值会直接被库拒绝
        task.setDeadline(LocalDateTime.of(2026, 12, 1, 18, 0));
        task.setTotalScore(100);
        taskMapper.insert(task);

        subA = 新建提交(task.getId(), "提交A");
        subB = 新建提交(task.getId(), "提交B");
        subC = 新建提交(task.getId(), "提交C");

        // subA：教师A 评一次
        新建评审(subA, teacherA, 80);
        // subB：教师A 与教师B 各评一次
        新建评审(subB, teacherA, 85);
        新建评审(subB, teacherB, 90);
    }

    @Test
    @DisplayName("不传任何参数 → 退化为查全部，三条评审都在结果里")
    void noFilterReturnsEverything() {
        List<Long> submissionIds = 只取SubmissionId(taskReviewService.list(null, null, adminId, 3));

        assertTrue(submissionIds.contains(subA), "不传参数时应包含 subA 的评审");
        assertTrue(submissionIds.contains(subB), "不传参数时应包含 subB 的评审");
    }

    @Test
    @DisplayName("按 submissionId 筛选 → 只返回该提交的评审，不再夹带别人的")
    void filterBySubmissionId() {
        List<TaskReview> reviews = taskReviewService.list(subA, null, adminId, 3);

        assertEquals(1, reviews.size(), "subA 只有 1 条评审");
        assertEquals(subA, reviews.get(0).getSubmissionId(), "返回的必须是 subA 的评审，不能是别的提交");
    }

    @Test
    @DisplayName("按 reviewerId 筛选 → 只返回该评审人评过的记录")
    void filterByReviewerId() {
        List<TaskReview> reviews = taskReviewService.list(null, teacherB, adminId, 3);

        assertEquals(1, reviews.size(), "教师B 只评过 subB 一次");
        assertEquals(subB, reviews.get(0).getSubmissionId());
        assertEquals(teacherB, reviews.get(0).getReviewerId());
    }

    @Test
    @DisplayName("两个条件同时传 → 交集，且不会因第二个条件被忽略而放大")
    void filterByBoth() {
        List<TaskReview> reviews = taskReviewService.list(subB, teacherB, adminId, 3);

        assertEquals(1, reviews.size(), "subB + 教师B 应为 1 条");
        assertEquals(teacherB, reviews.get(0).getReviewerId());
        assertEquals(subB, reviews.get(0).getSubmissionId());
    }

    @Test
    @DisplayName("筛选一个不存在的提交 → 空列表，绝不能退化成查全表")
    void unknownSubmissionReturnsEmptyNotNullEverything() {
        List<TaskReview> reviews = taskReviewService.list(999_999L, null, adminId, 3);

        assertTrue(reviews.isEmpty(),
                "查不到就该是空列表；若返回非空，说明筛选条件没进 SQL，调用方会拿到别人的数据");
    }

    @Test
    @DisplayName("HTTP 层：GET /reviews?submissionId= → 参数确实被 Controller 收下并下推")
    void httpFilterWorks() throws Exception {
        String token = jwtUtil.generate(adminId, 3);
        String body = mockMvc.perform(get("/reviews").param("submissionId", String.valueOf(subB))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode data = objectMapper.readTree(body).get("data");
        assertEquals(2, data.size(), "subB 有两条评审（教师A + 教师B）");
        for (JsonNode node : data) {
            assertEquals(subB, node.get("submissionId").asLong(), "结果里不能混进其他提交的评审");
        }
    }

    @Test
    @DisplayName("参数名拼错时仍会静默返回全表 —— 框架行为，调用方必须照文档拼")
    void misspelledParamIsSilentlyIgnored() throws Exception {
        String token = jwtUtil.generate(adminId, 3);
        // 下划线写法：没有对应 @RequestParam，Spring MVC 直接丢弃，不报错也不提示
        String body = mockMvc.perform(get("/reviews").param("submission_id", String.valueOf(subA))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode data = objectMapper.readTree(body).get("data");
        assertFalse(data.size() == 1,
                "拼错参数名不该起作用；若这里通过了，说明框架开始校验未知参数，需同步改文档");
    }

    @Test
    @DisplayName("参数值类型不对 → 400，不是 500")
    void wrongParamTypeReturns400() throws Exception {
        String token = jwtUtil.generate(adminId, 3);
        // 加了 Long 参数就等于多了一条"客户端可能传错"的路径。
        // 这类异常落到兜底处理器就会变成 500，把客户端错误记成服务端故障。
        mockMvc.perform(get("/reviews").param("submissionId", "abc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("submissionId")));
    }

    // ---------- 辅助方法 ----------

    private List<Long> 只取SubmissionId(List<TaskReview> reviews) {
        return reviews.stream().map(TaskReview::getSubmissionId).collect(Collectors.toList());
    }

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        // 本测试不登录，密码只是占位，必须非空以满足列约束
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("评审筛选测试");
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }

    private Long 新建提交(Long taskId, String content) {
        TaskSubmission s = new TaskSubmission();
        s.setTaskId(taskId);
        s.setUserId(studentId);
        s.setContent(content);
        taskSubmissionMapper.insert(s);
        return s.getId();
    }

    private void 新建评审(Long submissionId, Long reviewerId, int score) {
        TaskReview r = new TaskReview();
        r.setSubmissionId(submissionId);
        r.setReviewerId(reviewerId);
        r.setScore(score);
        r.setComment("评审筛选测试评语");
        taskReviewMapper.insert(r);
    }
}
