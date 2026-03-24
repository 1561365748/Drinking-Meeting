package com.milktea.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.milktea.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deepseek AI辅助推荐服务
 * 使用Deepseek API进行智能推荐
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepseekRecommendService {

    private final DataService dataService;
    private final UserService userService;

    @Value("${deepseek.api.key:}")
    private String apiKey;

    @Value("${deepseek.api.url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 获取Deepseek推荐
     */
    public List<RecommendResultV2.RecommendItem> recommend(String userId, RecommendRequestV2 request) {
        try {
            // 获取用户档案
            User user = userService.getUser(userId);

            // 构建prompt
            String prompt = buildPrompt(user, request);

            // 调用Deepseek API
            String aiResponse = callDeepseekAPI(prompt);

            // 解析AI响应
            return parseAIResponse(aiResponse);
        } catch (Exception e) {
            log.error("Deepseek推荐失败: {}", e.getMessage());
            // 降级处理：返回基于规则的热门推荐
            return getFallbackRecommendations(request);
        }
    }

    /**
     * 构建prompt
     */
    private String buildPrompt(User user, RecommendRequestV2 request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("【系统】你是一个专业的奶茶推荐助手。请严格按照以下规则工作：\n");
        prompt.append("1.推荐时必须严格遵循用户的忌口和疾病史。\n");
        prompt.append("2.如果用户未指定品牌，你可以基于常见奶茶品类进行推荐，并可询问用户偏好的品牌，或在理由中说明推荐属于哪类饮品（如果汁茶、鲜奶茶等）。\n");
        prompt.append("3.直接输出推荐列表，每项包含饮品名、小料、糖度、温度、推荐理由。\n");
        prompt.append("4.参考下面 few-shot 示例的输出格式。\n\n");

        // 添加few-shot示例
        prompt.append("【示例1】（信息完整：有品牌、有历史）\n");
        prompt.append("用户画像：\n");
        prompt.append("口味喜好：浓郁、甜口\n");
        prompt.append("忌口：无\n");
        prompt.append("疾病史：无\n");
        prompt.append("奶茶品牌：喜茶\n");
        prompt.append("历史选择：常点'黑糖波波牛乳'\n");
        prompt.append("历史反馈：喜欢波波，不喜欢太甜的果茶\n");
        prompt.append("推荐：\n");
        prompt.append("1.烤黑糖波波牛乳（正常糖，热）+ 波波 —— 焦糖香气浓郁，奶味厚重，符合用户对浓郁甜口的偏好。\n");
        prompt.append("2.黑糖波波牛乳（少糖，热）+ 波波 —— 经典款，少糖避免过甜，满足用户对黑糖的喜爱。\n");
        prompt.append("3.厚烧蛋糕波波牛乳（正常糖，热）+ 波波 —— 带有蛋糕酱的奶香，口感更丰富，符合甜口需求。\n\n");

        prompt.append("【示例2】（信息缺失：无品牌、无历史）\n");
        prompt.append("用户画像：\n");
        prompt.append("口味喜好：清爽、茶味重，微糖\n");
        prompt.append("忌口：乳糖不耐受\n");
        prompt.append("疾病史：无\n");
        prompt.append("奶茶品牌：未提供\n");
        prompt.append("历史选择：未提供\n");
        prompt.append("历史反馈：未提供\n");
        prompt.append("推荐：\n");
        prompt.append("1.水果茶（如葡萄、柠檬、百香果基底，微糖，去冰）+ 脆波波或椰果 —— 清爽酸甜，茶味明显，无乳制品，适合乳糖不耐受。\n");
        prompt.append("2.纯茶（如四季春、茉莉绿茶，无糖，热）不加小料 —— 突出茶香，完全无糖无奶，最安全的选择。\n");
        prompt.append("3.燕麦奶/豆奶奶茶（如燕麦奶红茶，微糖，热）+ 仙草 —— 植物奶替代牛奶，满足想喝奶茶的需求，仙草低卡不甜腻。\n\n");

        // 构建当前用户画像
        prompt.append("【当前用户】\n");

        // 口味喜好
        List<String> flavors = new ArrayList<>();
        if (request.getPreferredFlavors() != null && !request.getPreferredFlavors().isEmpty()) {
            flavors.addAll(request.getPreferredFlavors());
        } else if (user != null && user.getPreferredFlavors() != null) {
            flavors.addAll(user.getPreferredFlavors());
        }
        prompt.append("口味喜好：").append(flavors.isEmpty() ? "未指定" : String.join("、", flavors)).append("\n");

        // 忌口
        List<String> allergies = new ArrayList<>();
        if (user != null && user.getAllergies() != null) {
            allergies = user.getAllergies().stream()
                    .filter(a -> !a.equals("无"))
                    .collect(Collectors.toList());
        }
        prompt.append("忌口：").append(allergies.isEmpty() ? "无" : String.join("、", allergies)).append("\n");

        // 疾病史
        List<String> diseases = new ArrayList<>();
        if (user != null && user.getDiseaseHistory() != null) {
            diseases = user.getDiseaseHistory().stream()
                    .filter(d -> !d.equals("无"))
                    .collect(Collectors.toList());
        }
        prompt.append("疾病史：").append(diseases.isEmpty() ? "无" : String.join("、", diseases)).append("\n");

        // 奶茶品牌
        List<String> brandNames = new ArrayList<>();
        if (request.getBrandIds() != null && !request.getBrandIds().isEmpty()) {
            for (Integer brandId : request.getBrandIds()) {
                MilkTeaBrand brand = dataService.getBrandMap().get(brandId);
                if (brand != null) {
                    brandNames.add(brand.getName());
                }
            }
        }
        prompt.append("奶茶品牌：").append(brandNames.isEmpty() ? "未提供" : String.join("、", brandNames)).append("\n");

        // 历史选择
        List<String> historyProducts = new ArrayList<>();
        if (user != null && user.getHistorySelections() != null && !user.getHistorySelections().isEmpty()) {
            int count = Math.min(3, user.getHistorySelections().size());
            for (int i = 0; i < count; i++) {
                User.UserSelection sel = user.getHistorySelections().get(user.getHistorySelections().size() - 1 - i);
                historyProducts.add(sel.getProductName() + "（" + sel.getBrandName() + "）");
            }
        }
        prompt.append("历史选择：").append(historyProducts.isEmpty() ? "未提供" : String.join("、", historyProducts)).append("\n");

        // 历史反馈
        List<String> feedbacks = new ArrayList<>();
        if (user != null && user.getHistorySelections() != null) {
            for (User.UserSelection sel : user.getHistorySelections()) {
                if (sel.getFeedback() != null && !sel.getFeedback().isEmpty()) {
                    feedbacks.add(sel.getFeedback());
                }
            }
        }
        prompt.append("历史反馈：").append(feedbacks.isEmpty() ? "未提供" : feedbacks.get(0)).append("\n");

        // 用户备注
        if (request.getNote() != null && !request.getNote().isEmpty()) {
            prompt.append("用户备注：").append(request.getNote()).append("\n");
        }

        // 甜度偏好
        if (request.getSweetLevel() != null) {
            String sweetDesc = getSweetLevelDesc(request.getSweetLevel());
            prompt.append("甜度偏好：").append(sweetDesc).append("\n");
        }

        // 低卡偏好
        if (Boolean.TRUE.equals(request.getPreferLowCalorie())) {
            prompt.append("特殊需求：优先低卡路里\n");
        }

        prompt.append("\n请直接输出5条推荐，不需要思考过程。格式为：序号.饮品名（糖度，温度）+ 小料 —— 推荐理由");

        return prompt.toString();
    }

    /**
     * 调用Deepseek API
     * 构建 HTTP 请求，向 Deepseek API 发送推荐请求。
        解析 API 的响应，提取推荐结果。
        如果响应成功且包含推荐内容，返回推荐结果的文本内容
     */
    private String callDeepseekAPI(String prompt) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Deepseek API Key未配置，使用模拟响应");
            return getMockResponse();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            // 创建一个 Map 对象作为请求体。model 指定使用的模型（如 deepseek-chat）。
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 1000);

            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(requestBody), headers);
            // 发送 HTTP 请求
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject jsonResponse = JSON.parseObject(response.getBody());
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    return choices.getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                }
            }
        } catch (Exception e) {
            log.error("调用Deepseek API失败: {}", e.getMessage());
        }

        return getMockResponse();
    }

    /**
     * 获取模拟响应（API未配置时使用）
     */
    private String getMockResponse() {
        return "1.多肉葡萄（少糖，去冰）+ 脆波波 —— 经典果茶，清爽不腻，葡萄果肉丰富。\n" +
               "2.芝芝莓莓（半糖，去冰）+ 芝士奶盖 —— 草莓香甜，芝士浓郁，满足果茶爱好者。\n" +
               "3.满杯红柚（微糖，去冰）+ 椰果 —— 红柚清香，酸甜适中，解腻首选。\n" +
               "4.杨枝甘露（少糖，去冰）+ 芒果丁 —— 经典港式甜品，芒果香甜，椰浆顺滑。\n" +
               "5.冰鲜柠檬水（无糖，去冰）—— 清爽解渴，零热量负担，健康首选。";
    }

    /**
     * 解析AI响应
     */
    private List<RecommendResultV2.RecommendItem> parseAIResponse(String aiResponse) {
        List<RecommendResultV2.RecommendItem> items = new ArrayList<>();

        // 解析AI返回的推荐列表
        String[] lines = aiResponse.split("\n");
        int rank = 1;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || !line.matches("^\\d+\\..*")) {
                continue;
            }

            // 移除序号
            line = line.replaceFirst("^\\d+\\.", "").trim();

            RecommendResultV2.RecommendItem item = parseRecommendLine(line, rank);
            if (item != null) {
                items.add(item);
                rank++;
            }

            if (items.size() >= 5) break;
        }

        // 如果解析失败，返回默认推荐
        if (items.isEmpty()) {
            return getFallbackRecommendations(null);
        }

        return items;
    }

    /**
     * 解析单行推荐
     */
    private RecommendResultV2.RecommendItem parseRecommendLine(String line, int rank) {
        try {
            RecommendResultV2.RecommendItem item = new RecommendResultV2.RecommendItem();

            // 尝试匹配格式：饮品名（糖度，温度）+ 小料 —— 推荐理由
            Pattern pattern = Pattern.compile("(.+?)[（(](.+?)[）)]\\s*(?:\\+\\s*(.+?))?\\s*[—-]+\\s*(.+)");
            Matcher matcher = pattern.matcher(line);

            if (matcher.find()) {
                String productName = matcher.group(1).trim();
                String sugarTemp = matcher.group(2).trim();
                String toppings = matcher.group(3) != null ? matcher.group(3).trim() : "";
                String reason = matcher.group(4).trim();

                // 解析糖度和温度
                String[] parts = sugarTemp.split("[，,]");
                String sugar = parts.length > 0 ? parts[0].trim() : "半糖";
                String temp = parts.length > 1 ? parts[1].trim() : "常温";

                // 尝试匹配产品
                MilkTeaProduct product = findProductByName(productName);

                item.setProductName(productName);
                item.setSuggestedSugar(sugar);
                item.setSuggestedTemperature(temp);
                item.setRecommendReason(reason);
                item.setRecommendLevel(6 - Math.min(rank, 5)); // 5,4,3,2,1

                if (product != null) {
                    item.setProductId(product.getId());
                    item.setBrandName(product.getBrandName());
                    item.setCalorie(product.getCalorie());
                    item.setImageUrl(product.getImage());
                } else {
                    item.setBrandName("推荐");
                    item.setCalorie(200);
                    item.setImageUrl("/img/milktea/default.jpg");
                }

                // 解析小料
                if (!toppings.isEmpty()) {
                    item.setSuggestedToppings(Arrays.asList(toppings.split("[、,+]")));
                }
            } else {
                // 简单格式解析
                item.setProductName(line);
                item.setBrandName("推荐");
                item.setCalorie(200);
                item.setSuggestedSugar("半糖");
                item.setSuggestedTemperature("常温");
                item.setRecommendReason("AI推荐");
                item.setRecommendLevel(6 - Math.min(rank, 5));
            }

            return item;
        } catch (Exception e) {
            log.error("解析推荐行失败: {}", line, e);
            return null;
        }
    }

    /**
     * 根据名称查找产品
     */
    private MilkTeaProduct findProductByName(String name) {
        for (MilkTeaProduct product : dataService.getProductMap().values()) {
            if (name.contains(product.getName()) || product.getName().contains(name)) {
                return product;
            }
        }
        return null;
    }

    /**
     * 降级推荐（当API调用失败时）
     */
    private List<RecommendResultV2.RecommendItem> getFallbackRecommendations(RecommendRequestV2 request) {
        List<RecommendResultV2.RecommendItem> items = new ArrayList<>();

        // 获取热门产品
        List<DataService.PopularRanking> rankings = dataService.getPopularRankings();

        int count = 0;
        for (DataService.PopularRanking ranking : rankings) {
            if (count >= 5) break;

            MilkTeaProduct product = dataService.getProductMap().get(ranking.getProductId());
            if (product == null) continue;

            // 检查品牌过滤
            if (request != null && request.getBrandIds() != null && !request.getBrandIds().isEmpty()) {
                if (!request.getBrandIds().contains(product.getBrandId())) {
                    continue;
                }
            }

            RecommendResultV2.RecommendItem item = new RecommendResultV2.RecommendItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setBrandName(product.getBrandName());
            item.setImageUrl(product.getImage());
            item.setCalorie(product.getCalorie());
            item.setRecommendLevel(5 - count);
            item.setRecommendReason(ranking.getRecommendReason());
            item.setSuggestedSugar(getSweetLevelDesc(request != null ? request.getSweetLevel() : 3));
            item.setSuggestedTemperature("常温");
            item.setTags(ranking.getTags());

            items.add(item);
            count++;
        }

        return items;
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
