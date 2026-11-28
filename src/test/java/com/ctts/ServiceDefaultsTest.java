package com.ctts;

import com.ctts.entity.Project;
import com.ctts.entity.Task;
import com.ctts.entity.TaskSubmission;
import com.ctts.entity.User;
import com.ctts.mapper.ProjectMapper;
import com.ctts.mapper.TaskMapper;
import com.ctts.mapper.UserMapper;
import com.ctts.service.ProjectService;
import com.ctts.service.TaskService;
import com.ctts.service.TaskSubmissionService;
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

/**
 * add 的返回值形状测试：插入之后返回给前端的对象，必须带上数据库生成的字段。
 *
 * 这条不变量最容易被忽略。insert 只写业务列，status / create_time / update_time
 * 由数据库默认值生成、不会回填 Java 对象；若 add 里直接 return 入参，
 * 前端收到的 status 就是 null，看起来像"没写成功"，而库里其实一切正常。
 *
 * 同时锁住任务总分的两条边界：不传补 100、传了就尊重传入值。
 * 前者防 errno 1048（静态列清单会把 null 显式写进 NOT NULL 列，DDL 的 DEFAULT 不参与），
 * 后者防"补默认值"写成无条件覆盖。
 *
 * 造数一律走 Mapper 而非被测的 Service：前置数据不能依赖被测方法，
 * 否则 add 一坏，@BeforeEach 先抛异常，所有用例变成 error 而不是 fail，诊断信息全丢。
 * 类上 @Transactional 会让每个方法结束后强制回滚，不污染开发库。
 */
@SpringBootTest
@Transactional
class ServiceDefaultsTest {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private ProjectService projectService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskSubmissionService taskSubmissionService;

    private Long teacherId;
    private Long studentId;
    private Long projectId;
    private Long taskId;

    @BeforeEach
    void 准备数据() {
        teacherId = 新建用户("zz_sd_teacher", 2);
        studentId = 新建用户("zz_sd_student", 1);
        projectId = 新建项目();
        taskId = 新建任务();
    }

    @Test
    @DisplayName("新建项目：返回的 status 与两个时间戳是库里的真实值，不是 null")
    void projectAddReturnsDbGeneratedFields() {
        Project input = new Project();
        input.setTitle("默认值测试项目");
        input.setTeacherId(teacherId);
        input.setStartDate(LocalDate.of(2026, 9, 1));
        input.setEndDate(LocalDate.of(2026, 12, 31));

        Project saved = projectService.add(input);

        assertNotNull(saved.getId(), "主键应由 useGeneratedKeys 回填");
        assertEquals(Integer.valueOf(1), saved.getStatus(),
                "status 由 DDL 默认值生成，add 不回查就会是 null");
        assertNotNull(saved.getCreateTime(), "createTime 由 DDL 生成，不回查就会是 null");
        assertNotNull(saved.getUpdateTime(), "updateTime 同理");
    }

    @Test
    @DisplayName("新建任务：不传 totalScore 时补 100，且时间戳有值")
    void taskAddFillsDefaultTotalScore() {
        Task input = new Task();
        input.setProjectId(projectId);
        input.setTitle("默认分测试任务");
        input.setDeadline(LocalDateTime.of(2026, 12, 1, 18, 0));
        // 刻意不设置 totalScore

        Task saved = taskService.add(input);

        assertEquals(Integer.valueOf(100), saved.getTotalScore(),
                "不传总分应补 100；若补成 null，insert 会把 NULL 显式写进 NOT NULL 列（errno 1048）");
        assertNotNull(saved.getCreateTime(), "回查后才有数据库生成的时间");
        assertNotNull(saved.getUpdateTime(), "updateTime 同理");
    }

    @Test
    @DisplayName("新建任务：显式传了总分就尊重传入值，不被默认 100 覆盖")
    void taskAddKeepsExplicitTotalScore() {
        Task input = new Task();
        input.setProjectId(projectId);
        input.setTitle("自定义总分任务");
        input.setDeadline(LocalDateTime.of(2026, 12, 1, 18, 0));
        input.setTotalScore(60);

        Task saved = taskService.add(input);

        assertEquals(Integer.valueOf(60), saved.getTotalScore(),
                "补默认值必须只在 null 时生效，写成无条件 setTotalScore(100) 会把用户的 60 抹掉");
    }

    @Test
    @DisplayName("提交任务：返回的 status 与 submitTime 来自数据库，不是 null")
    void submissionAddReturnsDbGeneratedFields() {
        TaskSubmission input = new TaskSubmission();
        input.setTaskId(taskId);
        input.setUserId(studentId);
        input.setContent("默认值测试提交");

        TaskSubmission saved = taskSubmissionService.add(input);

        assertNotNull(saved.getSubmitTime(), "submitTime 由 DDL 生成，不回查就会是 null");
        assertEquals(Integer.valueOf(1), saved.getStatus(),
                "新提交的 status 恒为 1（待评审），由 DDL 默认值决定");
    }

    // ---------- 辅助方法：只用 Mapper 造前置数据，绕开被测的 add ----------

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        // 本测试不登录，密码只是占位，必须非空以满足列约束
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("返回值形状测试");
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }

    private Long 新建项目() {
        Project p = new Project();
        p.setTitle("默认值测试前置项目");
        p.setTeacherId(teacherId);
        p.setStartDate(LocalDate.of(2026, 9, 1));
        p.setEndDate(LocalDate.of(2026, 12, 31));
        projectMapper.insert(p);
        return p.getId();
    }

    private Long 新建任务() {
        Task t = new Task();
        t.setProjectId(projectId);
        t.setTitle("默认值测试前置任务");
        // deadline 与 total_score 都是 NOT NULL，Mapper 的静态列清单必须给值
        t.setDeadline(LocalDateTime.of(2026, 12, 1, 18, 0));
        t.setTotalScore(100);
        taskMapper.insert(t);
        return t.getId();
    }
}
