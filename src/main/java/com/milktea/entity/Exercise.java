package com.milktea.entity;

import lombok.Data;

/**
 * 运动实体类
 */
@Data
public class Exercise {
    private Integer id;
    private String name;
    private Integer caloriePerHour; // 每小时消耗热量(大卡)
    private String image;           // 图片路径
    private String description;     // 描述
    private String intensity;       // 强度: 低/中/高
}
