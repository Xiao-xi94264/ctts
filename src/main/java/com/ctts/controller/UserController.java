package com.ctts.controller;

import com.ctts.common.Result;
import com.ctts.entity.User;
import com.ctts.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    // 查询全部用户，可按角色筛选：GET /users?role=1（仅管理员）
    @GetMapping
    public Result<List<User>> list(@RequestParam(value = "role", required = false) Integer role,
                                   @RequestAttribute("role") Integer currentRole) {
        return Result.ok(userService.list(role, currentRole));
    }

    // 查询指定id用户：非管理员只能查自己
    @GetMapping("/{id}")
    public Result<User> get(@PathVariable Long id,
                            @RequestAttribute("userId") Long currentUserId,
                            @RequestAttribute("role") Integer currentRole) {
        return Result.ok(userService.getById(id, currentUserId, currentRole));
    }

    // 增加新用户
    @PostMapping
    public Result<User> add(@RequestBody User user) {
        return Result.ok(userService.add(user));
    }

    //用户登录接口
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody User user) {
        return Result.ok(userService.login(user));
    }

    // 更改指定id用户
    @PutMapping("/{id}")
    public Result<User> update(@PathVariable Long id, @RequestBody User user) {
        return Result.ok(userService.update(id, user));
    }

    // 删除指定id用户
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok(null);
    }

    // 账号启停（管理员）：PUT /users/6/status?status=0
    // 用 query 参数而不是请求体：改的是"用户的一个子属性"，一个整数不值得再定义一个 DTO
    @PutMapping("/{id}/status")
    public Result<User> updateStatus(@PathVariable Long id,
                                     @RequestParam("status") Integer status,
                                     @RequestAttribute("userId") Long currentUserId) {
        return Result.ok(userService.updateStatus(id, status, currentUserId));
    }
}
