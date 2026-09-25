package com.campus.lostfound.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 图像服务客户端（调用 Python FastAPI 服务）
 * <p>
 * 降级策略：AI 服务不可用时自动切换为纯文字匹配，系统核心功能不受影响；
 * 进入冷却期（cooldown-ms）后自动恢复探测。
 */
@Slf4j
@Component
public class AiClient {

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.timeout-ms:8000}")
    private int timeoutMs;

    @Value("${ai.cooldown-ms:30000}")
    private long cooldownMs;

    @Resource
    private RestTemplate restTemplate;

    private final AtomicBoolean available = new AtomicBoolean(true);
    private volatile long lastFailTime = 0;

    /**
     * 是否可用（冷却期过后自动重试）
     */
    public boolean isAvailable() {
        if (available.get()) {
            return true;
        }
        return System.currentTimeMillis() - lastFailTime > cooldownMs;
    }

    private void markDown() {
        available.set(false);
        lastFailTime = System.currentTimeMillis();
        log.warn("AI 图像服务不可用，已自动切换为纯文字匹配降级模式");
    }

    private void markUp() {
        if (!available.get()) {
            log.info("AI 图像服务已恢复");
        }
        available.set(true);
    }

    /**
     * 图片特征提取，返回 960 维向量；失败返回 null
     */
    public double[] embedImage(byte[] imageBytes) {
        Map<String, Object> resp = postMultipart("/embed", imageBytes);
        if (resp == null) {
            return null;
        }
        Object emb = resp.get("embedding");
        if (!(emb instanceof List)) {
            return null;
        }
        List<?> list = (List<?>) emb;
        double[] vec = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            vec[i] = ((Number) list.get(i)).doubleValue();
        }
        return vec;
    }

    /**
     * 物品自动分类，返回 {category, confidence}；失败返回 null
     */
    public Map<String, Object> classifyImage(byte[] imageBytes) {
        return postMultipart("/classify", imageBytes);
    }

    /**
     * 图片清晰度评估，返回 0~1（越高越清晰）；失败返回 -1
     */
    public double imageQuality(byte[] imageBytes) {
        Map<String, Object> resp = postMultipart("/quality", imageBytes);
        if (resp == null || !(resp.get("quality") instanceof Number)) {
            return -1;
        }
        return ((Number) resp.get("quality")).doubleValue();
    }

    /**
     * 图片文字识别（OCR），返回识别文本；失败或未安装 OCR 时返回 null
     */
    public String ocr(byte[] imageBytes) {
        Map<String, Object> resp = postMultipart("/ocr", imageBytes);
        if (resp == null) {
            return null;
        }
        if (Boolean.FALSE.equals(resp.get("available"))) {
            return null;
        }
        Object text = resp.get("text");
        return text == null ? null : text.toString();
    }

    /**
     * 图片描述生成（BLIP + 英译中），返回中文描述；失败或未加载时返回 null
     */
    public String caption(byte[] imageBytes) {
        Map<String, Object> resp = postMultipart("/caption", imageBytes);
        if (resp == null) {
            return null;
        }
        if (Boolean.FALSE.equals(resp.get("available"))) {
            return null;
        }
        Object text = resp.get("caption");
        if (text == null || text.toString().isEmpty()) {
            return null;
        }
        return text.toString();
    }

    /**
     * 文本语义向量提取（Chinese-CLIP 文本塔，与图像同空间），失败返回 null
     */
    public double[] embedText(String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        Map<String, Object> resp = postJson("/embed-text", body);
        if (resp == null) {
            return null;
        }
        Object emb = resp.get("embedding");
        if (!(emb instanceof List)) {
            return null;
        }
        List<?> list = (List<?>) emb;
        if (list.isEmpty()) {
            return null;
        }
        double[] vec = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            vec[i] = ((Number) list.get(i)).doubleValue();
        }
        return vec;
    }

    private Map<String, Object> postJson(String path, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + path, entity, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                markUp();
                return resp.getBody();
            }
            return null;
        } catch (Exception e) {
            log.debug("调用 AI 服务 {} 失败: {}", path, e.getMessage());
            markDown();
            return null;
        }
    }

    private Map<String, Object> postMultipart(String path, byte[] imageBytes) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "image.jpg";
                }
            };
            body.add("file", resource);
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + path, entity, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                markUp();
                return resp.getBody();
            }
            return null;
        } catch (Exception e) {
            log.debug("调用 AI 服务 {} 失败: {}", path, e.getMessage());
            markDown();
            return null;
        }
    }
}
