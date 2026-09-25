package com.campus.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.lostfound.common.BusinessException;
import com.campus.lostfound.dto.SubscriptionDTO;
import com.campus.lostfound.entity.Post;
import com.campus.lostfound.entity.Subscription;
import com.campus.lostfound.mapper.SubscriptionMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订阅服务：用户订阅某类型/类别/地点的帖子，新帖子发布时主动推送提醒。
 * <p>
 * 解决"被动等待"痛点——传统失物招领需用户反复刷新查看，订阅机制把"找"变成"等通知"。
 */
@Service
public class SubscriptionService {

    @Resource
    private SubscriptionMapper subscriptionMapper;

    @Resource
    private NotificationService notificationService;

    /**
     * 新增订阅
     */
    public Subscription add(Long userId, SubscriptionDTO dto) {
        if (dto.getType() == null || (!"LOST".equals(dto.getType()) && !"FOUND".equals(dto.getType()))) {
            throw new BusinessException("订阅类型错误");
        }
        Subscription s = new Subscription();
        s.setUserId(userId);
        s.setType(dto.getType());
        s.setCategory(StringUtils.hasText(dto.getCategory()) ? dto.getCategory().trim() : null);
        s.setLocation(StringUtils.hasText(dto.getLocation()) ? dto.getLocation().trim() : null);
        s.setCreateTime(LocalDateTime.now());
        subscriptionMapper.insert(s);
        return s;
    }

    /**
     * 我的订阅列表
     */
    public List<Subscription> myList(Long userId) {
        return subscriptionMapper.selectList(new QueryWrapper<Subscription>()
                .eq("user_id", userId)
                .orderByDesc("create_time"));
    }

    /**
     * 删除订阅
     */
    public void delete(Long userId, Long id) {
        Subscription s = subscriptionMapper.selectById(id);
        if (s == null) {
            throw new BusinessException("订阅不存在");
        }
        if (!s.getUserId().equals(userId)) {
            throw new BusinessException("无权删除他人的订阅");
        }
        subscriptionMapper.deleteById(id);
    }

    /**
     * 新帖子发布后，通知匹配的订阅用户（主动触达）。
     * 匹配规则：类型相同 且 （订阅类别为空 或 等于帖子类别）且 （订阅地点为空 或 帖子地点包含关键词）。
     */
    public void checkAndNotify(Post post) {
        if (post == null || post.getUserId() == null) {
            return;
        }
        List<Subscription> subs = subscriptionMapper.selectList(new QueryWrapper<Subscription>()
                .eq("type", post.getType())
                .ne("user_id", post.getUserId()));
        if (subs.isEmpty()) {
            return;
        }
        String typeText = "LOST".equals(post.getType()) ? "失物" : "招领";
        for (Subscription s : subs) {
            if (StringUtils.hasText(s.getCategory()) && !s.getCategory().equals(post.getCategory())) {
                continue;
            }
            if (StringUtils.hasText(s.getLocation())
                    && (post.getLocation() == null || !post.getLocation().contains(s.getLocation()))) {
                continue;
            }
            notificationService.notifyUser(s.getUserId(), "SUBSCRIBE",
                    "订阅提醒",
                    "有新的" + typeText + "信息发布：《" + post.getTitle() + "》，与你订阅的条件匹配，快去看看吧！",
                    post.getId());
        }
    }
}
