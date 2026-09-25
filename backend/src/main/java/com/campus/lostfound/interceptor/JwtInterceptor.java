package com.campus.lostfound.interceptor;

import com.campus.lostfound.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT 登录拦截器
 * 说明：/api/notification/stream（SSE）无法携带请求头，支持 token 作为查询参数传入
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 静态资源 / 非处理器方法直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            token = request.getParameter("token");
        }
        if (token == null || token.isEmpty()) {
            writeError(response, 401, "未登录或登录已过期");
            return false;
        }
        try {
            Claims claims = jwtUtil.parse(token);
            request.setAttribute("userId", Long.valueOf(claims.getSubject()));
            request.setAttribute("username", String.valueOf(claims.get("username")));
            request.setAttribute("role", String.valueOf(claims.get("role")));
            return true;
        } catch (Exception e) {
            writeError(response, 401, "登录状态无效，请重新登录");
            return false;
        }
    }

    private void writeError(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + msg + "\",\"data\":null}");
    }
}
