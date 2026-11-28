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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 创建用户的必填校验（待办 #26）。
 *
 * <p>修复前的行为：不传 password → {@code passwordEncoder.encode(null)} 抛
 * {@code IllegalArgumentException} 落到兜底 → 500；不传 username/role → 数据库
 * NOT NULL 拒绝 → 400 但提示是"数据约束校验失败"，看不出缺哪个字段。
 *
 * <p>修复方式：三个约束挂 {@code ValidationGroups.Create} 组，`POST /users` 用
 * {@code @Validated(Create)} 触发。**不能挂 Default 组**——PUT 是部分更新，
 * 只传一个字段就该过，所以 update 一律不加 @Valid。最后一条用例就是守这个边界。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserCreateValidTest {

    private static final String ADMIN_USERNAME = "zz_ucv_admin";
    private static final String CREATED_USERNAME = "zz_ucv_created";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private Long adminId;

    @BeforeEach
    void 造一个管理员() {
        清理残留();
        adminId = 新建用户(ADMIN_USERNAME, "校验测试管理员", 3);
    }

    @AfterEach
    void 清理数据() {
        清理残留();
    }

    @Test
    @DisplayName("不传 password → 400 且指名 password（修复前是 500）")
    void missingPasswordReturns400() throws Exception {
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + CREATED_USERNAME + "\",\"realName\":\"无密码\",\"role\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("password: 密码不能为空"));
    }

    @Test
    @DisplayName("不传 username → 400 且指名 username（修复前是「数据约束校验失败」）")
    void missingUsernameReturns400() throws Exception {
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"123456\",\"realName\":\"无用户名\",\"role\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("username: 用户名不能为空"));
    }

    @Test
    @DisplayName("不传 role → 400 且指名 role")
    void missingRoleReturns400() throws Exception {
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + CREATED_USERNAME + "\",\"password\":\"123456\",\"realName\":\"无角色\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("role: 角色不能为空"));
    }

    @Test
    @DisplayName("三个必填都传 → 200，返回体 password 为 null（不含哈希）")
    void completeBodySucceeds() throws Exception {
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + CREATED_USERNAME + "\",\"password\":\"123456\","
                                + "\"realName\":\"正常创建\",\"role\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.username").value(CREATED_USERNAME))
                // 字段还在，值为 null：selectById 不查 password 列，所以 BCrypt 哈希出不来
                .andExpect(jsonPath("$.data.password").value(nullValue()));
    }

    @Test
    @DisplayName("回归：PUT 只传 realName 仍 200 —— Create 组不介入部分更新")
    void partialUpdateStillWorks() throws Exception {
        mockMvc.perform(put("/users/{id}", adminId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"只改这一项\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.realName").value("只改这一项"))
                .andExpect(jsonPath("$.data.username").value(ADMIN_USERNAME));
    }

    // ---------- 辅助方法 ----------

    private String adminToken() {
        return jwtUtil.generate(adminId, 3);
    }

    private Long 新建用户(String username, String realName, Integer role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName(realName);
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }

    /** 只清本测试独占的两个用户名，避免误删开发库里的真实数据 */
    private void 清理残留() {
        for (String username : new String[]{ADMIN_USERNAME, CREATED_USERNAME}) {
            User u = userMapper.selectByUsername(username);
            if (u != null) {
                userMapper.delete(u.getId());
            }
        }
    }
}
