package com.milktea.entity;

import lombok.Data;
import java.util.List;

/**
 * 热量计算结果
 */
@Data
public class CalorieResult {
    private String productName;         // 奶茶名称
    private String brandName;           // 品牌名称
    private String size;                // 容量
    private Integer baseCalorie;        // 基础热量(奶茶本身)
    private Integer toppingCalorie;     // 小料热量
    private Integer totalCalorie;       // 总热量
    private Integer totalSugar;         // 总糖分
    private Integer totalCarbs;         // 总碳水
    private String calorieLevel;        // 热量等级: 低/中/高/超高
    private String healthAdvice;        // 健康建议
    private List<ToppingDetail> toppings; // 小料详情
    private List<ExerciseAdvice> exerciseAdvices; // 运动建议

    @Data
    public static class ToppingDetail {
        private String name;
        private Integer calorie;
    }

    @Data
    public static class ExerciseAdvice {
        private String exerciseName;
        private String image;
        private Double duration;    // 需要运动的时长(小时)
        private String durationText; // 格式化的时长文本
        private String description;
    }
}
