package com.ctts.service;

import com.ctts.enity.User;

import java.util.List;
import java.util.Map;

public interface UserService {
    List<User> list(Integer role);

    User getById(Long id);

    User add(User user);

    User update(Long id, User user);

    void delete(Long id);

    Map<String,Object> login(User user);
}

