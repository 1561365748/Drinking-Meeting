package com.milktea.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.milktea.entity.Topping;
import com.milktea.util.ImagePreprocessor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图片识别服务
 * 使用百度AI图像识别API（免费额度）
 */
@Service
@RequiredArgsConstructor
public class ImageService {

    private final DataService dataService;
    private final ImagePreprocessor imagePreprocessor;

    @Value("${baidu.ai.api-key:}")
    private String apiKey;

    @Value("${baidu.ai.secret-key:}")
    private String secretKey;

    // Access Token缓存
    private String accessToken;
    private long tokenExpireTime;

    /**
     * 识别小料图片
     * @param imageBase64 Base64编码的图片
     * @return 识别结果（小料ID列表）
     */
    public List<ToppingRecognitionResult> recognizeToppings(String imageBase64) {
        try {
            // 1. 图片预处理（去噪、增强）
            String processedImage = imagePreprocessor.preprocess(imageBase64);

            // 2. 调用百度AI识别
            List<String> keywords = callBaiduAI(processedImage);

            // 3. 匹配小料数据库
            return matchToppings(keywords);

        } catch (Exception e) {
            System.err.println("图片识别失败: " + e.getMessage());
            // 降级：返回空列表或默认推荐
            return getDefaultToppings();
        }
    }

    /**
     * 获取百度AI Access Token
     */
    private String getAccessToken() throws IOException, InterruptedException {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String url = "https://aip.baidubce.com/oauth/2.0/token?" +
                "grant_type=client_credentials" +
                "&client_id=" + apiKey +
                "&client_secret=" + secretKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = JSON.parseObject(response.body());

        accessToken = json.getString("access_token");
        tokenExpireTime = System.currentTimeMillis() + json.getLong("expires_in") * 1000 - 60000;

        return accessToken;
    }

    /**
     * 调用百度AI图像识别
     */
    private List<String> callBaiduAI(String imageBase64) throws IOException, InterruptedException {
        // 如果没有配置API密钥，使用模拟识别
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY")) {
            return simulateRecognition();
        }

        String token = getAccessToken();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        String url = "https://aip.baidubce.com/rest/2.0/image-classify/v2/advanced_general" +
                "?access_token=" + token;

        String body = "image=" + URLEncoder.encode(imageBase64, "UTF-8");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = JSON.parseObject(response.body());

        List<String> keywords = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map> results = json.getList("result", Map.class);
        if (results != null) {
            for (Map item : results) {
                String keyword = (String) item.get("keyword");
                if (keyword != null) {
                    keywords.add(keyword);
                }
            }
        }

        return keywords;
    }

    /**
     * 模拟识别（当API不可用时）
     */
    private List<String> simulateRecognition() {
        // 返回一些常见的小料关键词用于演示
        return Arrays.asList("珍珠", "椰果", "布丁", "芝士", "芋泥");
    }

    /**
     * 匹配小料数据库
     * 该方法支持精确匹配和模糊匹配，并为每个匹配结果设置置信度。
     */
    private List<ToppingRecognitionResult> matchToppings(List<String> keywords) {
        List<ToppingRecognitionResult> results = new ArrayList<>();
        Map<Integer, Topping> toppingMap = dataService.getToppingMap();

        // 关键词映射表
        // 查每个关键词是否存在于 keywordToToppingId 映射表中。
        Map<String, Integer> keywordToToppingId = new HashMap<>();
        keywordToToppingId.put("珍珠", 1);
        keywordToToppingId.put("波霸", 1);
        keywordToToppingId.put("椰果", 2);
        keywordToToppingId.put("芝士", 3);
        keywordToToppingId.put("奶盖", 3);
        keywordToToppingId.put("布丁", 4);
        keywordToToppingId.put("仙草", 5);
        keywordToToppingId.put("芋泥", 6);
        keywordToToppingId.put("红豆", 7);
        keywordToToppingId.put("芋圆", 8);
        keywordToToppingId.put("燕麦", 9);
        keywordToToppingId.put("西米", 10);
        keywordToToppingId.put("奥利奥", 21);
        keywordToToppingId.put("麻薯", 22);
        
        // 使用 matchedIds 集合，确保每个小料只匹配一次，避免重复结果。
        Set<Integer> matchedIds = new HashSet<>();

        for (String keyword : keywords) {
            Integer toppingId = keywordToToppingId.get(keyword);
            if (toppingId != null && !matchedIds.contains(toppingId)) {
                Topping topping = toppingMap.get(toppingId);
                if (topping != null) {
                    ToppingRecognitionResult result = new ToppingRecognitionResult();
                    result.setToppingId(toppingId);
                    result.setToppingName(topping.getName());
                    result.setCalorie(topping.getCalorie());
                    result.setConfidence(0.85);
                    results.add(result);
                    matchedIds.add(toppingId);
                }
            }

            // 模糊匹配
            // 检查小料名称是否包含关键词，或关键词是否包含小料名称。
            for (Topping topping : toppingMap.values()) {
                if (!matchedIds.contains(topping.getId()) &&
                        (topping.getName().contains(keyword) || keyword.contains(topping.getName()))) {
                    ToppingRecognitionResult result = new ToppingRecognitionResult();
                    result.setToppingId(topping.getId());
                    result.setToppingName(topping.getName());
                    result.setCalorie(topping.getCalorie());
                    result.setConfidence(0.7);
                    results.add(result);
                    matchedIds.add(topping.getId());
                }
            }
        }

        return results;
    }

    /**
     * 获取默认小料推荐
     */
    private List<ToppingRecognitionResult> getDefaultToppings() {
        List<ToppingRecognitionResult> results = new ArrayList<>();
        // 返回热门小料
        int[] popularIds = {1, 3, 4}; // 珍珠、芝士、布丁
        for (int id : popularIds) {
            Topping topping = dataService.getToppingMap().get(id);
            if (topping != null) {
                ToppingRecognitionResult result = new ToppingRecognitionResult();
                result.setToppingId(id);
                result.setToppingName(topping.getName());
                result.setCalorie(topping.getCalorie());
                result.setConfidence(0.5);
                results.add(result);
            }
        }
        return results;
    }

    /**
     * 小料识别结果
     */
    @lombok.Data
    public static class ToppingRecognitionResult {
        private Integer toppingId;
        private String toppingName;
        private Integer calorie;
        private Double confidence; // 置信度 0-1
    }

    // URL编码辅助方法
    private String URLEncoder(String s, String encoding) throws java.io.UnsupportedEncodingException {
        return java.net.URLEncoder.encode(s, encoding);
    }
}
