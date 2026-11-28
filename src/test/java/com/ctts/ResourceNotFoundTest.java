package com.ctts;

import com.ctts.common.BusinessException;
import com.ctts.entity.Project;
import com.ctts.entity.Task;
import com.ctts.entity.TaskReview;
import com.ctts.entity.User;
import com.ctts.mapper.UserMapper;
import com.ctts.service.ProjectService;
import com.ctts.service.TaskReviewService;
import com.ctts.service.TaskService;
import com.ctts.service.TaskSubmissionService;
import com.ctts.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * D7：更新 / 删除一个不存在的资源，必须返回 404，而不是"成功"。
 *
 * 修复前的行为：DELETE /projects/999999 返回 200 + data:null。
 * 调用方无法区分"删掉了"和"本来就没有"，前端会显示"删除成功"然后列表数量不变。
 *
 * 修复方式统一为「先查再写」：一次 selectById 就能分清三件事——
 * 记录不存在（404）、存在但不是我的（403）、正常（继续）。
 * 只看影响行数做不到，因为后两种都是 0 行。
 *
 * 类上 @Transactional：每个方法结束后强制回滚，不污染开发库。
 */
@SpringBootTest
@Transactional
class ResourceNotFoundTest {

    private static final Long NOT_FOUND_ID = 999_999L;

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskSubmissionService taskSubmissionService;
    @Autowired
    private TaskReviewService taskReviewService;
    @Autowired
    private UserService userService;

    private Long teacherId;
    private Long studentId;

    @BeforeEach
    void 造两个账号() {
        teacherId = 新建用户("zz_nf_teacher", 2);
        studentId = 新建用户("zz_nf_student", 1);
    }

    @Test
    @DisplayName("更新不存在的项目 → 404")
    void updateUnknownProject() {
        Project patch = new Project();
        patch.setTitle("试图改一个不存在的项目");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.update(NOT_FOUND_ID, patch, teacherId));
        assertEquals(404, ex.getCode().intValue(), "不存在的项目应报 404，而不是静默成功");
    }

    @Test
    @DisplayName("删除不存在的项目 → 404")
    void deleteUnknownProject() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.delete(NOT_FOUND_ID, teacherId));
        assertEquals(404, ex.getCode().intValue());
    }

    @Test
    @DisplayName("更新不存在的任务 → 404")
    void updateUnknownTask() {
        Task patch = new Task();
        patch.setTitle("试图改一个不存在的任务");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.update(NOT_FOUND_ID, patch, teacherId));
        assertEquals(404, ex.getCode().intValue());
    }

    @Test
    @DisplayName("删除不存在的任务 → 404")
    void deleteUnknownTask() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.delete(NOT_FOUND_ID, teacherId));
        assertEquals(404, ex.getCode().intValue());
    }

    @Test
    @DisplayName("删除不存在的提交 → 404")
    void deleteUnknownSubmission() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskSubmissionService.delete(NOT_FOUND_ID, studentId));
        assertEquals(404, ex.getCode().intValue());
    }

    @Test
    @DisplayName("更新不存在的评审 → 404")
    void updateUnknownReview() {
        TaskReview patch = new TaskReview();
        patch.setScore(90);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskReviewService.update(NOT_FOUND_ID, patch, teacherId));
        assertEquals(404, ex.getCode().intValue());
    }

    @Test
    @DisplayName("删除不存在的用户 → 404（这条在 v1.0 就已修，一并纳入回归）")
    void deleteUnknownUser() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.delete(NOT_FOUND_ID));
        assertEquals(404, ex.getCode().intValue());
    }

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        // 本测试不登录，密码只是占位，必须非空以满足列约束
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("不存在的资源测试");
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }
}
