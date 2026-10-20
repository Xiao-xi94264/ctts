package com.ctts.service;

import com.ctts.enity.User;

import java.util.List;

public interface UserService {
    List<User> list(Integer role);

    User getById(Long id);

    User add(User user);

    User update(Long id, User user);

    void delete(Long id);

    User login(User user);
}

