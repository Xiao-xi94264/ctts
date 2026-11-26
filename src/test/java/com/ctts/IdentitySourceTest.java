package com.ctts;

import com.ctts.enity.User;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 身份来源测试：写接口的归属人只能来自 token，请求体里填的 id 必须被忽略。
 * 类上的 @Transactional 会让每个测试方法结束后强制回滚，不污染开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IdentitySourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private Long teacherId;
    private Long studentId;

    @BeforeEach
    void 准备两个账号() {
        teacherId = 新建用户("zz_it_teacher", 2);
        studentId = 新建用户("zz_it_student", 1);
    }

    private Long 新建用户(String username, Integer role) {
        User u = new User();
        u.setUsername(username);
        // 本测试不登录，密码只是占位，必须非空以满足列约束
        u.setPassword("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        u.setRealName("身份来源测试");
        u.setRole(role);
        userMapper.insert(u);
        return u.getId();
    }

    @Test
    @DisplayName("建项目时请求体伪造 teacherId → 被忽略，记录的是 token 里的教师")
    void forgedTeacherIdIsIgnored() throws Exception {
        String token = jwtUtil.generate(teacherId, 2);
        // 故意把 teacherId 写成学生的 id，模拟前端篡改
        String body = "{\"title\":\"身份伪造验证\",\"teacherId\":" + studentId
                + ",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-06-30\"}";

        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 关键断言：库里记下的是教师本人，不是请求体里的学生
                .andExpect(jsonPath("$.data.teacherId").value(teacherId.intValue()));
    }

    @Test
    @DisplayName("建项目时不传 teacherId → 依然成功（已不校验必填）")
    void teacherIdMayBeOmitted() throws Exception {
        String token = jwtUtil.generate(teacherId, 2);
        String body = "{\"title\":\"不传归属人\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-06-30\"}";

        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherId").value(teacherId.intValue()));
    }
}
