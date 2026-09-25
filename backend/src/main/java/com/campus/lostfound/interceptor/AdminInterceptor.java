package com.campus.lostfound.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 管理员权限拦截器
 * <p>
 * 拦截管理类接口（/api/admin/**、/api/stats/**），校验当前登录用户角色是否为 ADMIN。
 * 依赖 {@link JwtInterceptor} 先解析 Token 并写入 role 属性，故需在 JwtInterceptor 之后注册。
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            writeError(response, 403, "无管理员权限");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + msg + "\",\"data\":null}");
    }
}
