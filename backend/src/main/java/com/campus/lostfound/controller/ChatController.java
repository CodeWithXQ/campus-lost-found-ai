package com.campus.lostfound.controller;

import com.campus.lostfound.common.Result;
import com.campus.lostfound.dto.ChatSendDTO;
import com.campus.lostfound.entity.ChatMessage;
import com.campus.lostfound.service.ChatService;
import com.campus.lostfound.vo.ChatVO;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 私聊接口
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    /** 发送消息 */
    @PostMapping("/send")
    public Result<ChatMessage> send(HttpServletRequest request, @RequestBody ChatSendDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(chatService.send(userId, dto));
    }

    /** 与某人在某个物品上下文下的历史消息 */
    @GetMapping("/history")
    public Result<List<ChatMessage>> history(HttpServletRequest request, @RequestParam Long peerId,
                                             @RequestParam(required = false) Long postId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(chatService.history(userId, peerId, postId));
    }

    /** 会话列表 */
    @GetMapping("/conversations")
    public Result<List<ChatVO>> conversations(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(chatService.conversations(userId));
    }

    /** 未读消息总数 */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(chatService.unreadCount(userId));
    }

    /** 标记与某人在某个物品上下文下的会话已读 */
    @PutMapping("/read")
    public Result<Void> markRead(HttpServletRequest request, @RequestParam Long peerId,
                                 @RequestParam(required = false) Long postId) {
        Long userId = (Long) request.getAttribute("userId");
        chatService.markRead(userId, peerId, postId);
        return Result.ok();
    }
}
