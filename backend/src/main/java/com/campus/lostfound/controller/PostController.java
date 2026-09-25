package com.campus.lostfound.controller;

import com.campus.lostfound.common.PageResult;
import com.campus.lostfound.common.Result;
import com.campus.lostfound.dto.PostDTO;
import com.campus.lostfound.entity.Post;
import com.campus.lostfound.service.PostService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 失物 / 招领帖子接口
 */
@RestController
@RequestMapping("/api/post")
public class PostController {

    @Resource
    private PostService postService;

    @PostMapping
    public Result<Post> publish(@RequestBody PostDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        boolean isAdmin = isAdmin(request);
        return Result.ok(postService.publish(userId, dto, isAdmin));
    }

    @GetMapping("/list")
    public Result<PageResult<Post>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(postService.list(type, category, keyword, location, status, page, size));
    }

    @GetMapping("/detail/{id}")
    public Result<Post> detail(@PathVariable Long id) {
        return Result.ok(postService.detail(id));
    }

    @GetMapping("/my")
    public Result<PageResult<Post>> my(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(postService.myPosts(userId, page, size));
    }

    @PutMapping("/{id}")
    public Result<Post> update(@PathVariable Long id, @RequestBody PostDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        boolean isAdmin = isAdmin(request);
        return Result.ok(postService.update(id, userId, dto, isAdmin));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        postService.delete(id, userId);
        return Result.ok();
    }

    @PostMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        postService.archive(id, userId);
        return Result.ok();
    }

    /**
     * 物品类别列表（公共）
     */
    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.ok(Arrays.asList(
                "手机", "钱包/包", "钥匙", "证件/卡", "书包/背包", "书籍",
                "衣物", "水杯", "眼镜", "耳机", "电脑/电子设备", "雨伞", "其他"));
    }

    /**
     * 文搜图：输入文字描述，返回语义最相似的帖子（跨模态检索）
     */
    @PostMapping("/search")
    public Result<List<Post>> search(@RequestBody Map<String, String> body) {
        String text = body == null ? null : body.get("text");
        if (text == null || text.trim().isEmpty()) {
            return Result.ok(java.util.Collections.emptyList());
        }
        return Result.ok(postService.searchByText(text.trim(), 20));
    }

    /** 当前登录用户是否为管理员（由 JwtInterceptor 写入 role 属性） */
    private boolean isAdmin(HttpServletRequest request) {
        return "ADMIN".equals(request.getAttribute("role"));
    }
}
