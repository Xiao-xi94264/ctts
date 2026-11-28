package com.ctts;

import com.ctts.entity.Project;
import com.ctts.entity.Task;
import com.ctts.entity.User;
import com.ctts.mapper.UserMapper;
import com.ctts.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局时间格式的契约测试。
 *
 * <p>这四条一起才说明问题：
 * 序列化（出参）、反序列化（入参）、HTTP 链路（ObjectMapper 是否真的被 Spring MVC 采用）、
 * 以及「带 T 的写法被拒绝」——最后一条锁住的是「只认一种格式」这个设计选择，
 * 有人想放宽成兼容 ISO 时，必须先改这条测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JacksonTimeFormatTest {

    private static final String TEACHER_USERNAME = "zz_time_fmt_teacher";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private String teacherToken;

    @BeforeEach
    void 准备教师账号() {
        User u = new User();
        u.setUsername(TEACHER_USERNAME);
        u.setPassword("$2a$10$not-a-real-hash-just-for-token-test");
        u.setRealName("时间格式测试教师");
        u.setRole(2);
        userMapper.insert(u);
        teacherToken = jwtUtil.generate(u.getId(), 2);
    }

    @Test
    @DisplayName("LocalDateTime 序列化为「yyyy-MM-dd HH:mm:ss」，且能原样读回")
    void localDateTimeRoundTrip() throws Exception {
        LocalDateTime deadline = LocalDateTime.of(2026, 3, 10, 18, 0, 0);

        Task task = new Task();
        task.setDeadline(deadline);
        String json = objectMapper.writeValueAsString(task);

        assertTrue(json.contains("\"deadline\":\"2026-03-10 18:00:00\""),
                "序列化结果里不该出现 T：" + json);

        Task parsed = objectMapper.readValue("{\"deadline\":\"2026-03-10 18:00:00\"}", Task.class);
        assertEquals(deadline, parsed.getDeadline(), "自己吐出去的格式必须能自己读回来");
    }

    @Test
    @DisplayName("LocalDate 序列化为「yyyy-MM-dd」，不带时间部分")
    void localDateSerialized() throws Exception {
        Project project = new Project();
        project.setStartDate(LocalDate.of(2026, 3, 1));

        String json = objectMapper.writeValueAsString(project);

        assertTrue(json.contains("\"startDate\":\"2026-03-01\""), "日期不该带时间：" + json);
    }

    @Test
    @DisplayName("带 T 的 ISO 写法被拒绝——契约只认一种格式")
    void isoFormatWithTIsRejected() {
        assertThrows(Exception.class,
                () -> objectMapper.readValue("{\"deadline\":\"2026-03-10T18:00:00\"}", Task.class),
                "统一格式后，带 T 的输入应当解析失败");
    }

    @Test
    @DisplayName("POST /projects 的真实响应：请求用空格日期，响应 createTime 不带 T")
    void projectCreateResponseUsesUnifiedFormat() throws Exception {
        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"时间格式契约测试项目\",\"startDate\":\"2026-03-01\",\"endDate\":\"2026-06-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2026-03-01"))
                .andExpect(jsonPath("$.data.createTime")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")));
    }
}
