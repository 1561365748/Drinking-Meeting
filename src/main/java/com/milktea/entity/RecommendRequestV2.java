package com.milktea.entity;

import lombok.Data;
import java.util.List;

/**
 * 推荐请求V2 - 支持三种推荐方法
 */
@Data
public class RecommendRequestV2 {
    // 用户ID
    private String userId;

    // 选择的品牌ID列表
    private List<Integer> brandIds;

    // 口味偏好
    private List<String> preferredFlavors;

    // 甜度偏好 1-5
    private Integer sweetLevel;

    // 是否偏好低卡
    private Boolean preferLowCalorie;

    // 用户备注
    private String note;
}
