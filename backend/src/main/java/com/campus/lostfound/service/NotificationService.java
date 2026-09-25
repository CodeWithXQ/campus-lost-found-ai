package com.campus.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.lostfound.common.BusinessException;
import com.campus.lostfound.common.PageResult;
import com.campus.lostfound.entity.Notification;
import com.campus.lostfound.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 站内通知服务
 */
@Service
public class NotificationService {

    @Resource
    private NotificationMapper notificationMapper;

    @Resource
    private SseRegistry sseRegistry;

    /**
     * 发送通知并实时推送
     */
    public void notifyUser(Long userId, String type, String title, String content, Long relatedId) {
        notifyUser(userId, type, title, content, relatedId, null);
    }

    /**
     * 发送通知并实时推送（携带额外关联 id）
     */
    public void notifyUser(Long userId, String type, String title, String content, Long relatedId, Long extraId) {
        if (userId == null) {
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRelatedId(relatedId);
        n.setExtraId(extraId);
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(n);
        sseRegistry.push(userId, n);
    }

    public PageResult<Notification> page(Long userId, int page, int size, boolean unreadOnly, String type) {
        QueryWrapper<Notification> qw = new QueryWrapper<Notification>()
                .eq("user_id", userId)
                .orderByDesc("create_time");
        if (unreadOnly) {
            qw.eq("is_read", 0);
        }
        if (type != null && !type.isEmpty()) {
            // 支持逗号分隔的多类型，如 CLAIM,CONTACT
            String[] types = type.split(",");
            qw.in("type", (Object[]) types);
        }
        Page<Notification> p = notificationMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(p);
    }

    public long unreadCount(Long userId, String type) {
        QueryWrapper<Notification> qw = new QueryWrapper<Notification>()
                .eq("user_id", userId)
                .eq("is_read", 0);
        if (type != null && !type.isEmpty()) {
            // 与消息列表口径一致：支持逗号分隔的多类型
            String[] types = type.split(",");
            qw.in("type", (Object[]) types);
        }
        Long c = notificationMapper.selectCount(qw);
        return c == null ? 0 : c;
    }

    public void markRead(Long userId, Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null) {
            throw new BusinessException("通知不存在");
        }
        if (!n.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该通知");
        }
        n.setIsRead(1);
        notificationMapper.updateById(n);
    }

    public void markAllRead(Long userId) {
        // 单条 SQL 批量置已读，避免逐条 updateById
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }
}
