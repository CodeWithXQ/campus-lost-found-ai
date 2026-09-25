package com.campus.lostfound.controller;

import com.campus.lostfound.common.PageResult;
import com.campus.lostfound.common.Result;
import com.campus.lostfound.entity.Notification;
import com.campus.lostfound.service.NotificationService;
import com.campus.lostfound.service.SseRegistry;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 站内通知接口
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    @Resource
    private NotificationService notificationService;

    @Resource
    private SseRegistry sseRegistry;

    @GetMapping("/list")
    public Result<PageResult<Notification>> list(HttpServletRequest request,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                 @RequestParam(required = false) String type) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(notificationService.page(userId, page, size, unreadOnly, type));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount(HttpServletRequest request,
                                    @RequestParam(required = false) String type) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(notificationService.unreadCount(userId, type));
    }

    @PutMapping("/read/{id}")
    public Result<Void> read(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        notificationService.markRead(userId, id);
        return Result.ok();
    }

    @PutMapping("/read-all")
    public Result<Void> readAll(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        notificationService.markAllRead(userId);
        return Result.ok();
    }

    /**
     * SSE 实时通知推送（EventSource 无法带请求头，token 通过查询参数传入）
     */
    @GetMapping("/stream")
    public SseEmitter stream(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return sseRegistry.connect(userId);
    }
}
