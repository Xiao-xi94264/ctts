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
import com.ctts.service.TaskSubmissionService;
import com.ctts.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D9：读接口要按角色收窄可见范围。
 *
 * 拦截器对 GET 一律放行（checkRole 里 `if GET return true`），
 * 所以"学生能看到全部提交"这种问题，在拦截器层面根本无从修起——
 * 必须落到 Service：查谁的数据、查多少，是业务规则而不是认证规则。
 *
 * 三条可见范围：
 * 学生 → 自己的；教师 → 自己项目下的 / 自己写的；管理员 → 全部。
 *
 * 类上 @Transactional：每个方法结束后强制回滚，不污染开发库。
 */
@SpringBootTest
@Transactional
class ReadScopeTest {

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
    private TaskSubmissionService taskSubmissionService;
    @Autowired
    private TaskReviewService taskReviewService;
    @Autowired
    private UserService userService;

    private Long teacherA;
    private Long teacherB;
    private Long studentA;
    private Long studentB;
    private Long admin;
    private Long subA;
    private Long subB;
    private Long revA;
    private Long revB;

    @BeforeEach
    void 造两个教师两个学生和一个管理员() {
        admin = 新建用户("zz_rs_admin", 3);
        teacherA = 新建用户("zz_rs_teacher_a", 2);
        teacherB = 新建用户("zz_rs_teacher_b", 2);
        studentA = 新建用户("zz_rs_student_a", 1);
        studentB = 新建用户("zz_rs_student_b", 1);

        Long projectA = 新建项目("收窄测试 A 的项目", teacherA);
        Long projectB = 新建项目("收窄测试 B 的项目", teacherB);

        Long taskA = 新建任务(projectA, "A 的任务");
        Long taskB = 新建任务(projectB, "B 的任务");

        subA = 新建提交(taskA, studentA, "学生A 的提交");
        subB = 新建提交(taskB, studentB, "学生B 的提交");

        // 两条提交各一条评审，评审人分别是各自项目的教师
        revA = 新建评审(subA, teacherA, 80);
        revB = 新建评审(subB, teacherB, 90);
    }

    @Test
    @DisplayName("学生看提交列表：只有自己交的，看不到同学的")
    void studentSeesOnlyOwnSubmissions() {
        List<Long> ids = 提交id(taskSubmissionService.list(studentA, 1));

        assertEquals(List.of(subA), ids, "学生只应看到自己那条提交");
        assertFalse(ids.contains(subB), "同学提交的存在性都不该暴露");
    }

    @Test
    @DisplayName("教师看提交列表：只有自己项目下的，看不到别的教师的项目")
    void teacherSeesOnlySubmissionsUnderOwnProjects() {
        List<Long> ids = 提交id(taskSubmissionService.list(teacherA, 2));

        assertEquals(List.of(subA), ids, "教师只应看到自己项目下的提交");
    }

    @Test
    @DisplayName("管理员看提交列表：全部可见（业务上需要全局视图）")
    void adminSeesAllSubmissions() {
        List<Long> ids = 提交id(taskSubmissionService.list(admin, 3));

        assertTrue(ids.contains(subA) && ids.contains(subB), "管理员应看到两条提交");
    }

    @Test
    @DisplayName("学生看评审列表：只看自己提交收到的评审")
    void studentSeesOnlyReviewsOnOwnSubmissions() {
        List<TaskReview> reviews = taskReviewService.list(null, null, studentA, 1);

        assertEquals(1, reviews.size(), "学生A 只有一条提交，且它被评过一次");
        assertEquals(subA, reviews.get(0).getSubmissionId(), "不能看到别人提交收到的评审");
    }

    @Test
    @DisplayName("教师看评审列表：只看自己写的，看不到别的教师写的")
    void teacherSeesOnlyOwnReviews() {
        List<TaskReview> reviews = taskReviewService.list(null, null, teacherA, 2);

        assertEquals(1, reviews.size(), "教师A 只写过一条评审");
        assertEquals(teacherA, reviews.get(0).getReviewerId());
    }

    @Test
    @DisplayName("教师即使指定了别人的 reviewerId，也只会得到空列表（条件取交集）")
    void teacherCannotWidenScopeByQueryParam() {
        List<TaskReview> reviews = taskReviewService.list(null, teacherB, teacherA, 2);

        assertTrue(reviews.isEmpty(), "自己写的 ∩ 别人写的 = 空，筛选参数不能突破角色边界");
    }

