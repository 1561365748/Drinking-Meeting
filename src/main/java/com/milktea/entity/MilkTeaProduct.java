package com.milktea.entity;

import lombok.Data;

/**
 * 奶茶产品实体类
 */
@Data
public class MilkTeaProduct {
    private Integer id;
    private String name;
    private Integer calorie;        // 热量(大卡)
    private Integer sugar;          // 糖分(克)
    private Integer carbs;          // 碳水化合物(克)
    private String image;           // 图片路径
    private String brandName;       // 品牌名称
    private Integer brandId;        // 品牌ID
}
