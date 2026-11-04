package com.ctts.service.impl;

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
        userMapper.delete(id);
    }

    @Override
    public Map<String,Object> login(User user) {
        User dbUser = userMapper.selectByUsername(user.getUsername());
        if (dbUser == null || !ENCODER.matches(user.getPassword(), dbUser.getPassword())) {
            return null;
        }
        dbUser.setPassword(null);
        String token = jwtUtil.generate(dbUser.getId(), dbUser.getRole());
        HashMap<String, Object> result = new HashMap<>();
        result.put("token",token);
        result.put("user",dbUser);
        return result;
    }
}
