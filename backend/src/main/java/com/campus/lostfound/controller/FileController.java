package com.campus.lostfound.controller;

import com.campus.lostfound.common.BusinessException;
import com.campus.lostfound.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传接口
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    private static final Set<String> ALLOWED_EXT = new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp"));

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        int idx = original == null ? -1 : original.lastIndexOf('.');
        if (idx >= 0) {
            ext = original.substring(idx + 1).toLowerCase();
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持图片格式: jpg/jpeg/png/gif/webp/bmp");
        }
        // 文件头魔数校验：防止伪造扩展名上传非图片文件
        byte[] bytes = file.getBytes();
        if (!isAllowedImage(bytes)) {
            throw new BusinessException("文件内容不是有效的图片，请上传真实的图片文件");
        }
        String dateDir = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        // 使用绝对路径，避免 Servlet 容器对相对路径写入的处理差异
        File base = new File(uploadDir).getAbsoluteFile();
        File dir = new File(base, dateDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("创建上传目录失败");
        }
        File dest = new File(dir, filename);
        Files.write(dest.toPath(), bytes);
        String relPath = dateDir + "/" + filename;
        Map<String, Object> data = new HashMap<>();
        data.put("url", "/uploads/" + relPath);
        data.put("path", relPath);
        return Result.ok(data);
    }

    /**
     * 校验文件头魔数是否为常见图片格式（JPEG/PNG/GIF/BMP/WebP）
     */
    private boolean isAllowedImage(byte[] b) {
        if (b == null || b.length < 4) {
            return false;
        }
        // JPEG: FF D8 FF
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return true;
        }
        // GIF: 47 49 46 38 ("GIF8")
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return true;
        }
        // BMP: 42 4D ("BM")
        if (b[0] == 'B' && b[1] == 'M') {
            return true;
        }
        // WebP: RIFF....WEBP
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
