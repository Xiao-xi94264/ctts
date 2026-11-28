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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账号启停接口 PUT /users/{id}/status。
 *
 * 类上不加 @Transactional：本类要验证的是"改库之后，拦截器能不能立刻看见"。
 * 在测试事务里，MyBatis 的一级缓存作用域是 SESSION，
 * 用另一条路径改 status 未必被感知——那会把真实行为测错（AccountStatusTest 踩过这个坑）。
 * 因此改为真实提交 + @AfterEach 清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserStatusApiTest {

    private static final String TARGET_USERNAME = "zz_us_target";
    private static final String ADMIN_USERNAME = "zz_us_admin";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JwtUtil jwtUtil;

    private Long targetId;
    private Long adminId;

    @BeforeEach
    void 造一个教师和一个管理员() {
        清理残留();
        targetId = 新建用户(TARGET_USERNAME, "启停测试教师", 2);
        adminId = 新建用户(ADMIN_USERNAME, "启停测试管理员", 3);
    }

    @AfterEach
    void 清理数据() {
        清理残留();
    }

    @Test
    @DisplayName("禁用后 status 变 0，且该账号手里的旧 token 下一次请求立刻 401")
    void disableMakesOldTokenInvalid() throws Exception {
        String targetToken = jwtUtil.generate(targetId, 2);
        String adminToken = jwtUtil.generate(adminId, 3);

        // 前置：启用状态下 token 可用（GET /tasks 对任意登录角色开放）
        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/users/{id}/status", targetId).param("status", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));

        // 关键断言：不需要等 token 过期，拦截器每次回查 status 就能拦下
        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("账号已被禁用，请联系管理员"));
    }

    @Test
    @DisplayName("重新启用后 status 回到 1，旧 token 又能用了")
    void reenableRestoresAccess() throws Exception {
        String targetToken = jwtUtil.generate(targetId, 2);
        String adminToken = jwtUtil.generate(adminId, 3);

        mockMvc.perform(put("/users/{id}/status", targetId).param("status", "0")
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/users/{id}/status", targetId).param("status", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));

        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("status 传 2 → 400，只有 0/1 两个合法值")
    void invalidStatusRejected() throws Exception {
        String adminToken = jwtUtil.generate(adminId, 3);

        mockMvc.perform(put("/users/{id}/status", targetId).param("status", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("管理员不能修改自己的启停状态 → 400（防止把自己锁在门外）")
    void cannotDisableSelf() throws Exception {
        String adminToken = jwtUtil.generate(adminId, 3);

        mockMvc.perform(put("/users/{id}/status", adminId).param("status", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("启停不存在的用户 → 404")
    void unknownUserReturns404() throws Exception {
        String adminToken = jwtUtil.generate(adminId, 3);

        mockMvc.perform(put("/users/{id}/status", 999_999L).param("status", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("教师调启停接口 → 403（/users 下写操作属管理员）")
    void teacherForbidden() throws Exception {
        String targetToken = jwtUtil.generate(targetId, 2);

        mockMvc.perform(put("/users/{id}/status", targetId).param("status", "0")
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ---------- 辅助方法 ----------

    private Long 新建用户(String username, String realName, Integer role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName(realName);
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }

    /** 这两个账号由本测试独占，清理时按用户名定位，避免误删开发库里的真实数据 */
    private void 清理残留() {
        for (String username : new String[]{TARGET_USERNAME, ADMIN_USERNAME}) {
            User u = userMapper.selectByUsername(username);
            if (u != null) {
                userMapper.hardDelete(u.getId());
            }
        }
    }
}
