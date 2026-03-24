package com.milktea.service;

import com.milktea.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 传统推荐服务
 * 基于物品的协同过滤算法进行推荐
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraditionalRecommendService {

    private final DataService dataService;
    private final UserService userService;

    /**
     * 获取传统推荐（协同过滤）
     */
    public List<RecommendResultV2.RecommendItem> recommend(String userId, RecommendRequestV2 request) {
        try {
            // 获取用户档案
            User user = userService.getUser(userId);

            // 1. 获取用户历史喜好的产品
            List<Integer> likedProducts = userService.getUserLikedProductIds(userId);

            // 2. 基于协同过滤计算相似产品
            Map<Integer, Double> cfScores = calculateCollaborativeFilteringScores(likedProducts);

            // 3. 基于用户特征计算匹配分
            Map<Integer, Double> featureScores = calculateFeatureScores(user, request);

            // 4. 综合评分
            Map<Integer, Double> finalScores = new HashMap<>();
            for (Integer productId : dataService.getProductMap().keySet()) {
                double cfScore = cfScores.getOrDefault(productId, 0.0) * 0.4;
                double featureScore = featureScores.getOrDefault(productId, 0.0) * 0.6;
                finalScores.put(productId, cfScore + featureScore);
            }

            // 5. 过滤产品
            Set<Integer> validProducts = filterProducts(user, request);

            // 6. 排序并生成推荐
            List<Integer> sortedProductIds = finalScores.entrySet().stream()
                    .filter(e -> validProducts.contains(e.getKey()))
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            // 7. 构建推荐结果
            List<RecommendResultV2.RecommendItem> items = new ArrayList<>();
            int rank = 1;
            for (Integer productId : sortedProductIds) {
                MilkTeaProduct product = dataService.getProductMap().get(productId);
                if (product == null) continue;

                RecommendResultV2.RecommendItem item = createRecommendItem(product, rank, user, request);
                items.add(item);
                rank++;
            }

            // 如果推荐不足，用热门产品补充
            if (items.size() < 5) {
                fillWithHotProducts(items, 5 - items.size(), request);
            }

            return items;
        } catch (Exception e) {
            log.error("传统推荐失败: {}", e.getMessage());
            return getFallbackRecommendations(request);
        }
    }

    /**
     * 计算协同过滤分数
     */
    private Map<Integer, Double> calculateCollaborativeFilteringScores(List<Integer> likedProducts) {
        Map<Integer, Double> scores = new HashMap<>();

        if (likedProducts.isEmpty()) {
            // 没有历史数据，使用热度分数
            return new HashMap<>(dataService.getProductHotScores());
        }

        // 基于用户喜好的产品，找到相似产品
        for (Integer likedId : likedProducts) {
            // 找到与该产品相似的其他产品
            Map<Integer, Double> similarProducts = findSimilarProducts(likedId);
            // 找到与该产品相似的其他产品，并返回一个 Map，其中键是相似产品的 ID，值是相似度分数。
            for (Map.Entry<Integer, Double> entry : similarProducts.entrySet()) {
                double currentScore = scores.getOrDefault(entry.getKey(), 0.0);
                scores.put(entry.getKey(), currentScore + entry.getValue());
            }
        }

        return normalizeScores(scores);
    }

    /**
     * 找到相似产品，相似度大于0.1的产品会被考虑
     */
    private Map<Integer, Double> findSimilarProducts(Integer productId) {
        Map<Integer, Double> similarities = new HashMap<>();
        MilkTeaProduct target = dataService.getProductMap().get(productId);

        if (target == null) return similarities;

        for (MilkTeaProduct product : dataService.getProductMap().values()) {
            if (product.getId().equals(productId)) continue;

            double similarity = calculateProductSimilarity(target, product);
            if (similarity > 0.1) {
                similarities.put(product.getId(), similarity);
            }
        }

        return similarities;
    }

    /**
     * 计算产品相似度
     */
    private double calculateProductSimilarity(MilkTeaProduct p1, MilkTeaProduct p2) {
        double similarity = 0.0;

        // 同品牌加分
        if (p1.getBrandId() != null && p1.getBrandId().equals(p2.getBrandId())) {
            similarity += 0.3;
        }

        // 热量相似度
        // 使用公式 1 - calorieDiff / 300.0 将热量差值归一化到 [0, 1] 范围内。
        // 如果差值大于 300，则相似度为 0（Math.max(0, ...)）。
        double calorieDiff = Math.abs(p1.getCalorie() - p2.getCalorie());
        double calorieSim = Math.max(0, 1 - calorieDiff / 300.0);
        similarity += calorieSim * 0.3;

        // 糖分相似度
        // 使用公式 1 - sugarDiff / 50.0 将糖分差值归一化到 [0, 1] 范围内。
        // 如果差值大于 50，则相似度为 0。
        double sugarDiff = Math.abs(p1.getSugar() - p2.getSugar());
        double sugarSim = Math.max(0, 1 - sugarDiff / 50.0);
        similarity += sugarSim * 0.2;

        // 名称相似度（基于关键词）
        similarity += calculateNameSimilarity(p1.getName(), p2.getName()) * 0.2;

        return similarity;
    }

    /**
     * 计算名称相似度
     * 通过提取名称中的关键词，计算关键词集合的交集与并集的比例，得出相似度分数。
     */
    private double calculateNameSimilarity(String name1, String name2) {
        Set<String> keywords1 = extractKeywords(name1);
        Set<String> keywords2 = extractKeywords(name2);

        if (keywords1.isEmpty() || keywords2.isEmpty()) return 0;
        
        // 保留 keywords1 和 keywords2 的公共元素，得到交集。
        Set<String> intersection = new HashSet<>(keywords1);
        intersection.retainAll(keywords2);
        // 得到并集
        Set<String> union = new HashSet<>(keywords1);
        union.addAll(keywords2);
        // 相似度的计算公式为：交集大小 ÷ 并集大小。
        return (double) intersection.size() / union.size();
    }

    /**
     * 提取关键词
     */
    private Set<String> extractKeywords(String name) {
        Set<String> keywords = new HashSet<>();
        String[] keywordList = {"芝士", "芝芝", "奶茶", "果茶", "牛乳", "葡萄", "草莓", "芒果",
                "柠檬", "柚子", "波波", "珍珠", "仙草", "杨枝甘露", "绿", "红", "四季春", "茉莉"};

        for (String keyword : keywordList) {
            if (name.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }

    /**
     * 计算用户特征匹配分数
     */
    private Map<Integer, Double> calculateFeatureScores(User user, RecommendRequestV2 request) {
        Map<Integer, Double> scores = new HashMap<>();

        for (MilkTeaProduct product : dataService.getProductMap().values()) {
            double score = 0.0;

            // 口味偏好匹配  看关键词
            List<String> flavors = request.getPreferredFlavors();
            if (flavors != null && !flavors.isEmpty()) {
                score += calculateFlavorMatch(product, flavors) * 0.4;
            } else if (user != null && user.getPreferredFlavors() != null) {
                score += calculateFlavorMatch(product, user.getPreferredFlavors()) * 0.4;
            }

            // 甜度偏好 看糖分含量
            Integer sweetLevel = request.getSweetLevel();
            if (sweetLevel != null) {
                score += calculateSweetMatch(product, sweetLevel) * 0.2;
            } else if (user != null && user.getSweetLevel() != null) {
                score += calculateSweetMatch(product, user.getSweetLevel()) * 0.2;
            }

            // 低卡偏好
            boolean preferLowCal = Boolean.TRUE.equals(request.getPreferLowCalorie()) ||
                    (user != null && Boolean.TRUE.equals(user.getPreferLowCalorie()));
            if (preferLowCal) {
                score += calculateLowCalorieMatch(product) * 0.2;
            }

            // 热度加分
            Double hotScore = dataService.getProductHotScores().get(product.getId());
            if (hotScore != null) {
                score += (hotScore / 100.0) * 0.2;
            }

            scores.put(product.getId(), score);
        }

        return normalizeScores(scores);
    }

    /**
     * 口味匹配计算
     */
    private double calculateFlavorMatch(MilkTeaProduct product, List<String> flavors) {
        double score = 0.0;
        String name = product.getName().toLowerCase();

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
                case "奶茶":
                    if (name.contains("芝士") || name.contains("奶盖") || name.contains("牛乳") ||
                            name.contains("芝芝") || name.contains("奶茶")) {
                        score += 20;
                    }
                    break;
                case "果茶":
                    if (name.contains("葡萄") || name.contains("草莓") || name.contains("芒果") ||
                            name.contains("橙") || name.contains("柚") || name.contains("桃") ||
                            name.contains("果")) {
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
        return (5 - diff) * 20.0;
    }

    /**
     * 低卡匹配计算
     */
    private double calculateLowCalorieMatch(MilkTeaProduct product) {
        if (product.getCalorie() < 150) {
            return 100;
        } else if (product.getCalorie() < 200) {
            return 70;
        } else if (product.getCalorie() < 300) {
            return 40;
        } else {
            return 0;
        }
    }

    /**
     * 过滤产品
     */
    private Set<Integer> filterProducts(User user, RecommendRequestV2 request) {
        Set<Integer> validProducts = new HashSet<>(dataService.getProductMap().keySet());

        // 品牌过滤
        if (request.getBrandIds() != null && !request.getBrandIds().isEmpty()) {
            validProducts = validProducts.stream()
                    .filter(id -> {
                        MilkTeaProduct p = dataService.getProductMap().get(id);
                        return p != null && request.getBrandIds().contains(p.getBrandId());
                    })
                    .collect(Collectors.toSet());
        }

        // 忌口过滤
        if (user != null && user.getAllergies() != null) {
            for (String allergy : user.getAllergies()) {
                if (allergy.equals("乳糖不耐受") || allergy.equals("乳制品")) {
                    validProducts.removeAll(getDairyProducts());
                } else if (allergy.equals("芒果过敏")) {
                    validProducts.removeAll(getMangoProducts());
                } else if (allergy.equals("草莓过敏")) {
                    validProducts.removeAll(getStrawberryProducts());
                } else if (allergy.equals("不能喝冷饮")) {
                    // 不过滤，但在推荐理由中提示
                }
            }
        }

        // 疾病过滤
        if (user != null && user.getDiseaseHistory() != null) {
            for (String disease : user.getDiseaseHistory()) {
                if (disease.equals("糖尿病")) {
                    validProducts = validProducts.stream()
                            .filter(id -> {
                                MilkTeaProduct p = dataService.getProductMap().get(id);
                                return p != null && p.getSugar() < 30;
                            })
                            .collect(Collectors.toSet());
                } else if (disease.equals("减肥中")) {
                    validProducts = validProducts.stream()
                            .filter(id -> {
                                MilkTeaProduct p = dataService.getProductMap().get(id);
                                return p != null && p.getCalorie() < 250;
                            })
                            .collect(Collectors.toSet());
                }
            }
        }

        return validProducts;
    }

    private Set<Integer> getDairyProducts() {
        return Set.of(104, 105, 106, 202, 205, 206, 207, 302, 303, 304, 305,
                402, 404, 504, 505, 601, 602, 603, 605, 701, 703, 704);
    }

    private Set<Integer> getMangoProducts() {
        return Set.of(107, 401, 705, 802);
    }

    private Set<Integer> getStrawberryProducts() {
        return Set.of(102, 202, 304);
    }

    /**
     * 创建推荐项
     */
    private RecommendResultV2.RecommendItem createRecommendItem(MilkTeaProduct product, int rank,
                                                                  User user, RecommendRequestV2 request) {
        RecommendResultV2.RecommendItem item = new RecommendResultV2.RecommendItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setBrandName(product.getBrandName());
        item.setImageUrl(product.getImage());
        item.setCalorie(product.getCalorie());
        item.setRecommendLevel(6 - Math.min(rank, 5));

        // 生成推荐理由
        item.setRecommendReason(generateReason(product, user, request));

        // 建议糖度
        Integer sweetLevel = request.getSweetLevel() != null ? request.getSweetLevel() :
                (user != null ? user.getSweetLevel() : 3);
        item.setSuggestedSugar(getSweetLevelDesc(sweetLevel));

        // 建议温度
        if (user != null && user.getAllergies() != null &&
                user.getAllergies().contains("不能喝冷饮")) {
            item.setSuggestedTemperature("热");
        } else {
            item.setSuggestedTemperature("去冰");
        }

        // 生成标签
        item.setTags(generateTags(product));

        return item;
    }

    /**
     * 生成推荐理由
     */
    private String generateReason(MilkTeaProduct product, User user, RecommendRequestV2 request) {
        List<String> reasons = new ArrayList<>();

        // 热度理由
        Double hotScore = dataService.getProductHotScores().get(product.getId());
        if (hotScore != null && hotScore > 80) {
            reasons.add("本周热门TOP产品");
        }

        // 口味匹配理由
        if (product.getCalorie() < 180) {
            reasons.add("清爽低卡");
        }
        if (product.getName().contains("芝士") || product.getName().contains("芝芝")) {
            reasons.add("浓郁芝士口感");
        }
        if (product.getName().contains("葡萄") || product.getName().contains("草莓")) {
            reasons.add("新鲜水果制作");
        }

        // 健康理由
        if (user != null && user.getDiseaseHistory() != null) {
            if (user.getDiseaseHistory().contains("减肥中") && product.getCalorie() < 200) {
                reasons.add("低卡路里，适合减脂期");
            }
            if (user.getDiseaseHistory().contains("糖尿病") && product.getSugar() < 25) {
                reasons.add("低糖配方，血糖友好");
            }
        }

        if (reasons.isEmpty()) {
            reasons.add("基于您的偏好推荐");
        }

        return String.join("，", reasons);
    }

    /**
     * 生成标签
     */
    private List<String> generateTags(MilkTeaProduct product) {
        List<String> tags = new ArrayList<>();

        if (product.getCalorie() < 150) tags.add("低卡");
        if (product.getSugar() < 25) tags.add("低糖");
        if (product.getName().contains("芝士") || product.getName().contains("芝芝")) tags.add("芝士控");
        if (product.getName().contains("果") || product.getName().contains("葡萄") || product.getName().contains("草莓")) {
            tags.add("果茶");
        }

        return tags;
    }

    /**
     * 用热门产品补充
     */
    private void fillWithHotProducts(List<RecommendResultV2.RecommendItem> items, int count,
                                      RecommendRequestV2 request) {
        Set<Integer> existingIds = items.stream()
                .map(RecommendResultV2.RecommendItem::getProductId)
                .collect(Collectors.toSet());

        int added = 0;
        for (DataService.PopularRanking ranking : dataService.getPopularRankings()) {
            if (added >= count) break;
            if (existingIds.contains(ranking.getProductId())) continue;

            MilkTeaProduct product = dataService.getProductMap().get(ranking.getProductId());
            if (product == null) continue;

            // 检查品牌过滤
            if (request != null && request.getBrandIds() != null && !request.getBrandIds().isEmpty()) {
                if (!request.getBrandIds().contains(product.getBrandId())) continue;
            }

            RecommendResultV2.RecommendItem item = new RecommendResultV2.RecommendItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setBrandName(product.getBrandName());
            item.setImageUrl(product.getImage());
            item.setCalorie(product.getCalorie());
            item.setRecommendLevel(6 - items.size() - 1);
            item.setRecommendReason(ranking.getRecommendReason());
            item.setSuggestedSugar("半糖");
            item.setSuggestedTemperature("去冰");
            item.setTags(ranking.getTags());

            items.add(item);
            added++;
        }
    }

    /**
     * 降级推荐
     */
    private List<RecommendResultV2.RecommendItem> getFallbackRecommendations(RecommendRequestV2 request) {
        List<RecommendResultV2.RecommendItem> items = new ArrayList<>();

        int rank = 1;
        for (DataService.PopularRanking ranking : dataService.getPopularRankings()) {
            if (items.size() >= 5) break;

            MilkTeaProduct product = dataService.getProductMap().get(ranking.getProductId());
            if (product == null) continue;

            RecommendResultV2.RecommendItem item = new RecommendResultV2.RecommendItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setBrandName(product.getBrandName());
            item.setImageUrl(product.getImage());
            item.setCalorie(product.getCalorie());
            item.setRecommendLevel(6 - rank);
            item.setRecommendReason(ranking.getRecommendReason());
            item.setSuggestedSugar("半糖");
            item.setSuggestedTemperature("去冰");
            item.setTags(ranking.getTags());

            items.add(item);
            rank++;
        }

        return items;
    }

    /**
     * 归一化分数
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
     * 获取甜度描述
     */
    private String getSweetLevelDesc(Integer level) {
        if (level == null) return "半糖";
        switch (level) {
            case 1: return "无糖";
            case 2: return "三分甜";
            case 3: return "半糖";
            case 4: return "七分甜";
            case 5: return "全糖";
            default: return "半糖";
        }
    }
}
