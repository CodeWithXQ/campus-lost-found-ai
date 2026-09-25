package com.campus.lostfound.controller;

import com.campus.lostfound.common.Result;
import com.campus.lostfound.service.MatchService;
import com.campus.lostfound.vo.MatchVO;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 智能匹配接口
 */
@RestController
@RequestMapping("/api/match")
public class MatchController {

    @Resource
    private MatchService matchService;

    /**
     * 查看某帖子的匹配结果（含匹配依据）
     */
    @GetMapping("/post/{postId}")
    public Result<List<MatchVO>> matchForPost(@PathVariable Long postId) {
        return Result.ok(matchService.matchForPost(postId));
    }

    /**
     * 我相关的全部匹配
     */
    @GetMapping("/my")
    public Result<List<MatchVO>> my(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(matchService.myMatches(userId));
    }

    /**
     * 重新执行匹配
     */
    @PostMapping("/run/{postId}")
    public Result<List<MatchVO>> rerun(@PathVariable Long postId) {
        matchService.rerun(postId);
        return Result.ok(matchService.matchForPost(postId));
    }

    /**
     * 忽略某条匹配（负反馈，后续不再推荐）
     */
    @PostMapping("/ignore/{matchId}")
    public Result<Void> ignore(@PathVariable Long matchId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        matchService.ignore(matchId, userId);
        return Result.ok();
    }
}
