package com.milktea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 奶茶君 - 奶茶热量计算与推荐系统
 * 主启动类
 */
@SpringBootApplication
public class MilkteaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MilkteaApplication.class, args);
        System.out.println("========================================");
        System.out.println("   奶茶君系统启动成功！");
        System.out.println("   访问地址: http://localhost:8080");
        System.out.println("========================================");
    }
}
