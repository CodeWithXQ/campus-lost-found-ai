package com.campus.lostfound.controller;

import com.campus.lostfound.common.PageResult;
import com.campus.lostfound.common.Result;
import com.campus.lostfound.dto.ClaimDTO;
import com.campus.lostfound.entity.ClaimRecord;
import com.campus.lostfound.service.ClaimService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 认领接口
 */
@RestController
@RequestMapping("/api/claim")
public class ClaimController {

    @Resource
    private ClaimService claimService;

    /**
     * 失主发起认领申请
     */
    @PostMapping("/apply")
    public Result<ClaimRecord> apply(@RequestBody ClaimDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(claimService.apply(userId, dto));
    }

    /**
     * 我相关的认领记录，分页。
     * scope 可选：applied=我申请的 / handle=待我处理的；缺省为合并两者。
     */
    @GetMapping("/my")
    public Result<PageResult<ClaimRecord>> my(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String scope,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(claimService.myClaims(userId, page, size, scope));
    }

    /**
     * 认领详情（申请人、拾主或管理员可查看）
     */
    @GetMapping("/{id}")
    public Result<ClaimRecord> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        return Result.ok(claimService.detail(userId, role, id));
    }

    /**
     * 拾主确认认领（帖子自动下架）
     */
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        claimService.confirm(userId, id);
        return Result.ok();
    }

    /**
     * 拾主拒绝认领
     */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        claimService.reject(userId, id);
        return Result.ok();
    }
}
