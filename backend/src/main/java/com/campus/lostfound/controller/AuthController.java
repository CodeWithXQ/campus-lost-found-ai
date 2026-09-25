package com.campus.lostfound.controller;

import com.campus.lostfound.common.Result;
import com.campus.lostfound.dto.LoginDTO;
import com.campus.lostfound.dto.ProfileDTO;
import com.campus.lostfound.dto.RegisterDTO;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.service.UserService;
import com.campus.lostfound.util.JwtUtil;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private UserService userService;

    @Resource
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO dto) {
        User u = userService.login(dto);
        String token = jwtUtil.generateToken(u.getId(), u.getUsername(), u.getRole());
        u.setPassword(null);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", u);
        return Result.ok(data);
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<User> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        User u = userService.getById(userId);
        if (u != null) {
            u.setPassword(null);
        }
        return Result.ok(u);
    }

    /**
     * 更新个人资料（昵称 / 手机 / 邮箱 / 头像）
     */
    @PutMapping("/me")
    public Result<User> updateProfile(@RequestBody ProfileDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userService.updateProfile(userId, dto));
    }
}
