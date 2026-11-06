package com.ctts.controller;

import com.ctts.common.Result;
import com.ctts.enity.User;
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

    // 查询全部用户，可按角色筛选：GET /users?role=1
    @GetMapping
    public Result<List<User>> list(@RequestParam(value = "role", required = false) Integer role) {
        return Result.ok(userService.list(role));
    }

    // 查询指定id用户
    @GetMapping("/{id}")
    public Result<User> get(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
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
}
