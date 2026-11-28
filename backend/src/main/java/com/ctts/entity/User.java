package com.ctts.entity;

import com.ctts.common.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private Long id;

    // 以下三个约束都挂在 Create 组：只在 POST /users 生效。
    // 不能挂 Default 组——PUT /users/{id} 是部分更新，只传 role 也该能成功。
    @NotBlank(message = "用户名不能为空", groups = ValidationGroups.Create.class)
    private String username;

    // 密码只在请求体里出现（注册/登录/改密要传），响应里不该有。
    // accessMode = WRITE_ONLY 就是表达这件事：文档里标"只写"，响应示例中剔除。
    // 注意它只改文档、不改序列化——响应里 password 为 null 是因为 selectById 根本不查这一列。
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank(message = "密码不能为空", groups = ValidationGroups.Create.class)
    private String password;

    private String realName;

    // role 是 NOT NULL 列。不校验的话 insert 显式写 null 会被数据库拒成
    // "数据约束校验失败"，看不出到底缺了哪个字段。
    @NotNull(message = "角色不能为空", groups = ValidationGroups.Create.class)
    private Integer role;

    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

