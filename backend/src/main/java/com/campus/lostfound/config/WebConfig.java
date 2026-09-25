package com.campus.lostfound.config;

import com.campus.lostfound.interceptor.AdminInterceptor;
import com.campus.lostfound.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;

/**
 * Web 配置：CORS、静态资源（上传目录 + 前端 SPA）、JWT 拦截器
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private JwtInterceptor jwtInterceptor;

    @Resource
    private AdminInterceptor adminInterceptor;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${frontend.dist-dir:../frontend/dist}")
    private String distDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/post/list",
                        "/api/post/detail/**",
                        "/api/post/categories",
                        "/api/match/post/**"
                );
        // 管理员接口：依赖 JwtInterceptor 先解析 Token 并写入 role，故在之后注册
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**", "/api/stats/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 上传的图片通过 /uploads/** 直接访问（使用绝对路径，避免相对路径依赖启动目录）
        File base = new File(uploadDir).getAbsoluteFile();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(base.toURI().toString());

        // Knife4j 接口文档静态资源：须在 /** SPA 兜底之前显式注册，
        // 否则 /doc.html、/webjars/** 会被 catch-all 回退成 index.html
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        // 前端 SPA：托管 dist 构建产物，非 API 路径回退到 index.html（支持 vue-router history 模式）
        // 注意：必须 normalize() 消除相对路径中的 ".."，否则 toURI() 产生的 file:/ 含 ".." 会导致资源解析错误
        File dist = new File(distDir).getAbsoluteFile().toPath().normalize().toFile();
        registry.addResourceHandler("/**")
                .addResourceLocations(dist.toURI().toString())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected org.springframework.core.io.Resource getResource(String resourcePath,
                                                                              org.springframework.core.io.Resource location) throws IOException {
                        org.springframework.core.io.Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        org.springframework.core.io.Resource index = location.createRelative("index.html");
                        return (index.exists() && index.isReadable()) ? index : null;
                    }
                });
    }
}
