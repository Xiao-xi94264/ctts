package com.ctts.controller;

import com.ctts.enity.User;
import com.ctts.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private UserMapper userMapper;

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
        return userMapper.selectById(id);
    }

    // 增加新用户
    @PostMapping
    public User add(@RequestBody User user) {
        user.setPassword(ENCODER.encode(user.getPassword()));
        userMapper.insert(user);
        return user;
    }

    // 更改指定id用户
    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        userMapper.update(user);
        return user;
    }

    // 删除指定id用户
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        userMapper.delete(id);
        return "deleted " + id;
    }

    //用户登录接口
    @PostMapping("/login")
    public User login(@RequestBody User user){
        User dbUser = userMapper.selectByUsername(user.getUsername());

        if(dbUser == null||!ENCODER.matches(user.getPassword(),dbUser.getPassword())){
            return null;
        }
        dbUser.setPassword(null);
        return dbUser;
    }
}
