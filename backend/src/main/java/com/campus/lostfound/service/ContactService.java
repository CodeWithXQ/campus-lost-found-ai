package com.campus.lostfound.service;

import com.campus.lostfound.common.BusinessException;
import com.campus.lostfound.entity.Notification;
import com.campus.lostfound.entity.Post;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.mapper.PostMapper;
import com.campus.lostfound.mapper.UserMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 一键联系服务：失主 / 拾主联系匹配到的对方，发送站内通知。
 */
@Service
public class ContactService {

    @Resource
    private PostMapper postMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private NotificationService notificationService;

    /**
     * 一键联系对方帖子发布者。
     * 丢失者与拾到者发送不同内容的通知。
     *
     * @param userId      当前用户
     * @param postId      我自己的帖子（当前详情页）
     * @param otherPostId 要联系的对方帖子
     */
    public void contact(Long userId, Long postId, Long otherPostId) {
        Post mine = postMapper.selectById(postId);
        if (mine == null || !mine.getUserId().equals(userId)) {
            throw new BusinessException("只能联系自己帖子匹配到的对方");
        }
        Post other = postMapper.selectById(otherPostId);
        if (other == null) {
            throw new BusinessException("对方帖子不存在");
        }
        User me = userMapper.selectById(userId);
        String myName = me == null ? "用户" : me.getNickname();

        String title;
        String content;
        if ("LOST".equals(mine.getType())) {
            // 我是失主，联系的是拾主
            title = "失主联系了你";
            content = "失主「" + myName + "」想联系您，关于您拾到的《" + other.getTitle() + "》，请及时查看并回复";
        } else {
            // 我是拾主，联系的是失主
            title = "拾主联系了你";
            content = "拾主「" + myName + "」想联系您，关于您丢失的《" + other.getTitle() + "》，请及时查看并回复";
        }
        // relatedId 指向发起人的帖子（对方帖子，供对方点击跳转），extraId 指向对方自己的帖子（供详情页过滤匹配）
        notificationService.notifyUser(other.getUserId(), Notification.TYPE_CONTACT, title, content, mine.getId(), other.getId());
    }
}
