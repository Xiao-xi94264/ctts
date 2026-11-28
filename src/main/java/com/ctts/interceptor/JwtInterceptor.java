package com.ctts.interceptor;

import com.ctts.common.Result;
import com.ctts.entity.User;
import com.ctts.mapper.UserMapper;
import com.ctts.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Objects;

@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    // Spring Boot 已按 application.yaml 的 jackson 配置装配好这个单例 Bean，注入即复用同一套序列化规则
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            log.warn("未携带 token 被拦截: {} {}", request.getMethod(), request.getRequestURI());
            writeError(response, 401, "未登录：请在 Authorization 请求头中携带 Bearer <token>");
            return false;
        }
        Long userId;
        Integer role;
        try {
            Claims claims = jwtUtil.parse(header.substring(7));
            userId = Long.valueOf(claims.getSubject());
            role = claims.get("role", Integer.class);
        } catch (Exception e) {
            // 只记异常摘要，不记 token 本身
            log.warn("token 校验失败: {} {} - {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            writeError(response, 401, "登录状态无效或已过期，请重新登录");
            return false;
        }

        // 验签通过只说明"这串 token 是我们签的"，不代表"这个账号现在还能用"。
        // 禁用/删除都发生在签发之后，只有回查一次库才看得见。
        // 注意此处刻意放在 try 之外：数据库故障抛出的 DataAccessException 应走全局异常处理的 500，
        // 若被上面那个 catch 吃掉，就会误报成"登录失效"，用户重登多少次都没用。
        User dbUser = userMapper.selectById(userId);
        if (dbUser == null) {
            log.warn("token 指向的账号不存在: userId={} {} {}", userId, request.getMethod(), request.getRequestURI());
            writeError(response, 401, "账号不存在，请重新登录");
            return false;
        }
        if (!Integer.valueOf(1).equals(dbUser.getStatus())) {
            log.warn("禁用账号持旧 token 访问: userId={} {} {}", userId, request.getMethod(), request.getRequestURI());
            writeError(response, 401, "账号已被禁用，请联系管理员");
            return false;
        }

        // 角色同样会在签发后被改。token 里的 role 是签发那一刻的快照，验签只证明
        // "这串 token 是我们签的"，证明不了"它自称的角色现在还对"。
        // 不一致就作废这张凭证：让前端重新登录去换一张新的，而不是拿旧身份继续用。
        Integer dbRole = dbUser.getRole();
        if (!Objects.equals(dbRole, role)) {
            log.warn("账号角色已变更，旧 token 作废: userId={} tokenRole={} dbRole={} {} {}",
                    userId, role, dbRole, request.getMethod(), request.getRequestURI());
            writeError(response, 401, "账号权限已变更，请重新登录");
            return false;
        }

        // 身份校验全部通过后才挂到 request 上，后面的接口与角色校验直接取
        request.setAttribute("userId", userId);
        // 授权依据取库里的角色而不是 token 里的：走到这里两者必然相等，
        // 但"以库为准"让角色的唯一真相留在数据库，将来放宽比对逻辑也不会走偏
        request.setAttribute("role", dbRole);
        // 已认证，再判断授权，角色权限不对返回 403
        if (!checkRole(request, dbRole)) {
            log.warn("权限不足: {} {} role={}", request.getMethod(), request.getRequestURI(), dbRole);
            writeError(response, 403, "权限不足：当前角色不能执行该操作");
            return false;
        }
        return true;
    }

    /**
     * 拦截器不走 @RestControllerAdvice（这里没有抛异常，只是 return false），
     * 所以必须自己把统一结构的 JSON 写进响应流。
     */
    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
    }

    // 授权规则：查询放行；写操作按角色限制
    private boolean checkRole(HttpServletRequest request, Integer role) {
        if ("GET".equals(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        int need;
        if (uri.startsWith("/users")) {
            need = 3;              // 用户增删改：管理员
        } else if (uri.startsWith("/submissions")) {
            need = 1;              // 提交成果：学生
        } else if (uri.startsWith("/projects") || uri.startsWith("/tasks") || uri.startsWith("/reviews")) {
            need = 2;              // 项目/任务/评审：教师
        } else {
            return true;
        }
        return role != null && role == need;
    }
}
