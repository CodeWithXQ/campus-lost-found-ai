package com.campus.lostfound.controller;

import com.campus.lostfound.common.PageResult;
import com.campus.lostfound.common.Result;
import com.campus.lostfound.entity.ClaimRecord;
import com.campus.lostfound.entity.Post;
import com.campus.lostfound.service.AdminService;
import com.campus.lostfound.service.ClaimService;
import com.campus.lostfound.service.PostService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 管理员接口（受 AdminInterceptor 保护，仅 ADMIN 可访问）
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Resource
    private AdminService adminService;

    @Resource
    private ClaimService claimService;

    @Resource
    private PostService postService;

    /**
     * 回填历史帖子的文本语义向量（textVector 为空的帖子），统一为 512 维，恢复语义检索。
     */
    @PostMapping("/fill-text-vectors")
    public Result<Map<String, Object>> fillTextVectors() {
        int filled = postService.fillMissingTextVectors();
        return Result.ok(Map.of("filled", filled));
    }

    /**
     * 分页查看全部帖子（含已下架/已归档）
     */
    @GetMapping("/posts")
    public Result<PageResult<Post>> posts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(adminService.pagePosts(type, status, keyword, page, size));
    }

    /**
     * 待审核帖子队列（管理员审核界面）
     */
    @GetMapping("/posts/pending")
    public Result<PageResult<Post>> pendingPosts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(adminService.pagePendingPosts(type, keyword, page, size));
    }

    /**
     * 待审核帖子数量（导航栏角标）
     */
    @GetMapping("/posts/pending/count")
    public Result<Long> pendingCount() {
        return Result.ok(adminService.countPending());
    }

    /**
     * 下架任意帖子
     */
    @PostMapping("/post/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        adminService.archivePost(id);
        return Result.ok();
    }

    /**
     * 重新上架已归档帖子（恢复展示）
     */
    @PostMapping("/post/{id}/relist")
    public Result<Void> relist(@PathVariable Long id) {
        adminService.relistPost(id);
        return Result.ok();
    }

    /**
     * 审核通过：待审核 → 展示中（触发匹配 + 订阅提醒）
     */
    @PostMapping("/post/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        adminService.approvePost(id);
        return Result.ok();
    }

    /**
     * 审核拒绝：待审核 → 审核未通过（reason 可选，落库并通知）
     */
    @PostMapping("/post/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        adminService.rejectPost(id, reason);
        return Result.ok();
    }

    /**
     * 删除任意帖子（含清理关联数据）
     */
    @DeleteMapping("/post/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminService.deletePost(id);
        return Result.ok();
    }

    /**
     * 全系统认领记录（分页 + 可选状态筛选），供认领管理页「全部认领记录」分类
     */
    @GetMapping("/claims")
    public Result<PageResult<ClaimRecord>> claims(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {
        return Result.ok(claimService.allClaims(page, size, status));
    }

    /**
     * 用户列表（含帖子数），分页
     */
    @GetMapping("/users")
    public Result<PageResult<Map<String, Object>>> users(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(adminService.listUsers(page, size));
    }

    /**
     * 注销用户（禁用登录）
     */
    @PostMapping("/user/{id}/disable")
    public Result<Void> disableUser(@PathVariable Long id) {
        adminService.disableUser(id);
        return Result.ok();
    }

    /**
     * 恢复已注销用户
     */
    @PostMapping("/user/{id}/enable")
    public Result<Void> enableUser(@PathVariable Long id) {
        adminService.enableUser(id);
        return Result.ok();
    }

    /**
     * 删除用户（级联清理关联数据）
     */
    @DeleteMapping("/user/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return Result.ok();
    }
}
