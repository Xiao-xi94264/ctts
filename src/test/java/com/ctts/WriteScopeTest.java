package com.ctts;

import com.ctts.common.BusinessException;
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
import com.ctts.service.ProjectService;
import com.ctts.service.TaskReviewService;
import com.ctts.service.TaskService;
import com.ctts.service.TaskSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * D10：写接口必须校验归属——"能调这个接口"不等于"能动这条记录"。
 *
 * 拦截器只回答"你是不是教师"，回答不了"这个项目是不是你的"。
 * 两者之间的缺口就是：教师A 可以改掉教师B 的项目、学生A 可以改掉学生B 的提交。
 *
 * 修复方式是每个写方法开头「先查一次」：
 * 查不到 → 404（D7）；查到了但不是自己的 → 403（本类）。
 *
 * 类上 @Transactional：每个方法结束后强制回滚，不污染开发库。
 */
@SpringBootTest
@Transactional
class WriteScopeTest {

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
    private ProjectService projectService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskSubmissionService taskSubmissionService;
    @Autowired
    private TaskReviewService taskReviewService;

    private Long teacherA;
    private Long teacherB;
    private Long studentA;
    private Long studentB;
    private Long projectA;
    private Long projectB;
    private Long taskA;
    private Long taskB;
    private Long subB;
    private Long reviewB;

    @BeforeEach
    void 造两个教师各自的项目与数据() {
        teacherA = 新建用户("zz_ws_teacher_a", 2);
        teacherB = 新建用户("zz_ws_teacher_b", 2);
        studentA = 新建用户("zz_ws_student_a", 1);
        studentB = 新建用户("zz_ws_student_b", 1);

        projectA = 新建项目("A 的项目", teacherA);
        projectB = 新建项目("B 的项目", teacherB);

        taskA = 新建任务(projectA, "A 的任务");
        taskB = 新建任务(projectB, "B 的任务");

        新建提交(taskA, studentA, "A 的提交，学生A 交");
        subB = 新建提交(taskB, studentB, "B 的提交，学生B 交");

        reviewB = 新建评审(subB, teacherB, 88);
    }

    @Test
    @DisplayName("教师A 改教师B 的项目 → 403")
    void teacherCannotUpdateOthersProject() {
        Project patch = new Project();
        patch.setTitle("被 A 改掉的 B 的项目");

        断言403(() -> projectService.update(projectB, patch, teacherA), "只能改自己的项目");
    }

    @Test
    @DisplayName("教师A 删教师B 的项目 → 403")
    void teacherCannotDeleteOthersProject() {
        断言403(() -> projectService.delete(projectB, teacherA), "只能删自己的项目");
    }

    @Test
    @DisplayName("教师A 删教师B 项目下的任务 → 403（归属要顺着 project_id 查一层）")
    void teacherCannotDeleteOthersTask() {
        断言403(() -> taskService.delete(taskB, teacherA), "只能删自己项目下的任务");
    }

    @Test
    @DisplayName("学生A 改学生B 的提交 → 403")
    void studentCannotUpdateOthersSubmission() {
        TaskSubmission patch = new TaskSubmission();
        patch.setContent("被学生A 改掉的内容");

        断言403(() -> taskSubmissionService.update(subB, patch, studentA), "只能改自己的提交");
    }

    @Test
    @DisplayName("教师A 改教师B 写的评审 → 403")
    void teacherCannotUpdateOthersReview() {
        TaskReview patch = new TaskReview();
        patch.setScore(10);

        断言403(() -> taskReviewService.update(reviewB, patch, teacherA), "只能改自己写的评审");
    }

    @Test
    @DisplayName("教师A 评审教师B 项目下的提交 → 403（评审权跟着项目走，不跟着角色走）")
    void teacherCannotReviewSubmissionOutsideOwnProject() {
        TaskReview review = new TaskReview();
        review.setSubmissionId(subB);
        // Controller 会用 token 覆盖 reviewerId，这里模拟覆盖后的值
        review.setReviewerId(teacherA);
        review.setScore(60);
        review.setComment("越权评审");

        断言403(() -> taskReviewService.add(review), "只能评审自己项目下的提交");
    }

    @Test
    @DisplayName("反证：教师A 改自己的项目要能成功，否则上面的 403 可能只是因为处处都拦")
    void teacherCanUpdateOwnProject() {
        Project patch = new Project();
        patch.setTitle("A 改自己的项目");

        Project updated = projectService.update(projectA, patch, teacherA);
        assertEquals("A 改自己的项目", updated.getTitle());
    }

    // ---------- 辅助方法 ----------

    private void 断言403(Executable call, String message) {
        BusinessException ex = assertThrows(BusinessException.class, call, message);
        assertEquals(403, ex.getCode().intValue(), message);
    }

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("写越权测试");
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }

    private Long 新建项目(String title, Long ownerId) {
        Project p = new Project();
        p.setTitle(title);
        p.setTeacherId(ownerId);
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

    private Long 新建提交(Long taskId, Long userId, String content) {
        TaskSubmission s = new TaskSubmission();
        s.setTaskId(taskId);
        s.setUserId(userId);
        s.setContent(content);
        taskSubmissionMapper.insert(s);
        return s.getId();
    }

    private Long 新建评审(Long submissionId, Long reviewerId, int score) {
        TaskReview r = new TaskReview();
        r.setSubmissionId(submissionId);
        r.setReviewerId(reviewerId);
        r.setScore(score);
        r.setComment("越权测试评语");
        taskReviewMapper.insert(r);
        return r.getId();
    }
}
