package com.ctts.interceptor;

import com.ctts.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(401);
            return false;
        }
        Integer role;
        try {
            Claims claims = jwtUtil.parse(header.substring(7));
            // 把身份挂到 request 上，后面的接口和角色校验直接取，不用再解析
            request.setAttribute("userId", Long.valueOf(claims.getSubject()));
            role = claims.get("role",Integer.class);
            request.setAttribute("role", claims.get("role", Integer.class));
        } catch (Exception e) {
            response.setStatus(401);
            return false;
        }
        //已认证，在判断授权，角色权限不对返回403
        if(!checkRole(request,role)){
            response.setStatus(403);
            return false;
        }
        return true;

    }
    // 授权规则：查询放行；写操作按角色限制
        private boolean checkRole(HttpServletRequest request,Integer role){
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
