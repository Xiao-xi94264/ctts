package com.ctts.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private Long id;
    private String username;

    // 密码只在请求体里出现（注册/登录/改密要传），响应里不该有。
    // accessMode = WRITE_ONLY 就是表达这件事：文档里标"只写"，响应示例中剔除。
    // 注意它只改文档、不改序列化——响应里 password 为 null 是因为 selectById 根本不查这一列。
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    private String realName;
    private Integer role;
    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

