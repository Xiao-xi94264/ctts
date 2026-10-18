package com.ctts.controller;

import com.ctts.enity.User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {
    //增加新用户
    @PostMapping
    public User add(@RequestBody User user) {
        // TODO: 接入 Service/Mapper 持久化
        return user;
    }

    //删除指定id用户
    @DeleteMapping("/{id}")
    public String delete(@PathVariable long id) {
        // TODO: 接入 Service/Mapper 持久化
        return "deleted " + id;
    }

    //更改指定id用户
    @PutMapping("/{id}")
    public String update(@PathVariable long id, @RequestBody User user) {
        // TODO: 接入 Service/Mapper 持久化
        return "updated " + id;
    }

    //查询用户
    @GetMapping("/{id}")
    public User get(@PathVariable long id) {
        // TODO: 接入 Service/Mapper 持久化
        return null;
    }
}
