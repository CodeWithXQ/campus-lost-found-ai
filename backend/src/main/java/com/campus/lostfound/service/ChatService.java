package com.campus.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.lostfound.common.BusinessException;
import com.campus.lostfound.dto.ChatSendDTO;
import com.campus.lostfound.entity.ChatMessage;
import com.campus.lostfound.entity.Post;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.mapper.ChatMessageMapper;
import com.campus.lostfound.mapper.PostMapper;
import com.campus.lostfound.mapper.UserMapper;
import com.campus.lostfound.vo.ChatVO;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 私聊服务
 */
@Service
public class ChatService {

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PostMapper postMapper;

    @Resource
    private SseRegistry sseRegistry;

    /**
     * 发送消息并实时推送给对方
     */
    public ChatMessage send(Long userId, ChatSendDTO dto) {
        if (dto.getReceiverId() == null) {
            throw new BusinessException("接收人不能为空");
        }
        if (dto.getReceiverId().equals(userId)) {
            throw new BusinessException("不能给自己发消息");
        }
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.isEmpty()) {
            throw new BusinessException("消息内容不能为空");
        }
        if (content.length() > 500) {
            throw new BusinessException("消息内容过长");
        }
        if (userMapper.selectById(dto.getReceiverId()) == null) {
            throw new BusinessException("对方不存在");
        }
        ChatMessage m = new ChatMessage();
        m.setConversationId(conversationId(userId, dto.getReceiverId(), dto.getPostId()));
        m.setSenderId(userId);
        m.setReceiverId(dto.getReceiverId());
        m.setContent(content);
        m.setPostId(dto.getPostId());
        m.setIsRead(0);
        m.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(m);
        sseRegistry.push(dto.getReceiverId(), "chat", m);
        return m;
    }

    /**
     * 与某人在某个物品上下文下的历史消息（正序）
     */
    public List<ChatMessage> history(Long userId, Long peerId, Long postId) {
        return chatMessageMapper.selectList(new QueryWrapper<ChatMessage>()
                .eq("conversation_id", conversationId(userId, peerId, postId))
                .orderByAsc("create_time"));
    }

    /**
     * 我的会话列表（按最后消息时间倒序）
     */
    public List<ChatVO> conversations(Long userId) {
        List<ChatMessage> all = chatMessageMapper.selectList(new QueryWrapper<ChatMessage>()
                .and(w -> w.eq("sender_id", userId).or().eq("receiver_id", userId))
                .orderByDesc("create_time"));
        // 倒序遍历，每个会话第一次遇到即为最新一条
        Map<String, ChatMessage> latestMap = new LinkedHashMap<>();
        Map<String, Integer> unreadMap = new HashMap<>();
        for (ChatMessage m : all) {
            latestMap.putIfAbsent(m.getConversationId(), m);
            if (m.getReceiverId().equals(userId) && (m.getIsRead() == null || m.getIsRead() == 0)) {
                unreadMap.merge(m.getConversationId(), 1, Integer::sum);
            }
        }
        List<ChatVO> result = new ArrayList<>();
        for (ChatMessage m : latestMap.values()) {
            Long peerId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
            ChatVO vo = new ChatVO();
            vo.setPeerId(peerId);
            User peer = userMapper.selectById(peerId);
            if (peer != null) {
                vo.setPeerName(peer.getNickname());
                vo.setPeerAvatar(peer.getAvatar());
            }
            vo.setPostId(m.getPostId());
            if (m.getPostId() != null) {
                Post post = postMapper.selectById(m.getPostId());
                if (post != null) {
                    vo.setPostTitle(post.getTitle());
                }
            }
            vo.setLastContent(m.getContent());
            vo.setLastTime(m.getCreateTime());
            vo.setUnread(unreadMap.getOrDefault(m.getConversationId(), 0));
            result.add(vo);
        }
        return result;
    }

    /**
     * 我的未读消息总数
     */
    public long unreadCount(Long userId) {
        Long c = chatMessageMapper.selectCount(new QueryWrapper<ChatMessage>()
                .eq("receiver_id", userId)
                .eq("is_read", 0));
        return c == null ? 0 : c;
    }

    /**
     * 标记与某人在某个物品上下文下的会话已读
     */
    public void markRead(Long userId, Long peerId, Long postId) {
        String conv = conversationId(userId, peerId, postId);
        List<ChatMessage> unread = chatMessageMapper.selectList(new QueryWrapper<ChatMessage>()
                .eq("conversation_id", conv)
                .eq("receiver_id", userId)
                .eq("is_read", 0));
        for (ChatMessage m : unread) {
            m.setIsRead(1);
            chatMessageMapper.updateById(m);
        }
    }

    /** 会话ID：两用户ID排序后拼接 + 帖子ID，构成「用户对 + 物品」三元组（无帖子时记 0），保证双方一致 */
    private String conversationId(Long a, Long b, Long postId) {
        long lo = a < b ? a : b;
        long hi = a < b ? b : a;
        long p = postId == null ? 0 : postId;
        return lo + "_" + hi + "_" + p;
    }
}
