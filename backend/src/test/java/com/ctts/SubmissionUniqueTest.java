package com.ctts;

import com.ctts.entity.Project;
import com.ctts.entity.Task;
import com.ctts.entity.TaskSubmission;
import com.ctts.entity.User;
import com.ctts.mapper.ProjectMapper;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 一个学生对同一任务只能交一份。
 *
 * <p>与 {@code ReviewUniqueTest} 完全同构的两层结构：
 * <ol>
 *   <li><b>Service 层</b> —— 先查后判，给出能读懂的提示（"改内容请走 PUT"）</li>
 *   <li><b>数据库唯一索引</b> {@code uk_submission_task_user} —— 防并发</li>
 * </ol>
 *
 * <p>这条约束还改变了统计口径：在此之前"提交条数"与"提交份数"可以不等
 * （同一学生重复交就多一条），约束落地后两者恒等。{@code StatsApiTest}
 * 里对应的断言已同步改写——约束一旦加上，旧口径下"什么数据都能造出来"的测试
 * 会直接撞库，这是好事：它逼着测试和业务规则保持一致。
 *
 * <p>为什么约束是 (task_id, user_id) 而不是 submission 上的别的东西：
 * 业务主语是"谁交的哪次作业"，这两列合起来才是这句话的主键。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubmissionUniqueTest {

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
    private JwtUtil jwtUtil;

    private Long teacherId;
    private Long studentId;
    private Long otherStudentId;
    private Long taskA;
    private Long taskB;

    @BeforeEach
    void 造两个任务() {
        teacherId = 新建用户("zz_su_teacher", 2);
        studentId = 新建用户("zz_su_student", 1);
        otherStudentId = 新建用户("zz_su_student2", 1);

        Project p = new Project();
        p.setTitle("提交唯一测试项目");
        p.setTeacherId(teacherId);
        p.setStartDate(LocalDate.of(2026, 9, 1));
        p.setEndDate(LocalDate.of(2026, 12, 31));
        projectMapper.insert(p);

        taskA = 新建任务(p.getId(), "任务A");
        taskB = 新建任务(p.getId(), "任务B");
    }

    @Test
    @DisplayName("同一任务交两次 → 第二次 400，提示给出已有 id 与 PUT 路径，且库里仍只有 1 条")
    void secondSubmissionIsRejected() throws Exception {
        提交(taskA, "第一次");

        mockMvc.perform(post("/submissions")
                        .header("Authorization", "Bearer " + studentToken(studentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskA + ",\"content\":\"第二次\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                // 只说"重复了"不够：调用方需要知道改内容该走哪个接口、改哪一条
                .andExpect(jsonPath("$.message").value(containsString("PUT /submissions/")));

        TaskSubmission only = taskSubmissionMapper.selectByTaskAndUser(taskA, studentId);
        assertNotNull(only);
        assertEquals("第一次", only.getContent(), "内容应保持第一次的，第二次不能覆盖也不能新增");
    }

    @Test
    @DisplayName("同一学生交不同任务 → 允许，约束锁的是 (任务, 学生) 组合而不是学生")
    void sameStudentDifferentTaskIsAllowed() throws Exception {
        提交(taskA, "任务A 的提交");
        提交(taskB, "任务B 的提交");

        assertEquals(2, taskSubmissionMapper.selectByUserId(studentId).size());
    }

    @Test
    @DisplayName("不同学生交同一任务 → 允许，一个人交不代表全班都交过了")
    void sameTaskDifferentStudentIsAllowed() throws Exception {
        提交(taskA, "学生1 的提交");

        mockMvc.perform(post("/submissions")
                        .header("Authorization", "Bearer " + studentToken(otherStudentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskA + ",\"content\":\"学生2 的提交\"}"))
                .andExpect(status().isOk());

        assertNotNull(taskSubmissionMapper.selectByTaskAndUser(taskA, studentId), "学生1 的提交应在");
        assertNotNull(taskSubmissionMapper.selectByTaskAndUser(taskA, otherStudentId), "学生2 的提交应在");
    }

    @Test
    @DisplayName("删掉提交后可以重新交 → 约束锁的是\"同时存在两份\"，不是一次性消耗")
    void canSubmitAgainAfterDelete() throws Exception {
        Long submissionId = 提交(taskA, "第一次");

        mockMvc.perform(delete("/submissions/" + submissionId)
                        .header("Authorization", "Bearer " + studentToken(studentId)))
                .andExpect(status().isOk());

        提交(taskA, "重新交的");

        assertEquals("重新交的", taskSubmissionMapper.selectByTaskAndUser(taskA, studentId).getContent());
    }

    @Test
    @DisplayName("改内容走 PUT → 仍是一条记录，不新增")
    void updateIsTheWayToChangeContent() throws Exception {
        Long submissionId = 提交(taskA, "第一次");

        mockMvc.perform(put("/submissions/" + submissionId)
                        .header("Authorization", "Bearer " + studentToken(studentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"改过的内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("改过的内容"));

        assertEquals(1, taskSubmissionMapper.selectByUserId(studentId).size());
    }

    @Test
    @DisplayName("绕过 Service 直接插重复 → 抛 DuplicateKeyException，证明唯一索引真的存在")
    void uniqueIndexExistsAtDatabaseLevel() {
        insertRaw(taskA, studentId);

        // 完全绕开 TaskSubmissionService（不经过任何 Java 判空），
        // 还能被拦住，就只能是数据库约束干的
        assertThrows(DuplicateKeyException.class, () -> insertRaw(taskA, studentId),
                "唯一索引 uk_submission_task_user 缺失：Service 判空只能挡住顺序请求，挡不住并发");
    }

    @Test
    @DisplayName("缺 taskId → 400（不是 500）：少传字段是客户端错误")
    void missingTaskIdReturns400() throws Exception {
        mockMvc.perform(post("/submissions")
                        .header("Authorization", "Bearer " + studentToken(studentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"忘了给任务\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("任务 ID")));
    }

    // ---------- 辅助方法 ----------

    /** 以学生身份提交并返回新提交 id */
    private Long 提交(Long taskId, String content) throws Exception {
        mockMvc.perform(post("/submissions")
                        .header("Authorization", "Bearer " + studentToken(studentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk());

        return taskSubmissionMapper.selectByTaskAndUser(taskId, studentId).getId();
    }

    /** 直接落库，不经过 Service */
    private void insertRaw(Long taskId, Long userId) {
        TaskSubmission s = new TaskSubmission();
        s.setTaskId(taskId);
        s.setUserId(userId);
        s.setContent("raw");
        taskSubmissionMapper.insert(s);
    }

    private String studentToken(Long id) {
        return jwtUtil.generate(id, 1);
    }

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("提交唯一测试");
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }

    private Long 新建任务(Long projectId, String title) {
        Task t = new Task();
        t.setProjectId(projectId);
        t.setTitle(title);
        t.setDeadline(LocalDateTime.of(2026, 12, 1, 18, 0));
        t.setTotalScore(100);
        taskMapper.insert(t);
        return t.getId();
    }
}
