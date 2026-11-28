package com.ctts.service.impl;

import com.ctts.common.BusinessException;
import com.ctts.entity.User;
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
import java.util.Objects;

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
    public List<User> list(Integer role, Integer currentRole) {
        // GET 在拦截器里一律放行，所以"谁能看这份名单"必须在这里判。
        // 用户列表含 role / status / phone / email，对学生和教师来说是管理面数据。
        if (!Integer.valueOf(3).equals(currentRole)) {
            throw new BusinessException(403, "只有管理员可以查看用户列表");
        }
        // 角色筛选下推到 SQL。原先是 selectAll() 再用 stream 过滤——
        // 等于整表搬到内存、丢掉大半，数据量一大就是纯浪费，而且 pageSize 之类后续优化也无从谈起
        return userMapper.selectByRole(role);
    }

    @Override
    public User getById(Long id, Long currentUserId, Integer currentRole) {
        // 非管理员只能看自己：别人的 role/status/联系方式不属于公开信息
        if (!Integer.valueOf(3).equals(currentRole) && !Objects.equals(id, currentUserId)) {
            throw new BusinessException(403, "只能查看自己的账号信息");
        }
        User user = userMapper.selectById(id);
        // 权限通过不代表记录存在。这里原先直接 return，null 被 Result.ok 包成
        // "200 + data:null"，文档却写着 404——前端只能看到"查到了但什么都没有"。
        // 逻辑删除（第㊵步）让这个偏差更明显：已删用户也会这样返回
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
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
        // 可改字段清单必须与 UserMapper.xml 的 <set> 保持一致，否则漏一个就变成"传了也被判空"
        if (user.getUsername() == null && user.getRealName() == null && user.getRole() == null
                && user.getPhone() == null && user.getEmail() == null) {
            throw new BusinessException(400, "没有需要更新的字段");
        }
        userMapper.update(user);
        // 回查拿数据库真实状态；selectById 不查 password 列，返回对象天然不含哈希
        return userMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        if (userMapper.softDelete(id) == 0) {
            // 影响行数为 0 说明这条记录根本不存在，删除"成功"是假象
            throw new BusinessException(404, "用户不存在，无法删除");
        }
    }

    @Override
    public User updateStatus(Long id, Integer status, Long currentUserId) {
        // 只允许两个取值。用 equals 而非 ==：status 为 null 时 == 会拆箱抛 NPE
        if (!Integer.valueOf(0).equals(status) && !Integer.valueOf(1).equals(status)) {
            throw new BusinessException(400, "status 只能是 0（禁用）或 1（启用）");
        }
        // 允许管理员禁用自己 = 允许把系统唯一的出口锁死。这类"自锁"必须在入口拦住
        if (Objects.equals(id, currentUserId)) {
            throw new BusinessException(400, "不能修改当前登录账号的启停状态");
        }
        // 先查判存在，而不是看 update 影响行数：MySQL 在"值没变"时可能返回 0 行，
        // 那会把"重复禁用同一账号"误报成"用户不存在"
        User existing = userMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userMapper.updateStatus(id, status);
        // 禁用之后，该账号手里的旧 token 会在下一次请求被拦截器拒掉（拦截器每次都回查 status）
        log.info("账号启停: targetId={} {}→{} operatorId={}",
                id, existing.getStatus(), status, currentUserId);
        return userMapper.selectById(id);
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
