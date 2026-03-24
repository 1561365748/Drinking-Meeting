package com.milktea.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.milktea.entity.*;
import lombok.Getter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据加载服务
 */
@Service
public class DataService {

    @Getter
    private Map<Integer, MilkTeaBrand> brandMap = new HashMap<>();
    @Getter
    private Map<Integer, MilkTeaProduct> productMap = new HashMap<>();
    @Getter
    private Map<Integer, Topping> toppingMap = new HashMap<>();
    @Getter
    private Map<Integer, Exercise> exerciseMap = new HashMap<>();
    @Getter
    private List<PopularRanking> popularRankings = new ArrayList<>();
    @Getter
    private Map<Integer, Double> productHotScores = new HashMap<>();
    @Getter
    private Map<Integer, Double> toppingHotScores = new HashMap<>();
    @Getter
    private Map<String, List<Integer>> userPreferences = new HashMap<>();

    @Getter
    private Map<String, Double> sizeMultiplier = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            loadDataFiles();
        } catch (Exception e) {
            System.err.println("加载数据文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadDataFiles() throws IOException {
        // 尝试从外部data目录加载，如果不存在则从classpath加载
        Path dataPath = Paths.get("data");

        // 加载奶茶数据
        String milkTeaJson = loadJsonFile(dataPath.resolve("milk_tea_data.json"));
        if (milkTeaJson != null) {
            parseMilkTeaData(milkTeaJson);
        }

        // 加载小料数据
        String toppingJson = loadJsonFile(dataPath.resolve("topping_data.json"));
        if (toppingJson != null) {
            parseToppingData(toppingJson);
        }

        // 加载运动数据
        String exerciseJson = loadJsonFile(dataPath.resolve("exercise_data.json"));
        if (exerciseJson != null) {
            parseExerciseData(exerciseJson);
        }

        // 加载热门排行数据
        String rankingJson = loadJsonFile(dataPath.resolve("popular_ranking.json"));
        if (rankingJson != null) {
            parseRankingData(rankingJson);
        }

        System.out.println("数据加载完成: 品牌=" + brandMap.size() +
                ", 产品=" + productMap.size() +
                ", 小料=" + toppingMap.size() +
                ", 运动=" + exerciseMap.size());
    }

    private String loadJsonFile(Path path) throws IOException {
        // 首先尝试从外部文件加载
        if (Files.exists(path)) {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        // 然后尝试从classpath加载
        try {
            ClassPathResource resource = new ClassPathResource("data/" + path.getFileName().toString());
            if (resource.exists()) {
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    private void parseMilkTeaData(String json) {
        JSONObject obj = JSON.parseObject(json);
        List<Map> brands = obj.getList("brands", Map.class);

        for (Map brandMap : brands) {
            MilkTeaBrand brand = new MilkTeaBrand();
            brand.setId((Integer) brandMap.get("id"));
            brand.setName((String) brandMap.get("name"));
            brand.setLogo((String) brandMap.get("logo"));

            List<MilkTeaProduct> products = new ArrayList<>();
            List<Map> productsList = (List<Map>) brandMap.get("products");
            for (Map productMap : productsList) {
                MilkTeaProduct product = new MilkTeaProduct();
                product.setId((Integer) productMap.get("id"));
                product.setName((String) productMap.get("name"));
                product.setCalorie((Integer) productMap.get("calorie"));
                product.setSugar((Integer) productMap.get("sugar"));
                product.setCarbs((Integer) productMap.get("carbs"));
                product.setImage((String) productMap.get("image"));
                product.setBrandId(brand.getId());
                product.setBrandName(brand.getName());
                products.add(product);
                this.productMap.put(product.getId(), product);
            }
            brand.setProducts(products);
            this.brandMap.put(brand.getId(), brand);
        }

        // 解析容量倍数
        Map multipliers = obj.getObject("sizeMultiplier", Map.class);
        if (multipliers != null) {
            for (Map.Entry entry : (Set<Map.Entry>) multipliers.entrySet()) {
                this.sizeMultiplier.put((String) entry.getKey(),
                        ((Number) entry.getValue()).doubleValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void parseToppingData(String json) {
        JSONObject obj = JSON.parseObject(json);
        List<Map> toppings = obj.getList("toppings", Map.class);

        for (Map toppingMap : toppings) {
            Topping topping = new Topping();
            topping.setId((Integer) toppingMap.get("id"));
            topping.setName((String) toppingMap.get("name"));
            topping.setCalorie((Integer) toppingMap.get("calorie"));
            topping.setSugar((Integer) toppingMap.get("sugar"));
            topping.setCarbs((Integer) toppingMap.get("carbs"));
            topping.setImage((String) toppingMap.get("image"));
            topping.setCategory((String) toppingMap.get("category"));
            this.toppingMap.put(topping.getId(), topping);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseExerciseData(String json) {
        JSONObject obj = JSON.parseObject(json);
        List<Map> exercises = obj.getList("exercises", Map.class);

        for (Map exerciseMap : exercises) {
            Exercise exercise = new Exercise();
            exercise.setId((Integer) exerciseMap.get("id"));
            exercise.setName((String) exerciseMap.get("name"));
            exercise.setCaloriePerHour((Integer) exerciseMap.get("caloriePerHour"));
            exercise.setImage((String) exerciseMap.get("image"));
            exercise.setDescription((String) exerciseMap.get("description"));
            exercise.setIntensity((String) exerciseMap.get("intensity"));
            this.exerciseMap.put(exercise.getId(), exercise);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseRankingData(String json) {
        JSONObject obj = JSON.parseObject(json);
        List<Map> rankings = obj.getList("rankings", Map.class);

        for (Map rankingMap : rankings) {
            PopularRanking ranking = new PopularRanking();
            ranking.setRank((Integer) rankingMap.get("rank"));
            ranking.setProductId((Integer) rankingMap.get("productId"));
            ranking.setBrandName((String) rankingMap.get("brandName"));
            ranking.setProductName((String) rankingMap.get("productName"));
            ranking.setHotScore((Integer) rankingMap.get("hotScore"));
            ranking.setSocialHotScore((Integer) rankingMap.get("socialHotScore"));
            ranking.setRecommendReason((String) rankingMap.get("recommendReason"));
            ranking.setTags((List<String>) rankingMap.get("tags"));
            this.popularRankings.add(ranking);

            // 计算综合热度分数 (归一化到0-100)
            double hotScore = ranking.getHotScore() * 0.5 + ranking.getSocialHotScore() * 0.5;
            this.productHotScores.put(ranking.getProductId(), hotScore);
        }

        // 解析小料热度
        List<Map> toppingRankings = obj.getList("toppingRankings", Map.class);
        if (toppingRankings != null) {
            for (Map tr : toppingRankings) {
                Integer toppingId = (Integer) tr.get("toppingId");
                Integer hotScore = (Integer) tr.get("hotScore");
                this.toppingHotScores.put(toppingId, hotScore.doubleValue());
            }
        }

        // 解析用户偏好数据 (用于协同过滤)
        List<Map> userPrefs = obj.getList("userPreferences", Map.class);
        if (userPrefs != null) {
            for (Map up : userPrefs) {
                String userId = (String) up.get("userId");
                List<Integer> likedProducts = (List<Integer>) up.get("likedProducts");
                this.userPreferences.put(userId, likedProducts);
            }
        }
    }

    @lombok.Data
    public static class PopularRanking {
        private Integer rank;
        private Integer productId;
        private String brandName;
        private String productName;
        private Integer hotScore;
        private Integer socialHotScore;
        private String recommendReason;
        private List<String> tags;
    }
}
