package com.milktea.algorithm;

import com.milktea.entity.MilkTeaProduct;
import com.milktea.entity.RecommendRequest;
import com.milktea.service.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 基于物品的协同过滤推荐算法 (ItemCF)
 * 核心思想：推荐与用户之前喜欢的产品相似的产品
 */
@Component
@RequiredArgsConstructor
public class ItemCFRecommender {

    private final DataService dataService;

    // 物品相似度矩阵缓存
    private Map<Integer, Map<Integer, Double>> itemSimilarityMatrix;

    /**
     * 计算推荐分数
     */
    public Map<Integer, Double> recommend(RecommendRequest request) {
        Map<Integer, Double> scores = new HashMap<>();

        // 获取用户可能喜欢的产品（基于偏好推断）
        Set<Integer> userLikedProducts = inferUserLikes(request);

        // 确保相似度矩阵已计算
        if (itemSimilarityMatrix == null) {
            buildSimilarityMatrix();
        }

        // 对于每个产品，计算推荐分数
        for (Integer productId : dataService.getProductMap().keySet()) {
            if (userLikedProducts.contains(productId)) {
                continue; // 跳过已经喜欢的产品
            }

            double score = 0.0;
            Map<Integer, Double> similarities = itemSimilarityMatrix.get(productId);

            if (similarities != null) {
                for (Integer likedProductId : userLikedProducts) {
                    Double similarity = similarities.get(likedProductId);
                    if (similarity != null) {
                        score += similarity;
                    }
                }
            }

            scores.put(productId, score);
        }

        return scores;
    }

    /**
     * 推断用户可能喜欢的产品
     */
    private Set<Integer> inferUserLikes(RecommendRequest request) {
        Set<Integer> likedProducts = new HashSet<>();

        // 基于口味偏好
        if (request.getPreferredFlavors() != null) {
            for (String flavor : request.getPreferredFlavors()) {
                likedProducts.addAll(getProductsByFlavor(flavor));
            }
        }

        // 基于甜度偏好
        if (request.getSweetLevel() != null) {
            likedProducts.addAll(getProductsBySweetLevel(request.getSweetLevel()));
        }

        // 基于低卡偏好
        if (Boolean.TRUE.equals(request.getPreferLowCalorie())) {
            likedProducts.addAll(getLowCalorieProducts());
        }

        // 如果没有任何偏好，返回热门产品
        if (likedProducts.isEmpty()) {
            likedProducts.addAll(getHotProducts());
        }

        return likedProducts;
    }

    /**
     * 构建物品相似度矩阵
     */
    private void buildSimilarityMatrix() {
        itemSimilarityMatrix = new HashMap<>();

        // 获取用户偏好数据
        Map<String, List<Integer>> userPreferences = dataService.getUserPreferences();

        // 构建物品-用户倒排表
        Map<Integer, Set<String>> itemUsers = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : userPreferences.entrySet()) {
            String userId = entry.getKey();
            for (Integer productId : entry.getValue()) {
                itemUsers.computeIfAbsent(productId, k -> new HashSet<>()).add(userId);
            }
        }

        // 计算物品共现矩阵
        Map<Integer, Map<Integer, Integer>> coOccurrence = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : userPreferences.entrySet()) {
            List<Integer> products = entry.getValue();
            for (int i = 0; i < products.size(); i++) {
                for (int j = i + 1; j < products.size(); j++) {
                    Integer p1 = products.get(i);
                    Integer p2 = products.get(j);

                    coOccurrence.computeIfAbsent(p1, k -> new HashMap<>())
                            .merge(p2, 1, Integer::sum);
                    coOccurrence.computeIfAbsent(p2, k -> new HashMap<>())
                            .merge(p1, 1, Integer::sum);
                }
            }
        }

        // 计算余弦相似度
        for (Integer i : dataService.getProductMap().keySet()) {
            Map<Integer, Double> similarities = new HashMap<>();
            int ni = itemUsers.getOrDefault(i, Collections.emptySet()).size();

            for (Integer j : dataService.getProductMap().keySet()) {
                if (i.equals(j)) continue;

                int nj = itemUsers.getOrDefault(j, Collections.emptySet()).size();
                int nij = coOccurrence.getOrDefault(i, Collections.emptyMap())
                        .getOrDefault(j, 0);

                if (ni > 0 && nj > 0 && nij > 0) {
                    // 余弦相似度
                    double similarity = nij / Math.sqrt((double) ni * nj);
                    similarities.put(j, similarity);
                }
            }

            itemSimilarityMatrix.put(i, similarities);
        }
    }

    /**
     * 根据口味获取产品
     */
    private Set<Integer> getProductsByFlavor(String flavor) {
        Set<Integer> products = new HashSet<>();

        for (MilkTeaProduct product : dataService.getProductMap().values()) {
            String name = product.getName().toLowerCase();

            switch (flavor) {
                case "甜":
                    if (name.contains("糖") || name.contains("蜜") || product.getSugar() > 40) {
                        products.add(product.getId());
                    }
                    break;
                case "清爽":
                    if (name.contains("柠") || name.contains("柚") || name.contains("果茶") ||
                        name.contains("四季春") || name.contains("绿")) {
                        products.add(product.getId());
                    }
                    break;
                case "浓郁":
                    if (name.contains("芝士") || name.contains("奶盖") || name.contains("牛乳") ||
                        name.contains("芝芝")) {
                        products.add(product.getId());
                    }
                    break;
                case "果茶":
                    if (name.contains("葡萄") || name.contains("草莓") || name.contains("芒果") ||
                        name.contains("橙") || name.contains("桃")) {
                        products.add(product.getId());
                    }
                    break;
                case "奶茶":
                    if (name.contains("奶茶") || name.contains("牛乳") || name.contains("拿铁")) {
                        products.add(product.getId());
                    }
                    break;
            }
        }

        return products;
    }

    /**
     * 根据甜度获取产品
     */
    private Set<Integer> getProductsBySweetLevel(int level) {
        Set<Integer> products = new HashSet<>();

        for (MilkTeaProduct product : dataService.getProductMap().values()) {
            int sugar = product.getSugar();
            int productLevel;

            if (sugar < 20) productLevel = 1;
            else if (sugar < 35) productLevel = 2;
            else if (sugar < 45) productLevel = 3;
            else if (sugar < 55) productLevel = 4;
            else productLevel = 5;

            if (Math.abs(productLevel - level) <= 1) {
                products.add(product.getId());
            }
        }

        return products;
    }

    /**
     * 获取低卡产品
     */
    private Set<Integer> getLowCalorieProducts() {
        Set<Integer> products = new HashSet<>();

        for (MilkTeaProduct product : dataService.getProductMap().values()) {
            if (product.getCalorie() < 200) {
                products.add(product.getId());
            }
        }

        return products;
    }

    /**
     * 获取热门产品
     */
    private Set<Integer> getHotProducts() {
        Set<Integer> products = new HashSet<>();

        for (DataService.PopularRanking ranking : dataService.getPopularRankings()) {
            if (ranking.getRank() <= 5) {
                products.add(ranking.getProductId());
            }
        }

        return products;
    }
}
