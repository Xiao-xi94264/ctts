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
import com.ctts.util.JwtUtil;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 一个提交只能有一条评审。
 *
 * <p>这条规则由两层保证，两层职责不同，测试也分开验：
 * <ol>
 *   <li><b>Service 层</b> —— 给得出能读懂的提示（"已有评审，改分请走 PUT"）</li>
 *   <li><b>数据库唯一索引</b> {@code uk_review_submission} —— 防并发。
 *       两个请求可能同时通过 Service 的判空，只有一个能插入成功</li>
 * </ol>
 *
 * <p>只测 Service 层是不够的：把唯一索引删掉后，"重复评审返回 400"照样成立，
 * 但并发下依然能插进两条。所以最后一条用例绕过 Service 直接走 Mapper，
 * 专门验证约束本身是否存在——这是唯一能区分两层的写法。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewUniqueTest {

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
    private JwtUtil jwtUtil;

    private Long teacherId;
    private Long studentId;
    private Long submissionId;

    @BeforeEach
    void 造一份可评审的提交() {
        teacherId = 新建用户("zz_ru_teacher", 2);
        studentId = 新建用户("zz_ru_student", 1);

        Project p = new Project();
        p.setTitle("唯一评审测试项目");
        p.setTeacherId(teacherId);
        p.setStartDate(LocalDate.of(2026, 9, 1));
        p.setEndDate(LocalDate.of(2026, 12, 31));
        projectMapper.insert(p);

        Task t = new Task();
        t.setProjectId(p.getId());
        t.setTitle("唯一评审测试任务");
        t.setDeadline(LocalDateTime.of(2026, 12, 1, 18, 0));
        t.setTotalScore(100);
        taskMapper.insert(t);

        TaskSubmission s = new TaskSubmission();
        s.setTaskId(t.getId());
        s.setUserId(studentId);
        s.setContent("唯一评审测试提交");
        taskSubmissionMapper.insert(s);
        submissionId = s.getId();
    }

    @Test
    @DisplayName("同一提交评两次 → 第二次 400，且库里仍然只有 1 条")
    void secondReviewIsRejected() throws Exception {
        评审(submissionId, 85, "第一次");

        mockMvc.perform(post("/reviews")
                        .header("Authorization", "Bearer " + teacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submissionId\":" + submissionId + ",\"score\":60,\"comment\":\"第二次\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                // 提示要指名改分该走哪里，否则调用方只能猜
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PUT /reviews/")));

        TaskReview only = taskReviewMapper.selectBySubmissionId(submissionId);
        assertNotNull(only);
        assertEquals(85, only.getScore(), "分数应保持第一次的 85，第二次的 60 不能覆盖也不能新增");
    }

    @Test
    @DisplayName("改分走 PUT → 成功，且仍是一条记录")
    void updateIsTheWayToChangeScore() throws Exception {
        评审(submissionId, 85, "第一次");
        Long reviewId = taskReviewMapper.selectBySubmissionId(submissionId).getId();

        mockMvc.perform(put("/reviews/" + reviewId)
                        .header("Authorization", "Bearer " + teacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":92,\"comment\":\"改过分\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(92));

        assertEquals(92, taskReviewMapper.selectBySubmissionId(submissionId).getScore());
    }

    @Test
    @DisplayName("删掉评审后可以重新评 → 说明约束锁的是\"同时存在两条\"，不是永久锁死")
    void canReviewAgainAfterDelete() throws Exception {
        评审(submissionId, 70, "第一次");
        Long reviewId = taskReviewMapper.selectBySubmissionId(submissionId).getId();

        mockMvc.perform(delete("/reviews/" + reviewId)
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk());

        // 删除后同一提交应能重新评审，否则等于一次性消耗
        mockMvc.perform(post("/reviews")
                        .header("Authorization", "Bearer " + teacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submissionId\":" + submissionId + ",\"score\":75,\"comment\":\"重评\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(75));
    }

    @Test
    @DisplayName("绕过 Service 直接插重复 → 抛 DuplicateKeyException，证明唯一索引真的存在")
    void uniqueIndexExistsAtDatabaseLevel() {
        // 第一条正常插入
        insertRaw(submissionId, 80);

        // 第二条完全绕开 TaskReviewService（不经过任何 Java 判空），
        // 还能被拦住，就只能是数据库约束干的
        assertThrows(DuplicateKeyException.class, () -> insertRaw(submissionId, 90),
                "唯一索引 uk_review_submission 缺失：Service 判空只能挡住顺序请求，挡不住并发");
    }

    // ---------- 辅助方法 ----------

    private void 评审(Long subId, int score, String comment) throws Exception {
        mockMvc.perform(post("/reviews")
                        .header("Authorization", "Bearer " + teacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submissionId\":" + subId + ",\"score\":" + score
                                + ",\"comment\":\"" + comment + "\"}"))
                .andExpect(status().isOk());
    }

    /** 直接落库，不经过 Service */
    private void insertRaw(Long subId, int score) {
        TaskReview r = new TaskReview();
        r.setSubmissionId(subId);
        r.setReviewerId(teacherId);
        r.setScore(score);
        r.setComment("raw");
        taskReviewMapper.insert(r);
    }

    private String teacherToken() {
        return jwtUtil.generate(teacherId, 2);
    }

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("唯一评审测试");
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }
}
