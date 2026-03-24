package com.milktea.service;

import com.milktea.algorithm.ItemCFRecommender;
import com.milktea.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐服务（已弃用，只使用getRecommendations方法）
 * 混合推荐算法：用户特征匹配(60%) + 网络大众喜好(40%)
 */
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final DataService dataService;
    private final ItemCFRecommender itemCFRecommender;

    // 用户特征权重
    private static final double USER_FEATURE_WEIGHT = 0.6;
    // 大众热度权重
    private static final double POPULARITY_WEIGHT = 0.4;

    /**
     * 获取推荐
     * 根据用户的请求数据，返回推荐结果。它会根据请求是否包含用户偏好信息，决定返回热门推荐还是个性化推荐。
     */
    public RecommendResult getRecommendations(RecommendRequest request) {
        RecommendResult result = new RecommendResult();

        // 判断是否为空请求（返回热门推荐）
        boolean isEmptyRequest = isEmptyRequest(request);

        if (isEmptyRequest) {
            result.setRecommendType("hot");
            result.setRecommendations(getHotRecommendations());
        } else {
            result.setRecommendType("personal");
            result.setRecommendations(getPersonalizedRecommendations(request));
        }

        return result;
    }

    /**
     * 判断是否为空请求
     */
    private boolean isEmptyRequest(RecommendRequest request) {
        return (request == null) ||
                (isEmpty(request.getPreferredFlavors()) &&
                 request.getSweetLevel() == null &&
                 !Boolean.TRUE.equals(request.getPreferLowCalorie()) &&
                 isEmpty(request.getAllergies()) &&
                 isEmpty(request.getDislikeToppings()) &&
                 isEmpty(request.getHealthIssues()));
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    /**
     * 获取热门推荐（无用户输入时）
     */
    private List<RecommendResult.RecommendItem> getHotRecommendations() {
        List<RecommendResult.RecommendItem> items = new ArrayList<>();

        // 获取热门排行
        List<DataService.PopularRanking> rankings = dataService.getPopularRankings();

        for (DataService.PopularRanking ranking : rankings) {
            MilkTeaProduct product = dataService.getProductMap().get(ranking.getProductId());
            if (product == null) continue;

            RecommendResult.RecommendItem item = createRecommendItem(product, ranking);
            items.add(item);
        }

        return items;
    }

    /**
     * 获取个性化推荐（有用户输入时）
     */
    private List<RecommendResult.RecommendItem> getPersonalizedRecommendations(RecommendRequest request) {
        List<RecommendResult.RecommendItem> items = new ArrayList<>();
        Map<Integer, Double> scores = new HashMap<>();

        // 1. 计算每个产品的用户特征匹配分
        Map<Integer, Double> userFeatureScores = calculateUserFeatureScores(request);

        // 2. 获取大众热度分
        Map<Integer, Double> hotScores = dataService.getProductHotScores();

        // 3. 获取协同过滤推荐分数
        Map<Integer, Double> cfScores = getCollaborativeFilteringScores(request);

        // 4. 综合计算最终分数
        for (Integer productId : dataService.getProductMap().keySet()) {
            double userScore = userFeatureScores.getOrDefault(productId, 0.0);
            double hotScore = normalizeHotScore(hotScores.getOrDefault(productId, 0.0));
            double cfScore = cfScores.getOrDefault(productId, 0.0);

            // 混合评分公式
            // 用户特征分 = 偏好匹配(70%) + 协同过滤(30%)
            double featureScore = userScore * 0.7 + cfScore * 0.3;

            // 最终得分 = 用户特征(60%) + 大众热度(40%)
            double finalScore = featureScore * USER_FEATURE_WEIGHT + hotScore * POPULARITY_WEIGHT;

            scores.put(productId, finalScore);
        }

        // 5. 过滤不符合条件的产品
        Set<Integer> filteredProducts = filterProducts(request);

        // 6. 排序并生成推荐列表
        List<Integer> sortedProductIds = scores.entrySet().stream()
                .filter(e -> filteredProducts.contains(e.getKey()))
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 7. 构建推荐结果
        for (Integer productId : sortedProductIds) {
            MilkTeaProduct product = dataService.getProductMap().get(productId);
            if (product == null) continue;

            double score = scores.get(productId);
            RecommendResult.RecommendItem item = createPersonalizedItem(product, score, request);
            items.add(item);
        }

        return items;
    }

    /**
     * 计算用户特征匹配分数
     */
    private Map<Integer, Double> calculateUserFeatureScores(RecommendRequest request) {
        Map<Integer, Double> scores = new HashMap<>();

        for (MilkTeaProduct product : dataService.getProductMap().values()) {
            double score = 0.0;

            // 口味偏好匹配
            if (!isEmpty(request.getPreferredFlavors())) {
                score += calculateFlavorMatch(product, request.getPreferredFlavors());
            }

            // 甜度偏好
            if (request.getSweetLevel() != null) {
                score += calculateSweetMatch(product, request.getSweetLevel());
            }

            // 低卡偏好
            if (Boolean.TRUE.equals(request.getPreferLowCalorie())) {
                score += calculateLowCalorieMatch(product);
            }

            // 健康问题匹配
            if (!isEmpty(request.getHealthIssues())) {
                score += calculateHealthMatch(product, request.getHealthIssues());
            }

            scores.put(product.getId(), score);
        }

        // 归一化到0-100
        return normalizeScores(scores);
    }

    /**
     * 口味匹配计算
     */
    private double calculateFlavorMatch(MilkTeaProduct product, List<String> flavors) {
        double score = 0.0;
        String name = product.getName().toLowerCase();
        String brandName = product.getBrandName() != null ? product.getBrandName().toLowerCase() : "";

        for (String flavor : flavors) {
            switch (flavor) {
                case "甜":
                    if (name.contains("糖") || name.contains("蜜") || product.getSugar() > 40) {
                        score += 20;
                    }
                    break;
                case "清爽":
                    if (name.contains("柠") || name.contains("柚") || name.contains("果茶") ||
                        name.contains("四季春") || name.contains("绿")) {
                        score += 20;
                    }
                    break;
                case "浓郁":
                    if (name.contains("芝士") || name.contains("奶盖") || name.contains("牛乳") ||
                        name.contains("芝芝")) {
                        score += 20;
                    }
                    break;
                case "果茶":
                    if (name.contains("葡萄") || name.contains("草莓") || name.contains("芒果") ||
                        name.contains("橙") || name.contains("柚") || name.contains("桃")) {
                        score += 20;
                    }
                    break;
                case "奶茶":
                    if (name.contains("奶茶") || name.contains("牛乳") || name.contains("拿铁")) {
                        score += 20;
                    }
                    break;
            }
        }

        return Math.min(score, 100);
    }

    /**
     * 甜度匹配计算
     */
    private double calculateSweetMatch(MilkTeaProduct product, Integer sweetLevel) {
        // sweetLevel: 1-5 (1最不甜, 5最甜)
        int productSweetLevel;
        if (product.getSugar() < 20) {
            productSweetLevel = 1;
        } else if (product.getSugar() < 35) {
            productSweetLevel = 2;
        } else if (product.getSugar() < 45) {
            productSweetLevel = 3;
        } else if (product.getSugar() < 55) {
            productSweetLevel = 4;
        } else {
            productSweetLevel = 5;
        }

        int diff = Math.abs(sweetLevel - productSweetLevel);
        return (5 - diff) * 20.0; // 0-100
    }

    /**
     * 低卡匹配计算
     */
    private double calculateLowCalorieMatch(MilkTeaProduct product) {
        if (product.getCalorie() < 150) {
            return 100;
        } else if (product.getCalorie() < 250) {
            return 70;
        } else if (product.getCalorie() < 350) {
            return 30;
        } else {
            return 0;
        }
    }

    /**
     * 健康问题匹配计算
     */
    private double calculateHealthMatch(MilkTeaProduct product, List<String> healthIssues) {
        double score = 50.0; // 基础分

        for (String issue : healthIssues) {
            switch (issue) {
                case "糖尿病":
                    if (product.getSugar() < 25) {
                        score += 30;
                    } else if (product.getSugar() > 50) {
                        score -= 50;
                    }
                    break;
                case "高血压":
                    // 假设低糖产品更友好
                    if (product.getSugar() < 30) {
                        score += 20;
                    }
                    break;
                case "减肥中":
                    if (product.getCalorie() < 200) {
                        score += 40;
                    } else if (product.getCalorie() > 350) {
                        score -= 30;
                    }
                    break;
                case "健身中":
                    if (product.getCarbs() > 50) {
                        score += 20; // 碳水补充
                    }
                    break;
            }
        }

        return Math.max(0, Math.min(100, score));
    }

    /**
     * 获取协同过滤推荐分数
     */
    private Map<Integer, Double> getCollaborativeFilteringScores(RecommendRequest request) {
        // 基于用户偏好模拟协同过滤
        Map<Integer, Double> scores = new HashMap<>();

        // 使用ItemCF算法计算相似度
        Map<Integer, Double> cfScores = itemCFRecommender.recommend(request);

        // 归一化
        return normalizeScores(cfScores);
    }

    /**
     * 过滤不符合条件的产品
     */
    private Set<Integer> filterProducts(RecommendRequest request) {
        Set<Integer> validProducts = new HashSet<>(dataService.getProductMap().keySet());

        // 过滤忌口相关
        if (!isEmpty(request.getAllergies())) {
            for (String allergy : request.getAllergies()) {
                switch (allergy) {
                    case "乳制品":
                        validProducts.removeAll(getDairyProducts());
                        break;
                    case "花生":
                        validProducts.removeAll(getPeanutProducts());
                        break;
                    case "麸质":
                        validProducts.removeAll(getGlutenProducts());
                        break;
                }
            }
        }

        // 过滤不喜欢的小料相关产品
        if (!isEmpty(request.getDislikeToppings())) {
            // 简单实现：保留所有产品，在结果中标注
        }

        return validProducts;
    }

    private Set<Integer> getDairyProducts() {
        // 含乳制品的产品ID
        return Set.of(104, 105, 106, 202, 205, 206, 207, 302, 303, 304, 305,
                      402, 404, 504, 505, 601, 602, 603, 605, 701, 703, 704);
    }

    private Set<Integer> getPeanutProducts() {
        return Set.of(); // 根据实际情况填充
    }

    private Set<Integer> getGlutenProducts() {
        return Set.of(104, 106, 305, 402, 505); // 含谷物类的
    }

    /**
     * 创建热门推荐项
     */
    private RecommendResult.RecommendItem createRecommendItem(MilkTeaProduct product,
                                                               DataService.PopularRanking ranking) {
        RecommendResult.RecommendItem item = new RecommendResult.RecommendItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setBrandName(product.getBrandName());
        item.setImage(product.getImage());
        item.setCalorie(product.getCalorie());
        item.setTags(ranking.getTags());
        item.setRecommendReason(ranking.getRecommendReason());

        // 计算星级
        int stars = calculateStars(ranking.getHotScore());
        item.setStars(stars);
        item.setStarsDisplay(generateStarsDisplay(stars));

        return item;
    }

    /**
     * 创建个性化推荐项
     */
    private RecommendResult.RecommendItem createPersonalizedItem(MilkTeaProduct product,
                                                                  double score,
                                                                  RecommendRequest request) {
        RecommendResult.RecommendItem item = new RecommendResult.RecommendItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setBrandName(product.getBrandName());
        item.setImage(product.getImage());
        item.setCalorie(product.getCalorie());
        item.setMatchScore(score);

        // 生成推荐理由
        item.setRecommendReason(generatePersonalReason(product, request));

        // 生成标签
        item.setTags(generateTags(product));

        // 计算星级
        int stars = calculateStars(score);
        item.setStars(stars);
        item.setStarsDisplay(generateStarsDisplay(stars));

        return item;
    }

    /**
     * 生成个性化推荐理由
     */
    private String generatePersonalReason(MilkTeaProduct product, RecommendRequest request) {
        List<String> reasons = new ArrayList<>();

        // 热度理由
        Double hotScore = dataService.getProductHotScores().get(product.getId());
        if (hotScore != null && hotScore > 90) {
            reasons.add("本周热门TOP产品");
        }

        // 口味匹配理由
        if (!isEmpty(request.getPreferredFlavors())) {
            for (String flavor : request.getPreferredFlavors()) {
                if (flavor.equals("清爽") && product.getCalorie() < 200) {
                    reasons.add("清爽低卡，适合您的口味偏好");
                    break;
                }
                if (flavor.equals("浓郁") && product.getName().contains("芝士")) {
                    reasons.add("浓郁芝士口感，满足您的偏好");
                    break;
                }
            }
        }

        // 健康理由
        if (!isEmpty(request.getHealthIssues())) {
            if (request.getHealthIssues().contains("减肥中") && product.getCalorie() < 200) {
                reasons.add("低卡路里，适合减脂期饮用");
            }
            if (request.getHealthIssues().contains("糖尿病") && product.getSugar() < 25) {
                reasons.add("低糖配方，血糖友好");
            }
        }

        // 默认理由
        if (reasons.isEmpty()) {
            reasons.add("根据您的偏好推荐");
        }

        return String.join("；", reasons);
    }

    /**
     * 生成产品标签
     */
    private List<String> generateTags(MilkTeaProduct product) {
        List<String> tags = new ArrayList<>();

        if (product.getCalorie() < 150) {
            tags.add("低卡");
        }
        if (product.getSugar() < 25) {
            tags.add("低糖");
        }
        if (product.getName().contains("芝士") || product.getName().contains("芝芝")) {
            tags.add("芝士控");
        }
        if (product.getName().contains("果") || product.getName().contains("葡萄") ||
            product.getName().contains("草莓")) {
            tags.add("果茶");
        }

        return tags;
    }

    /**
     * 计算星级
     */
    private int calculateStars(double score) {
        if (score >= 90) return 5;
        if (score >= 70) return 4;
        if (score >= 50) return 3;
        if (score >= 30) return 2;
        return 1;
    }

    /**
     * 生成星级显示
     */
    private String generateStarsDisplay(int stars) {
        return "⭐".repeat(Math.max(0, stars));
    }

    /**
     * 归一化分数到0-100
     */
    private Map<Integer, Double> normalizeScores(Map<Integer, Double> scores) {
        if (scores.isEmpty()) return scores;

        double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double min = scores.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double range = max - min;

        if (range == 0) return scores;

        Map<Integer, Double> normalized = new HashMap<>();
        for (Map.Entry<Integer, Double> entry : scores.entrySet()) {
            normalized.put(entry.getKey(), ((entry.getValue() - min) / range) * 100);
        }

        return normalized;
    }

    /**
     * 归一化热度分数
     */
    private double normalizeHotScore(double score) {
        return score; // 已经是0-100范围
    }
}
