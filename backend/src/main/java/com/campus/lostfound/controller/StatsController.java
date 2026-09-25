package com.campus.lostfound.controller;

import com.campus.lostfound.common.Result;
import com.campus.lostfound.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 数据看板统计接口
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Resource
    private StatsService statsService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(statsService.overview());
    }

    @GetMapping("/category")
    public Result<List<Map<String, Object>>> category() {
        return Result.ok(statsService.categoryStats());
    }

    @GetMapping("/location")
    public Result<List<Map<String, Object>>> location() {
        return Result.ok(statsService.locationStats());
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend() {
        return Result.ok(statsService.trend());
    }

    @GetMapping("/success-rate")
    public Result<Map<String, Object>> successRate() {
        return Result.ok(statsService.successRate());
    }
}
