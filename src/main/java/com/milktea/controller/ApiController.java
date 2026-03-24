package com.milktea.controller;

import com.milktea.entity.*;
import com.milktea.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * API控制器
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final CalorieService calorieService;
    private final DataService dataService;
    private final ImageService imageService;
    private final RecommendService recommendService;
    private final UserService userService;
    private final DeepseekRecommendService deepseekRecommendService;
    private final TraditionalRecommendService traditionalRecommendService;
    private final SmartMatchRecommendService smartMatchRecommendService;

    // ==================== 用户认证相关API ====================

    /**
     * 用户登录/注册
     */
    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        Map<String, Object> response = new HashMap<>();

        // 验证用户ID格式
        if (!userService.isValidUserId(userId)) {
            response.put("success", false);
            response.put("message", "用户ID格式不正确，请输入纯数字");
            return ResponseEntity.badRequest().body(response);
        }

        boolean isNewUser = !userService.userExists(userId);
        User user = userService.loginOrRegister(userId);

        if (user == null) {
            response.put("success", false);
            response.put("message", "登录失败，请重试");
            return ResponseEntity.badRequest().body(response);
        }

        response.put("success", true);
        response.put("isNewUser", isNewUser);
        response.put("userName", user.getUserId());
        response.put("message", isNewUser ? "注册成功" : "登录成功");

        return ResponseEntity.ok(response);
    }

    /**
     * 更新用户档案
     */
    @PostMapping("/user/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> body) {
        String userId = (String) body.get("userId");
        Map<String, Object> response = new HashMap<>();

        try {
            @SuppressWarnings("unchecked")
            List<String> diseaseHistory = (List<String>) body.get("diseaseHistory");
            @SuppressWarnings("unchecked")
            List<String> allergies = (List<String>) body.get("allergies");
            @SuppressWarnings("unchecked")
            List<String> preferredFlavors = (List<String>) body.get("preferredFlavors");

            Integer sweetLevel = body.get("sweetLevel") != null ?
                    ((Number) body.get("sweetLevel")).intValue() : null;
            Boolean preferLowCalorie = (Boolean) body.get("preferLowCalorie");

            userService.updateUserProfile(userId, diseaseHistory, allergies, preferredFlavors, sweetLevel, preferLowCalorie);

            response.put("success", true);
            response.put("message", "档案更新成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserInfo(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        User user = userService.getUser(userId);

        if (user == null) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(response);
        }

        response.put("success", true);
        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    /**
     * 添加用户反馈
     */
    @PostMapping("/user/feedback")
    public ResponseEntity<Map<String, Object>> addUserFeedback(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = (String) body.get("userId");
            Integer productId = (Integer) body.get("productId");
            String productName = (String) body.get("productName");
            String brandName = (String) body.get("brandName");
            Integer likeRating = body.get("likeRating") != null ? ((Number) body.get("likeRating")).intValue() : null;
            String feedback = (String) body.get("feedback");
            String recommendSource = (String) body.get("recommendSource");
            String sugarLevel = (String) body.get("sugarLevel");
            String temperature = (String) body.get("temperature");

            @SuppressWarnings("unchecked")
            List<String> toppingNames = (List<String>) body.get("toppingNames");

            User.UserSelection selection = new User.UserSelection();
            selection.setProductId(productId);
            selection.setProductName(productName);
            selection.setBrandName(brandName);
            selection.setLikeRating(likeRating);
            selection.setFeedback(feedback);
            selection.setRecommendSource(recommendSource);
            selection.setSugarLevel(sugarLevel);
            selection.setTemperature(temperature);
            selection.setToppingNames(toppingNames != null ? toppingNames : new ArrayList<>());

            userService.addUserSelection(userId, selection);

            response.put("success", true);
            response.put("message", "反馈提交成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "提交失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ==================== 三种推荐方法API ====================

    /**
     * 获取三种推荐方法的结果
     */
    @PostMapping("/recommend/all")
    public ResponseEntity<RecommendResultV2> getAllRecommendations(@RequestBody RecommendRequestV2 request) {
        RecommendResultV2 result = new RecommendResultV2();

        try {
            String userId = request.getUserId();

            // 1. Deepseek AI推荐
            List<RecommendResultV2.RecommendItem> deepseekItems =
                    deepseekRecommendService.recommend(userId, request);
            result.setDeepseekRecommendations(deepseekItems);

            // 2. 传统推荐（协同过滤）
            List<RecommendResultV2.RecommendItem> traditionalItems =
                    traditionalRecommendService.recommend(userId, request);
            result.setTraditionalRecommendations(traditionalItems);

            // 3. 智能匹配推荐（词向量）
            List<RecommendResultV2.RecommendItem> smartMatchItems =
                    smartMatchRecommendService.recommend(userId, request);
            result.setSmartMatchRecommendations(smartMatchItems);

            result.setSuccess(true);
            result.setMessage("推荐成功");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("推荐失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取所有品牌
     */
    @GetMapping("/brands")
    public ResponseEntity<List<MilkTeaBrand>> getBrands() {
        return ResponseEntity.ok(calorieService.getAllBrands());
    }

    /**
     * 获取品牌下的产品
     */
    @GetMapping("/brands/{brandId}/products")
    public ResponseEntity<List<MilkTeaProduct>> getProductsByBrand(@PathVariable Integer brandId) {
        return ResponseEntity.ok(calorieService.getProductsByBrand(brandId));
    }

    /**
     * 获取所有小料
     */
    @GetMapping("/toppings")
    public ResponseEntity<List<Topping>> getToppings() {
        return ResponseEntity.ok(calorieService.getAllToppings());
    }

    /**
     * 计算热量
     */
    @PostMapping("/calorie/calculate")
    public ResponseEntity<CalorieResult> calculateCalorie(@RequestBody CalorieRequest request) {
        try {
            CalorieResult result = calorieService.calculateCalorie(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 识别小料图片
     */
    @PostMapping("/image/recognize")
    public ResponseEntity<Map<String, Object>> recognizeImage(@RequestParam("image") MultipartFile file) {
        try {
            // 转换为Base64
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());

            // 识别小料
            List<ImageService.ToppingRecognitionResult> results = imageService.recognizeToppings(base64);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("results", results);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "图片处理失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 识别Base64图片
     */
    @PostMapping("/image/recognize-base64")
    public ResponseEntity<Map<String, Object>> recognizeBase64Image(@RequestBody Map<String, String> body) {
        try {
            String base64 = body.get("image");

            // 识别小料
            List<ImageService.ToppingRecognitionResult> results = imageService.recognizeToppings(base64);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("results", results);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "图片识别失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取推荐
     */
    @PostMapping("/recommend")
    public ResponseEntity<RecommendResult> getRecommendations(@RequestBody RecommendRequest request) {
        try {
            RecommendResult result = recommendService.getRecommendations(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取热门排行
     */
    @GetMapping("/hot")
    public ResponseEntity<List<Map<String, Object>>> getHotRankings() {
        List<Map<String, Object>> hotList = dataService.getPopularRankings().stream()
                .limit(10)
                .map(ranking -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("rank", ranking.getRank());
                    item.put("productId", ranking.getProductId());
                    item.put("brandName", ranking.getBrandName());
                    item.put("productName", ranking.getProductName());
                    item.put("hotScore", ranking.getHotScore());
                    item.put("recommendReason", ranking.getRecommendReason());
                    item.put("tags", ranking.getTags());

                    // 添加产品详情
                    MilkTeaProduct product = dataService.getProductMap().get(ranking.getProductId());
                    if (product != null) {
                        item.put("image", product.getImage());
                        item.put("calorie", product.getCalorie());
                    }

                    return item;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(hotList);
    }

    /**
     * 获取所有运动
     */
    @GetMapping("/exercises")
    public ResponseEntity<List<Exercise>> getExercises() {
        return ResponseEntity.ok(new ArrayList<>(dataService.getExerciseMap().values()));
    }

    /**
     * 搜索产品
     */
    @GetMapping("/search")
    public ResponseEntity<List<MilkTeaProduct>> searchProducts(@RequestParam String keyword) {
        List<MilkTeaProduct> results = dataService.getProductMap().values().stream()
                .filter(p -> p.getName().toLowerCase().contains(keyword.toLowerCase()) ||
                        p.getBrandName().toLowerCase().contains(keyword.toLowerCase()))
                .limit(20)
                .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }
}
