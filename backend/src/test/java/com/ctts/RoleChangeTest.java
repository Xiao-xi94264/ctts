package com.ctts;

import com.ctts.entity.User;
import com.ctts.mapper.UserMapper;
import com.ctts.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色变更测试：token 签发之后账号的角色被改，旧 token 必须立即作废。
 *
 * <p>与 {@link AccountStatusTest} 同样的理由不用 {@code @Transactional}：
 * 要用 JdbcTemplate 绕过 MyBatis 的 SqlSession 级缓存去改 role，
 * 事务内共用 SqlSession 会让拦截器读到旧值，测试假失败。
 *
 * <p>这里断言的是 <b>401 而不是 403</b>，这是刻意的语义区分：
 * 403 表示"身份有效、但这件事你不能做"；角色变了意味着"这张凭证不再代表你现在的身份"，
 * 应当重新登录换一张。断言 401 同时能区分三种实现状态——
 * 完全不查（旧 token 照样 200）、只按 token 角色授权（依旧 200）、只按库里角色授权（403）、
 * 比对后作废（401）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleChangeTest {

    private static final String USERNAME = "zz_it_role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    @BeforeEach
    void 准备一个已登录的教师() {
        User u = new User();
        u.setUsername(USERNAME);
        // 本测试不登录，密码只是占位
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("角色变更测试");
        u.setRole(2);
        userMapper.insert(u);
        userId = u.getId();
    }

    @AfterEach
    void 清理测试数据() {
        // 必须先清项目：training_project.teacher_id 外键指向本用户，
        // 直接删用户会被 errno 1451 挡住，残留数据又会让下一条用例撞 username 唯一索引
        jdbcTemplate.update("DELETE FROM training_project WHERE teacher_id = ?", userId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", USERNAME);
    }

    private void 改角色(int role) {
        jdbcTemplate.update("UPDATE sys_user SET role = ? WHERE id = ?", role, userId);
    }

    /** 教师建项目的请求体，够过校验即可，不关心业务结果 */
    private static final String PROJECT_BODY =
            "{\"title\":\"角色变更测试项目\",\"startDate\":\"2026-03-01\",\"endDate\":\"2026-06-30\"}";

    @Test
    @DisplayName("角色未变：教师 token 正常建项目 → 200")
    void unchangedRoleStillWorks() throws Exception {
        String token = jwtUtil.generate(userId, 2);
        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROJECT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("降权后旧 token 立即作废：教师→学生，仍持教师 token 建项目 → 401")
    void demotedUserWithOldTokenIsRejected() throws Exception {
        String token = jwtUtil.generate(userId, 2);

        // 先证明这张 token 在降权前确实能建项目
        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROJECT_BODY))
                .andExpect(status().isOk());

        // token 一个字都没变，变的是库里的 role
        改角色(1);

        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROJECT_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("账号权限已变更，请重新登录"));
    }

    @Test
    @DisplayName("升权后旧 token 同样作废：学生→教师，读接口也要求重登 → 401")
    void promotedUserWithOldTokenIsRejected() throws Exception {
        // 真实地走一遍"先降后升"：学生身份签发 token，随后被提拔为教师
        改角色(1);
        String studentToken = jwtUtil.generate(userId, 1);
        改角色(2);

        // GET 不触发角色授权，只可能被"角色比对"这一层拦住。
        // 若注掉比对而只按 token 里的 role 授权，这里会返回 200
        mockMvc.perform(get("/submissions").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
