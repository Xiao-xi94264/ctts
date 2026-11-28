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
import com.ctts.service.TaskReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 评审删除的事务与状态回退测试。
 *
 * 这一组断言守的是「两条写操作必须同时成立」：
 * 新增评审时 submission.status 1→2，删除评审时 2→1。
 * 如果哪天 delete 里把 submissionId 写成 review 的 id，或者漏掉状态回退，
 * 只有第 2 条测试会变红，手工测试很容易漏过。
 *
 * 类上 @Transactional：每个方法结束后强制回滚，不污染开发库。
 */
@SpringBootTest
@Transactional
class ReviewTransactionTest {

    private static final Integer 待评审 = 1;
    private static final Integer 已评审 = 2;

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

    private Long teacherId;
    private Long studentId;
    private Long submissionId;

    @BeforeEach
    void 准备一条待评审的提交() {
        teacherId = 新建用户("zz_rv_teacher", 2);
        studentId = 新建用户("zz_rv_student", 1);

        Project project = new Project();
        project.setTitle("评审事务测试项目");
        project.setTeacherId(teacherId);
        project.setStartDate(LocalDate.of(2026, 1, 1));
        project.setEndDate(LocalDate.of(2026, 6, 30));
        projectMapper.insert(project);

        Task task = new Task();
        task.setProjectId(project.getId());
        task.setTitle("评审事务测试任务");
        // deadline 是 NOT NULL 列，必须给值，否则插入直接被数据库拒绝
        task.setDeadline(LocalDateTime.of(2026, 6, 30, 18, 0));
        task.setTotalScore(100);
        taskMapper.insert(task);

        submissionId = 新建提交(task.getId());
    }

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        // 本测试不登录，密码只是占位，必须非空以满足列约束
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("评审事务测试");
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }

    private Long 新建提交(Long taskId) {
        TaskSubmission s = new TaskSubmission();
        s.setTaskId(taskId);
        s.setUserId(studentId);
        s.setContent("待评审的提交");
        taskSubmissionMapper.insert(s);
        return s.getId();
    }

    /** 插入一条评审，走 Service 的 add（含状态 1→2） */
    private TaskReview 新增评审() {
        TaskReview review = new TaskReview();
        review.setSubmissionId(submissionId);
        review.setReviewerId(teacherId);
        review.setScore(85);
        review.setComment("测试评语");
        return taskReviewService.add(review);
    }

    @Test
    @DisplayName("新增评审后：提交状态 1 → 2")
    void addReviewMarksSubmissionAsReviewed() {
        TaskReview saved = 新增评审();

        assertNotNull(saved.getId(), "评审应已落库并回填主键");
        assertEquals(已评审, submissionStatus(), "新增评审后提交状态应为已评审");
    }

    @Test
    @DisplayName("删除评审后：提交状态 2 → 1，且评审记录消失")
    void deleteReviewRestoresSubmissionStatus() {
        TaskReview saved = 新增评审();
        assertEquals(已评审, submissionStatus(), "前置条件：提交应为已评审");

        taskReviewService.delete(saved.getId(), teacherId);

        // 关键断言一：状态回退到 1，而不是 0 或保持 2
        assertEquals(待评审, submissionStatus(), "删除评审后提交状态应回退为待评审");
        // 关键断言二：评审行真的删掉了
        assertNull(taskReviewMapper.selectById(saved.getId()), "评审记录应已删除");
    }

    @Test
    @DisplayName("删除评审不影响其他提交的状态")
    void deleteReviewDoesNotTouchOtherSubmissions() {
        // 另建一条提交，保持待评审。
        // 必须换一个学生交：uk_submission_task_user 落地后，同一学生对同一任务只能有一份提交，
        // 这里若仍用 studentId 会直接撞唯一索引——改数据不改断言。
        Long otherStudentId = 新建用户("zz_rv_student2", 1);
        TaskSubmission other = new TaskSubmission();
        other.setTaskId(taskSubmissionMapper.selectById(submissionId).getTaskId());
        other.setUserId(otherStudentId);
        other.setContent("另一条不该被影响的提交");
        taskSubmissionMapper.insert(other);

        TaskReview saved = 新增评审();
        taskReviewService.delete(saved.getId(), teacherId);

        assertEquals(待评审, submissionStatus(), "被评审的提交应回退为待评审");
        assertEquals(待评审, taskSubmissionMapper.selectById(other.getId()).getStatus(),
                "无关提交的状态不应被改动");
    }

    @Test
    @DisplayName("删除不存在的评审 → 抛 404 业务异常，不静默成功")
    void deleteUnknownReviewThrows404() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskReviewService.delete(999_999L, teacherId),
                "删除不存在的评审应抛业务异常，而不是静默返回");
        assertEquals(404, ex.getCode().intValue(), "错误码应为 404");
    }

    /** 从库里重新查状态，避免用到内存里过期的对象 */
    private Integer submissionStatus() {
        return taskSubmissionMapper.selectById(submissionId).getStatus();
    }
}
