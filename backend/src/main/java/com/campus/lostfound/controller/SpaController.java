package com.campus.lostfound.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 根路径处理：
 * - 前端已构建时，返回 index.html（单端口托管）
 * - 前端未构建时，返回友好提示页（替代 Spring Boot 的 Whitelabel 404 错误页）
 */
@Controller
public class SpaController {

    @Value("${frontend.dist-dir:../frontend/dist}")
    private String distDir;

    @GetMapping("/")
    public void index(HttpServletResponse response) throws IOException {
        File index = new File(new File(distDir).getAbsoluteFile().toPath().normalize().toFile(), "index.html");
        response.setContentType("text/html;charset=UTF-8");
        if (index.exists() && index.isFile()) {
            Files.copy(index.toPath(), response.getOutputStream());
        } else {
            response.getWriter().write(buildHintPage());
        }
    }

    private String buildHintPage() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>校园失物招领智能匹配系统</title>\n" +
                "<style>body{font-family:'Microsoft YaHei',Arial,sans-serif;background:#f5f7fa;display:flex;" +
                "align-items:center;justify-content:center;min-height:100vh;margin:0}" +
                ".card{background:#fff;border-radius:12px;box-shadow:0 8px 30px rgba(0,21,41,.12);" +
                "padding:40px 44px;max-width:560px}.card h1{font-size:22px;color:#303133;margin:0 0 8px}" +
                ".card .sub{color:#909399;font-size:14px;margin-bottom:22px}.ok{color:#67c23a;font-weight:600}" +
                ".card h2{font-size:16px;color:#303133;margin:16px 0 8px}.card pre{background:#f8fafc;" +
                "border:1px solid #ebeef5;border-radius:8px;padding:14px;font-size:13px;line-height:1.9;" +
                "color:#476582;overflow-x:auto}.card .tip{color:#e6a23c;font-size:13px;margin-top:14px}" +
                "</style></head><body><div class=\"card\"><h1>校园失物招领智能匹配系统</h1>\n" +
                "<div class=\"sub\">后端服务 <span class=\"ok\">运行中 ✓</span>（端口 8080），但前端页面尚未构建。</div>\n" +
                "<h2>方式一：构建前端（推荐，单端口访问）</h2>\n" +
                "<pre>cd frontend\nnpm install\nnpm run build</pre>\n" +
                "<div class=\"sub\">构建完成后刷新本页面即可进入系统。</div>\n" +
                "<h2>方式二：开发模式（前后端分离）</h2>\n" +
                "<pre>cd frontend\nnpm run dev</pre>\n" +
                "<div class=\"sub\">然后访问 <a href=\"http://localhost:5173\">http://localhost:5173</a></div>\n" +
                "<div class=\"tip\">提示：本页由后端在检测不到前端 dist 目录时自动显示，构建前端后不再出现。</div>\n" +
                "</div></body></html>";
    }
}
