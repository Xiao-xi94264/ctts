package com.ctts.service.impl;

import com.ctts.common.BusinessException;
import com.ctts.enity.User;
import com.ctts.mapper.UserMapper;
import com.ctts.service.UserService;
import com.ctts.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    // 由 PasswordConfig 的 @Bean 提供；注入接口而非实现类，换算法只改配置类
    @Autowired
    private PasswordEncoder passwordEncoder;

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
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        // insert 只写了 4 列，status/create_time 由数据库默认值填充，不会回填到 Java 对象
        // 回查一次拿数据库真实状态；selectById 不查 password 列，返回体天然不含哈希
        return userMapper.selectById(user.getId());
    }

    @Override
    public User update(Long id, User user) {
        user.setId(id);
        // 所有可更新字段都没传 → <set> 会生成空的 SET，SQL 语法错误，必须在这里拦住
        if (user.getUsername() == null && user.getRealName() == null && user.getRole() == null) {
            throw new BusinessException(400, "没有需要更新的字段");
        }
        userMapper.update(user);
        // 回查拿数据库真实状态；selectById 不查 password 列，返回对象天然不含哈希
        return userMapper.selectById(id);
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
        if (dbUser == null || !passwordEncoder.matches(user.getPassword(), dbUser.getPassword())) {
            // 只记用户名，密码（明文/哈希）一律不进日志
            log.warn("登录失败: username={}", user.getUsername());
            // 用户不存在与密码错误合并成同一提示，防账号枚举
            throw new BusinessException(401, "用户名或密码错误");
        }
        // 密码已确认正确，才谈"这个账号让不让进"；顺序不能反，否则任意密码都能探测账号是否存在
        // 用 equals 而非 != 1：status 为 null 时 != 会自动拆箱抛 NPE
        if (!Integer.valueOf(1).equals(dbUser.getStatus())) {
            log.warn("禁用账号尝试登录: userId={}, username={}", dbUser.getId(), dbUser.getUsername());
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        dbUser.setPassword(null);
        String token = jwtUtil.generate(dbUser.getId(), dbUser.getRole());
        log.info("登录成功: userId={}, username={}, role={}", dbUser.getId(), dbUser.getUsername(), dbUser.getRole());
        HashMap<String, Object> result = new HashMap<>();
        result.put("token",token);
        result.put("user",dbUser);
        return result;
    }
}
