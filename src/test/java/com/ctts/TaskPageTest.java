package com.ctts;

import com.ctts.entity.Project;
import com.ctts.entity.Task;
import com.ctts.entity.User;
import com.ctts.mapper.ProjectMapper;
import com.ctts.mapper.TaskMapper;
import com.ctts.mapper.UserMapper;
import com.ctts.service.TaskService;
import com.ctts.vo.PageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D8：分页必须带总条数，否则前端算不出总页数。
 *
 * 造 5 个任务 + 1 个空项目。若 selectCount 的 <where> 与 selectByPage 不一致，
 * 就会出现"第 1 页 2 条、total 却是 3"这种数字对不上的情况——手工测很难发现。
 *
 * 类上 @Transactional：每个方法结束后强制回滚，不污染开发库。
 */
@SpringBootTest
@Transactional
class TaskPageTest {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private TaskService taskService;

    private Long teacherId;
    /** 5 个任务 */
    private Long projectId;
    /** 0 个任务 */
    private Long emptyProjectId;

    @BeforeEach
    void 造一个有五个任务的项目和一个空项目() {
        teacherId = 新建用户("zz_tp_teacher", 2);
        projectId = 新建项目("分页测试项目");
        emptyProjectId = 新建项目("分页测试空项目");
        for (int i = 1; i <= 5; i++) {
            新建任务(projectId, "任务" + i);
        }
    }

    @Test
    @DisplayName("第 1 页：拿 2 条，total 是 5 —— 两个数字必须同时说清")
    void firstPageReportsTotal() {
        PageVO<Task> page = taskService.page(projectId, 1, 2);

        assertEquals(5, page.getTotal(), "总数应为 5，与本页取了几条无关");
        assertEquals(2, page.getList().size(), "本页应取 2 条");
        assertEquals(1, page.getPage());
        assertEquals(2, page.getPageSize());
    }

    @Test
    @DisplayName("最后一页：只剩 1 条，total 仍是 5")
    void lastPageKeepsTotal() {
        PageVO<Task> page = taskService.page(projectId, 3, 2);

        assertEquals(1, page.getList().size(), "5 条按每页 2 条，第三页只剩 1 条");
        assertEquals(5, page.getTotal(), "翻到最后一页不会改变总数");
    }

    @Test
    @DisplayName("pageSize 超过上限被裁到 100，防止一次把整表拉进内存")
    void pageSizeIsCapped() {
        PageVO<Task> page = taskService.page(projectId, 1, 100_000);

        assertEquals(100, page.getPageSize(), "返回的 pageSize 必须是裁剪后的值，前端才能算对总页数");
    }

    @Test
    @DisplayName("pageSize 传 0 回落到默认 10，不会生成 LIMIT 0")
    void invalidPageSizeFallsBack() {
        PageVO<Task> page = taskService.page(projectId, 1, 0);

        assertEquals(10, page.getPageSize());
        assertEquals(5, page.getList().size(), "只有 5 条，全部返回");
    }

    @Test
    @DisplayName("空项目：total=0 且 list 为空，不查数据页也不报错")
    void emptyProject() {
        PageVO<Task> page = taskService.page(emptyProjectId, 1, 10);

        assertEquals(0, page.getTotal());
        assertTrue(page.getList().isEmpty());
    }

    // ---------- 辅助方法 ----------

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("分页测试");
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

    private void 新建任务(Long pid, String title) {
        Task t = new Task();
        t.setProjectId(pid);
        t.setTitle(title);
        // deadline 是 NOT NULL 列，必须给值
        t.setDeadline(LocalDateTime.of(2026, 12, 1, 18, 0));
        t.setTotalScore(100);
        taskMapper.insert(t);
    }
}
