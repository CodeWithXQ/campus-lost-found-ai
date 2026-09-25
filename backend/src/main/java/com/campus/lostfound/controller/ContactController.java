package com.campus.lostfound.controller;

import com.campus.lostfound.common.Result;
import com.campus.lostfound.service.ContactService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 一键联系接口
 */
@RestController
@RequestMapping("/api/contact")
public class ContactController {

    @Resource
    private ContactService contactService;

    @PostMapping
    public Result<Void> contact(@RequestParam Long postId,
                                @RequestParam Long otherPostId,
                                HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        contactService.contact(userId, postId, otherPostId);
        return Result.ok();
    }
}
