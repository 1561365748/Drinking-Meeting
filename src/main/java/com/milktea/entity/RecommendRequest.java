package com.milktea.entity;

import lombok.Data;
import java.util.List;

/**
 * 推荐请求
 */
@Data
public class RecommendRequest {
    // 用户偏好
    private List<String> preferredFlavors;  // 偏好口味: 甜/清爽/浓郁/果茶/奶茶
    private Integer sweetLevel;              // 甜度偏好: 1-5
    private Boolean preferLowCalorie;        // 是否偏好低卡

    // 忌口
    private List<String> allergies;          // 过敏源: 花生/乳制品/麸质等
    private List<String> dislikeToppings;    // 不喜欢的小料

    // 健康问题
    private List<String> healthIssues;       // 健康问题: 糖尿病/高血压/减肥中/健身中
}
