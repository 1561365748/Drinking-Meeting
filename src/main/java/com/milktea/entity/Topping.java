package com.milktea.entity;

import lombok.Data;

/**
 * 小料实体类
 */
@Data
public class Topping {
    private Integer id;
    private String name;
    private Integer calorie;        // 热量(大卡/份)
    private Integer sugar;          // 糖分(克)
    private Integer carbs;          // 碳水化合物(克)
    private String image;           // 图片路径
    private String category;        // 分类
}
