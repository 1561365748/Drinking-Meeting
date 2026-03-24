package com.milktea.entity;

import lombok.Data;
import java.util.List;

/**
 * 推荐结果
 */
@Data
public class RecommendResult {
    private List<RecommendItem> recommendations;
    private String recommendType; // "hot" 热门推荐 / "personal" 个性化推荐

    @Data
    public static class RecommendItem {
        private Integer productId;
        private String productName;
        private String brandName;
        private String image;
        private Integer calorie;
        private Integer stars;             // 星级 1-5
        private String starsDisplay;       // 星级显示 "⭐⭐⭐⭐⭐"
        private String recommendReason;    // 推荐理由
        private Double matchScore;         // 匹配分数 0-100
        private List<String> tags;         // 标签
    }
}
