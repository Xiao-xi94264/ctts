package com.ctts.controller;

import com.ctts.common.RequireRole;
import com.ctts.common.Result;
import com.ctts.common.ValidationGroups;
import com.ctts.entity.User;
import com.ctts.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "用户模块 /users", description = "写操作仅管理员（role=3）。GET /users 仅管理员；GET /users/{id} 非管理员只能查自己；POST /users/login 免鉴权")
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    // 查询全部用户，可按角色筛选：GET /users?role=1（仅管理员）
    @Operation(summary = "用户列表", description = "仅管理员，其他角色 403。可选 ?role=1/2/3，过滤下推到 SQL")
    @RequireRole({3})
    @GetMapping
    public Result<List<User>> list(@RequestParam(value = "role", required = false) Integer role,
                                   @RequestAttribute("role") Integer currentRole) {
        return Result.ok(userService.list(role, currentRole));
    }

    // 查询指定id用户：非管理员只能查自己
    @Operation(summary = "用户详情", description = "非管理员查他人 403；用户不存在 404。响应中 password 恒为 null")
    @RequireRole({1, 2, 3})
    @GetMapping("/{id}")
    public Result<User> get(@PathVariable Long id,
                            @RequestAttribute("userId") Long currentUserId,
                            @RequestAttribute("role") Integer currentRole) {
        return Result.ok(userService.getById(id, currentUserId, currentRole));
    }

    // 增加新用户
    // @Validated(Create) 而不是 @Valid：约束都挂在 Create 组，@Valid 只触发 Default 组，
    // 用错等于没校验，password 为 null 会一路走到 BCrypt 抛 IllegalArgumentException → 500
    @Operation(summary = "创建用户（注册）",
            description = "username / password / role 必填，缺哪个 400 指名哪个；密码 BCrypt 入库后回查，响应不含哈希")
    @RequireRole({3})
    @PostMapping
    public Result<User> add(@Validated(ValidationGroups.Create.class) @RequestBody User user) {
        return Result.ok(userService.add(user));
    }

    //用户登录接口
    @Operation(summary = "登录（免鉴权）",
            description = "返回 {user, token}，token 有效期 2 小时。密码错 401；账号被禁用 401")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody User user) {
        return Result.ok(userService.login(user));
    }

    // 更改指定id用户
    @Operation(summary = "部分更新用户",
            description = "只改传了的字段（username / realName / role / phone / email）；password 不在可改范围")
    @RequireRole({3})
    @PutMapping("/{id}")
    public Result<User> update(@PathVariable Long id, @RequestBody User user) {
        return Result.ok(userService.update(id, user));
    }

    // 删除指定id用户
    @Operation(summary = "删除用户", description = "物理删除；仍被项目/提交引用时 400（外键 errno 1451）")
    @RequireRole({3})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok(null);
    }

    // 账号启停（管理员）：PUT /users/6/status?status=0
    // 用 query 参数而不是请求体：改的是"用户的一个子属性"，一个整数不值得再定义一个 DTO
    @Operation(summary = "账号启停",
            description = "?status=0 禁用 / =1 启用；不能操作自己（400）；禁用后该账号手里的旧 token 下一次请求即 401")
    @RequireRole({3})
    @PutMapping("/{id}/status")
    public Result<User> updateStatus(@PathVariable Long id,
                                     @RequestParam("status") Integer status,
                                     @RequestAttribute("userId") Long currentUserId) {
        return Result.ok(userService.updateStatus(id, status, currentUserId));
    }
}
