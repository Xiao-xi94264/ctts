package com.ctts.service.impl;

import com.ctts.common.BusinessException;
import com.ctts.enity.User;
import com.ctts.mapper.UserMapper;
import com.ctts.service.UserService;
import com.ctts.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public List<User> list(Integer role) {
        List<User> users = userMapper.selectAll();
        if(role!=null){
            return users.stream().
                    filter(user -> role.equals(user.getRole()))
                    .toList();
        }
        return users;
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public User add(User user) {
        user.setPassword(ENCODER.encode(user.getPassword()));
        userMapper.insert(user);
        return user;
    }

    @Override
    public User update(Long id, User user) {
        user.setId(id);
        userMapper.update(user);
        return user;
    }

    @Override
    public void delete(Long id) {
        if (userMapper.delete(id) == 0) {
            // 影响行数为 0 说明这条记录根本不存在，删除"成功"是假象
            throw new BusinessException(404, "用户不存在，无法删除");
        }
    }

    @Override
    public Map<String,Object> login(User user) {
        User dbUser = userMapper.selectByUsername(user.getUsername());
        if (dbUser == null || !ENCODER.matches(user.getPassword(), dbUser.getPassword())) {
            // 用户不存在与密码错误合并成同一提示，防账号枚举
            throw new BusinessException(401, "用户名或密码错误");
        }
        dbUser.setPassword(null);
        String token = jwtUtil.generate(dbUser.getId(), dbUser.getRole());
        HashMap<String, Object> result = new HashMap<>();
        result.put("token",token);
        result.put("user",dbUser);
        return result;
    }
}
