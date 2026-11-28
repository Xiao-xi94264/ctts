package com.ctts.service;

import com.ctts.entity.User;

import java.util.List;
import java.util.Map;

public interface UserService {
    /** currentRole：只有管理员能看用户列表 */
    List<User> list(Integer role, Integer currentRole);

    /** currentRole + currentUserId：非管理员只能看自己 */
    User getById(Long id, Long currentUserId, Integer currentRole);

    User add(User user);

    User update(Long id, User user);

    void delete(Long id);

    Map<String,Object> login(User user);

    /** 账号启停：status 只接受 0/1；不能操作自己；用户不存在返回 404 */
    User updateStatus(Long id, Integer status, Long currentUserId);
}

