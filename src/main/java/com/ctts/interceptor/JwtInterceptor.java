package com.ctts.interceptor;

import com.ctts.common.Result;
import com.ctts.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    // Spring Boot 已按 application.yaml 的 jackson 配置装配好这个单例 Bean，注入即复用同一套序列化规则
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response, 401, "未登录：请在 Authorization 请求头中携带 Bearer <token>");
            return false;
        }
        Integer role;
        try {
            Claims claims = jwtUtil.parse(header.substring(7));
            // 把身份挂到 request 上，后面的接口和角色校验直接取，不用再解析
            request.setAttribute("userId", Long.valueOf(claims.getSubject()));
            role = claims.get("role", Integer.class);
            request.setAttribute("role", role);
        } catch (Exception e) {
            writeError(response, 401, "登录状态无效或已过期，请重新登录");
            return false;
        }
        // 已认证，再判断授权，角色权限不对返回 403
        if (!checkRole(request, role)) {
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
