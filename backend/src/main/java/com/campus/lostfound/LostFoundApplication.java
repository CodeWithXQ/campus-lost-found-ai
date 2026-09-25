package com.campus.lostfound;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校园失物招领智能匹配与AI图像识别系统 - 启动类
 */
@SpringBootApplication
@MapperScan("com.campus.lostfound.mapper")
public class LostFoundApplication {

    public static void main(String[] args) {
        SpringApplication.run(LostFoundApplication.class, args);
        System.out.println("=============================================");
        System.out.println("  失物招领系统后端启动成功: http://localhost:8080");
        System.out.println("=============================================");
    }
}
