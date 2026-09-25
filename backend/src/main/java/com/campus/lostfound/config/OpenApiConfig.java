package com.campus.lostfound.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / Knife4j 接口文档配置。
 * <p>
 * 启动后端后访问 http://localhost:8080/doc.html 查看中文接口文档。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("校园失物招领智能匹配与 AI 图像识别系统 API")
                .description("基于 SpringBoot + Vue3 + MobileNetV3 的校园失物招领系统接口文档")
                .version("1.0.0"));
    }
}
