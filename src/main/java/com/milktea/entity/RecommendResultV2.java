package com.milktea.entity;

import lombok.Data;
import java.util.List;

/**
 * 三种推荐方法的综合推荐结果
 */
@Data
public class RecommendResultV2 {
    // 是否成功
    private boolean success;

    // 消息
    private String message;

    // Deepseek辅助推荐结果
    private List<RecommendItem> deepseekRecommendations;

    // 传统推荐结果（协同过滤）
    private List<RecommendItem> traditionalRecommendations;

    // 智能匹配推荐结果（词向量）
    private List<RecommendItem> smartMatchRecommendations;

    /**
     * 推荐项
     */
    @Data
    public static class RecommendItem {
        // 产品ID
        private Integer productId;

        // 产品名称
        private String productName;

        // 品牌名称
        private String brandName;

        // 图片URL
        private String imageUrl;

        // 热量
        private Integer calorie;

        // 推荐等级 1-5星
        private Integer recommendLevel;

        // 推荐理由
        private String recommendReason;

        // 建议糖度
        private String suggestedSugar;

        // 建议温度
        private String suggestedTemperature;

        // 推荐的小料
        private List<String> suggestedToppings;

        // 匹配分数
        private Double matchScore;

        // 标签
        private List<String> tags;
    }

    /**
     * 创建失败的推荐结果
     */
    public static RecommendResultV2 error(String message) {
        RecommendResultV2 result = new RecommendResultV2();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }
}
