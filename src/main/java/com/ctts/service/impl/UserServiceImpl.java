package com.ctts.service.impl;

import com.ctts.enity.User;
import com.ctts.mapper.UserMapper;
import com.ctts.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

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
    public User login(User user) {
        User dbUser = userMapper.selectByUsername(user.getUsername());
        if (dbUser == null || !ENCODER.matches(user.getPassword(), dbUser.getPassword())) {
            return null;
        }
        dbUser.setPassword(null);
        return dbUser;
    }
}
