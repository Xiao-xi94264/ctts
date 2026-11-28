package com.ctts;

import com.ctts.entity.User;
import com.ctts.mapper.UserMapper;
import com.ctts.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户字段完整性测试（D6）。
 *
 * <p>补列这件事有个容易漏的前提：只把 {@code phone/email} 加进 SELECT 是没用的——
 * insert 不写这两列，查出来永远是 null，只是把"隐式 null"变成"显式 null"。
 * 所以三条用例分别锁 SELECT、insert、update 三个环节：
 * 任何一处漏改，都会有一条变红。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserFieldsTest {

    private static final String ADMIN_USERNAME = "zz_it_admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;

    @BeforeEach
    void 准备一个管理员() {
        User admin = new User();
        admin.setUsername(ADMIN_USERNAME);
        admin.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        admin.setRealName("用户字段测试管理员");
        admin.setRole(3);
        userMapper.insert(admin);
        adminToken = jwtUtil.generate(admin.getId(), 3);
    }

    @Test
    @DisplayName("POST /users 带上 phone/email → 响应原样返回，且 createTime/updateTime 有值")
    void createUserReturnsContactAndTime() throws Exception {
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zz_it_fields_create\",\"password\":\"123456\",\"realName\":\"带联系方式\",\"role\":1,\"phone\":\"13900000001\",\"email\":\"create@ctts.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("13900000001"))
                .andExpect(jsonPath("$.data.email").value("create@ctts.test"))
                .andExpect(jsonPath("$.data.createTime").isNotEmpty())
                .andExpect(jsonPath("$.data.updateTime").isNotEmpty());
    }

    @Test
    @DisplayName("GET /users/{id} → 联系方式与注册时间都不再是 null")
    void queryUserReturnsAllColumns() throws Exception {
        Long id = 造一个带联系方式的用户();

        mockMvc.perform(get("/users/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("13900000002"))
                .andExpect(jsonPath("$.data.email").value("query@ctts.test"))
                .andExpect(jsonPath("$.data.createTime").isNotEmpty())
                // 没查 password 列，响应里不应出现哈希
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("PUT /users/{id} 只改 phone → 不被判成空更新，且改动落库")
    void updateOnlyPhone() throws Exception {
        Long id = 造一个带联系方式的用户();

        mockMvc.perform(put("/users/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900000009\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("13900000009"))
                // 其余字段没传，应当保持原值
                .andExpect(jsonPath("$.data.realName").value("字段测试用户"));
    }

    /** 走 Mapper 造数：手机号要能写进库，这本身就依赖 insert 已补上 phone/email 列 */
    private Long 造一个带联系方式的用户() {
        User u = new User();
        u.setUsername("zz_it_fields_query");
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("字段测试用户");
        u.setRole(1);
        u.setPhone("13900000002");
        u.setEmail("query@ctts.test");
        userMapper.insert(u);
        return u.getId();
    }
}
