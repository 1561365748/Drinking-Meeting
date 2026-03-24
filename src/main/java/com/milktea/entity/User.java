package com.milktea.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户实体类
 */
@Data
public class User {
    // 用户ID（数字构成的用户名）
    private String userId;

    // 疾病史
    private List<String> diseaseHistory = new ArrayList<>();

    // 忌口/过敏
    private List<String> allergies = new ArrayList<>();

    // 口味偏好
    private List<String> preferredFlavors = new ArrayList<>();

    // 甜度偏好 1-5
    private Integer sweetLevel = 3;

    // 是否偏好低卡
    private Boolean preferLowCalorie = false;

    // 历史选择记录
    private List<UserSelection> historySelections = new ArrayList<>();

    // 注册时间
    private LocalDateTime registerTime;

    // 最后登录时间
    private LocalDateTime lastLoginTime;

    /**
     * 用户选择记录
     */
    @Data
    public static class UserSelection {
        // 选择的奶茶产品ID
        private Integer productId;
        // 产品名称
        private String productName;
        // 品牌名称
        private String brandName;
        // 选择的小料ID列表
        private List<Integer> toppingIds = new ArrayList<>();
        // 小料名称列表
        private List<String> toppingNames = new ArrayList<>();
        // 糖度选择
        private String sugarLevel;
        // 温度选择
        private String temperature;
        // 喜欢程度 1-5
        private Integer likeRating;
        // 文字反馈
        private String feedback;
        // 选择时间
        private LocalDateTime selectionTime;
        // 推荐来源（deepseek/traditional/smart）
        private String recommendSource;
    }
}
