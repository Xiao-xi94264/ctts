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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账号状态测试：token 签发之后账号被禁用，旧 token 必须立即失效。
 *
 * <p><b>这个类为什么不用 {@code @Transactional}（与另两个测试类不同）</b>：
 * MyBatis 一级缓存的作用域是 SqlSession，而事务内的所有 Mapper 调用共用一个 SqlSession。
 * 若测试包在事务里，用 JdbcTemplate 改 status 不会让 MyBatis 的缓存失效，
 * 拦截器会读到缓存中的旧 status，测试就会假失败。
 * 去掉事务后每次请求都是独立 SqlSession（与生产一致），改用 {@code @AfterEach} 清理数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountStatusTest {

    private static final String USERNAME = "zz_it_status";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * UserMapper.update 的 SQL 刻意不含 status 列（防止普通资料接口改启停），
     * 所以测试里要用 JdbcTemplate 直接执行 SQL，故意造出"业务接口进不去"的状态。
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long studentId;
    private String token;

    @BeforeEach
    void 准备一个已登录的学生() {
        User u = new User();
        u.setUsername(USERNAME);
        // 本测试不登录，密码只是占位
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("账号状态测试");
        u.setRole(1);
        userMapper.insert(u);
        studentId = u.getId();
        // 模拟"禁用动作发生之前就签发好的 token"
        token = jwtUtil.generate(studentId, 1);
    }

    @AfterEach
    void 清理测试数据() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", USERNAME);
    }

    private void 改状态(int status) {
        jdbcTemplate.update("UPDATE sys_user SET status = ? WHERE id = ?", status, studentId);
    }

    @Test
    @DisplayName("账号正常时旧 token 可用 → 200")
    void enabledUserCanAccess() throws Exception {
        mockMvc.perform(get("/submissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("账号被禁用后，已签发的 token 立即失效 → 401")
    void disabledUserWithOldTokenIsRejected() throws Exception {
        // 先证明这个 token 在禁用前确实有效
        mockMvc.perform(get("/submissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 禁用：token 本身一个字都没变
        改状态(0);

        mockMvc.perform(get("/submissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("账号已被禁用，请联系管理员"));
    }

    @Test
    @DisplayName("账号恢复启用后，同一个 token 又能用 → 证明每请求实时查库")
    void reEnabledUserCanAccessAgain() throws Exception {
        改状态(0);
        mockMvc.perform(get("/submissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        改状态(1);
        // 如果拦截器是"登录时查一次就记住"，这一步仍然是 401；返回 200 才说明每请求都查库
        mockMvc.perform(get("/submissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
