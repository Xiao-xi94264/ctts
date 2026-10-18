package com.ctts.controller;

import com.ctts.enity.User;
import com.ctts.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    private static final Map<Long, User> USERS = new LinkedHashMap<>();
    private static final AtomicLong ID_GEN = new AtomicLong(4);

    static {
        USERS.put(1L, new User(1L, "zhangsan", "张三", 1));
        USERS.put(2L, new User(2L, "lisi", "李四", 1));
        USERS.put(3L, new User(3L, "wanglaoshi", "王老师", 2));
    }

    // 查询全部用户，可按角色筛选：GET /users?role=1
    @GetMapping
    public List<User> list(@RequestParam(value = "role", required = false) Integer role) {
        List<User> users = userMapper.selectAll();
        if (role != null) {
            return users.stream()
                    .filter(user -> role.equals(user.getRole()))
                    .toList();
        }
        return users;
    }

    // 查询指定id用户
    @GetMapping("/{id}")
    public User get(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        return user;
    }

    // 增加新用户
    @PostMapping
    public User add(@RequestBody User user) {
        long id = ID_GEN.incrementAndGet();
        user.setId(id);
        USERS.put(id, user);
        return user;
    }

    // 更改指定id用户
    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        USERS.put(id, user);
        return user;
    }

    // 删除指定id用户
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        USERS.remove(id);
        return "deleted " + id;
    }
}