    @Test
    @DisplayName("用户列表：学生调 → 403（含 role/status/联系方式的名单属管理面数据）")
    void studentCannotListUsers() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.list(null, 1));
        assertEquals(403, ex.getCode().intValue());
    }

    @Test
    @DisplayName("用户详情：学生查别人的账号 → 403，查自己的正常")
    void studentCanOnlyReadSelf() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.getById(teacherA, studentA, 1));
        assertEquals(403, ex.getCode().intValue());

        User self = userService.getById(studentA, studentA, 1);
        assertEquals(studentA, self.getId(), "查自己应当放行");
    }

    @Test
    @DisplayName("用户列表按角色筛选：过滤发生在 SQL 里，Mapper 直接返回子集")
    void userListRoleFilterPushedDownToSql() {
        int total = userMapper.selectByRole(null).size();
        List<User> students = userMapper.selectByRole(1);

        assertFalse(students.isEmpty(), "库里应当有学生");
        assertTrue(students.stream().allMatch(u -> Integer.valueOf(1).equals(u.getRole())),
                "返回集里混进别的角色 = WHERE 没生效");
        assertTrue(students.size() < total,
                "若要取回全表再过滤，这里会等于全量；小于全量说明是数据库筛的");

        List<User> viaService = userService.list(1, 3);
        assertEquals(students.size(), viaService.size(), "Service 只是转调，不该再叠一层内存过滤");
    }

    // ---------- 单条详情：列表收窄了，按 id 单查也必须同样收窄 ----------

    @Test
    @DisplayName("提交详情：学生查自己的 200 内容正确，查同学的 → 403")
    void studentSubmissionDetail() {
        TaskSubmission own = taskSubmissionService.getById(subA, studentA, 1);
        assertEquals(subA, own.getId());
        assertEquals("学生A 的提交", own.getContent(), "详情要拿到真实内容，不能是空壳");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskSubmissionService.getById(subB, studentA, 1));
        assertEquals(403, ex.getCode().intValue(), "列表里看不到的，按 id 也不该读到");
    }

    @Test
    @DisplayName("提交详情：教师查自己项目下的 200，查别教师项目的 → 403，管理员都 200")
    void teacherAndAdminSubmissionDetail() {
        assertEquals(subA, taskSubmissionService.getById(subA, teacherA, 2).getId());
        assertEquals(subB, taskSubmissionService.getById(subB, admin, 3).getId(),
                "管理员是全局视图，两条都能查");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskSubmissionService.getById(subB, teacherA, 2));
        assertEquals(403, ex.getCode().intValue(), "越权读：别的教师项目下的提交");
    }

    @Test
    @DisplayName("评审详情：学生的判定要跨一层（评审 → 提交 → user_id）")
    void studentReviewDetail() {
        assertEquals(revA, taskReviewService.getById(revA, studentA, 1).getId(),
                "针对自己提交的评审应当可读");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskReviewService.getById(revB, studentA, 1));
        assertEquals(403, ex.getCode().intValue(), "同学提交收到的评审不可读");
    }

    @Test
    @DisplayName("评审详情：教师只能读自己写的，别人的 → 403")
    void teacherReviewDetail() {
        assertEquals(revA, taskReviewService.getById(revA, teacherA, 2).getId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskReviewService.getById(revB, teacherA, 2));
        assertEquals(403, ex.getCode().intValue());
    }

    @Test
    @DisplayName("详情查不存在的 id → 404，而不是 data:null 的假成功")
    void detailNotFound() {
        BusinessException sk = assertThrows(BusinessException.class,
                () -> taskSubmissionService.getById(999_999L, admin, 3));
        assertEquals(404, sk.getCode().intValue());

        BusinessException rv = assertThrows(BusinessException.class,
                () -> taskReviewService.getById(999_999L, admin, 3));
        assertEquals(404, rv.getCode().intValue());
    }

    // ---------- 辅助方法 ----------

    private List<Long> 提交id(List<TaskSubmission> list) {
        return list.stream().map(TaskSubmission::getId).sorted().collect(Collectors.toList());
    }

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("读越权测试");
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
        r.setComment("收窄测试评语");
        taskReviewMapper.insert(r);
        return r.getId();
    }
}
