package com.campus.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.lostfound.common.BusinessException;
import com.campus.lostfound.dto.LoginDTO;
import com.campus.lostfound.dto.ProfileDTO;
import com.campus.lostfound.dto.RegisterDTO;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 用户服务
 */
@Service
public class UserService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Resource
    private UserMapper userMapper;

    public User register(RegisterDTO dto) {
        if (!StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException("用户名和密码不能为空");
        }
        if (dto.getUsername().length() < 3) {
            throw new BusinessException("用户名至少 3 个字符");
        }
        if (dto.getPassword().length() < 6) {
            throw new BusinessException("密码至少 6 位");
        }
        Long cnt = userMapper.selectCount(new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (cnt != null && cnt > 0) {
            throw new BusinessException("用户名已存在");
        }
        User u = new User();
        u.setUsername(dto.getUsername());
        u.setPassword(encoder.encode(dto.getPassword()));
        u.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        u.setPhone(dto.getPhone());
        u.setRole("USER");
        u.setCreateTime(LocalDateTime.now());
        userMapper.insert(u);
        return u;
    }

    public User login(LoginDTO dto) {
        if (!StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException("用户名和密码不能为空");
        }
        User u = userMapper.selectOne(new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (u == null || !encoder.matches(dto.getPassword(), u.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (u.getStatus() != null && u.getStatus() == 1) {
            throw new BusinessException("账号已被注销，请联系管理员");
        }
        return u;
    }

    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 更新个人资料（昵称 / 手机 / 邮箱 / 头像）
     */
    public User updateProfile(Long userId, ProfileDTO dto) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException("用户不存在");
        }
        if (dto.getNickname() != null) {
            String nickname = dto.getNickname().trim();
            if (!StringUtils.hasText(nickname)) {
                throw new BusinessException("昵称不能为空");
            }
            u.setNickname(nickname);
        }
        if (dto.getPhone() != null) {
            u.setPhone(dto.getPhone().trim());
        }
        if (dto.getEmail() != null) {
            u.setEmail(dto.getEmail().trim());
        }
        if (dto.getAvatar() != null) {
            u.setAvatar(dto.getAvatar().trim());
        }
        userMapper.updateById(u);
        u.setPassword(null);
        return u;
    }
}
