package com.campus.lostfound.controller;

import com.campus.lostfound.common.Result;
import com.campus.lostfound.dto.SubscriptionDTO;
import com.campus.lostfound.entity.Subscription;
import com.campus.lostfound.service.SubscriptionService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 订阅接口（主动触达）
 */
@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    @Resource
    private SubscriptionService subscriptionService;

    /**
     * 新增订阅
     */
    @PostMapping
    public Result<Subscription> add(@RequestBody SubscriptionDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(subscriptionService.add(userId, dto));
    }

    /**
     * 我的订阅列表
     */
    @GetMapping("/my")
    public Result<List<Subscription>> my(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(subscriptionService.myList(userId));
    }

    /**
     * 删除订阅
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        subscriptionService.delete(userId, id);
        return Result.ok();
    }
}
