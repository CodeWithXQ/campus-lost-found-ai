package com.campus.lostfound.controller;

import com.campus.lostfound.common.Result;
import com.campus.lostfound.service.AiClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 图像能力接口（代理 Python 服务，带降级返回）
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Resource
    private AiClient aiClient;

    /**
     * 上传图片 → AI 自动识别物品类别
     */
    @PostMapping("/classify")
    public Result<Map<String, Object>> classify(@RequestParam("file") MultipartFile file) throws IOException {
        Map<String, Object> resp = aiClient.classifyImage(file.getBytes());
        if (resp == null) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("available", false);
            fallback.put("category", null);
            fallback.put("confidence", 0);
            return Result.ok(fallback);
        }
        resp.put("available", true);
        return Result.ok(resp);
    }

    /**
     * 上传图片 → OCR 识别文字（学号/卡号等），用于证件卡类信息提取
     */
    @PostMapping("/ocr")
    public Result<Map<String, Object>> ocr(@RequestParam("file") MultipartFile file) throws IOException {
        String text = aiClient.ocr(file.getBytes());
        Map<String, Object> m = new HashMap<>();
        if (text == null) {
            m.put("available", false);
            m.put("text", "");
        } else {
            m.put("available", true);
            m.put("text", text);
        }
        return Result.ok(m);
    }

    /**
     * AI 服务健康检查（前端可显示当前是否处于 AI 模式）
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> m = new HashMap<>();
        m.put("available", aiClient.isAvailable());
        return Result.ok(m);
    }
}
