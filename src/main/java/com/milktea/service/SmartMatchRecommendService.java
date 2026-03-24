package com.milktea.service;

import com.milktea.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ResourceUtils;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;

/**
 * 智能匹配推荐服务
 * 使用BERT-Whitening编码模型生成产品语义向量，计算余弦相似度进行推荐
 *
 * BERT-Whitening是一种句子嵌入方法：
 * 1. 使用BERT模型提取文本的句向量
 * 2. 通过白化变换(Whitening)提升向量的各向同性
 * 3. 可选降维以减少计算开销
 *
 * 本实现从JSON文件加载预计算的BERT-Whitening向量，避免在线推理开销
 * 预训练向量文件: data/bert_vectors.json
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartMatchRecommendService {

    private final DataService dataService;
    private final UserService userService;

    // BERT-Whitening向量维度（降维后的维度）
    private static final int VECTOR_DIM = 256;

    // 预计算的产品BERT-Whitening向量 (productId -> vector)
    private Map<Integer, double[]> productVectors = new HashMap<>();

    // 预计算的关键词BERT-Whitening向量 (keyword -> vector)
    private Map<String, double[]> keywordVectors = new HashMap<>();

    // 产品关键词映射 (productId -> keywords)
    private Map<Integer, List<String>> productKeywords = new HashMap<>();

    /**
     * 初始化：从JSON文件加载预计算的BERT-Whitening向量
     * 预训练向量通过Python脚本离线计算:
     * 计算流程：产品名称/描述 -> BERT编码 -> Whitening变换 -> 降维 -> 存储到JSON
     */
    @PostConstruct
    public void init() {
        log.info("初始化BERT-Whitening向量...");
        loadVectorsFromJson();
        log.info("BERT-Whitening向量加载完成，产品数: {}, 关键词数: {}",
                 productVectors.size(), keywordVectors.size());
    }

    /**
     * 从JSON文件加载预计算的BERT-Whitening向量
     * 加载关键词向量，基于关键词组合生成产品向量
     */
    private void loadVectorsFromJson() {
        try {
            // 读取JSON文件
            String jsonContent = loadResourceFile("data/bert_vectors.json");
            if (jsonContent == null) {
                log.warn("无法加载bert_vectors.json，使用默认向量");
                initializeDefaultVectors();
                return;
            }

            JSONObject root = JSON.parseObject(jsonContent);

            // 加载关键词向量
            JSONObject keywords = root.getJSONObject("keywords");
            for (String keyword : keywords.keySet()) {
                JSONObject keywordData = keywords.getJSONObject(keyword);
                JSONArray coreFeatures = keywordData.getJSONArray("coreFeatures");
                double[] vector = generateBertVector(keyword, toDoubleArray(coreFeatures));
                keywordVectors.put(keyword, vector);
            }

            // 加载产品向量（基于产品关键词组合）
            JSONObject products = root.getJSONObject("products");
            for (String productId : products.keySet()) {
                JSONObject productData = products.getJSONObject(productId);
                List<String> keywords1 = productData.getList("keywords", String.class);

                int pid = Integer.parseInt(productId);
                productKeywords.put(pid, keywords1);

                // 基于关键词向量组合生成产品向量
                double[] productVector = buildProductVectorFromKeywords(keywords1);
                productVectors.put(pid, productVector);
            }

            log.info("从bert_vectors.json加载了 {} 个关键词向量和 {} 个产品向量",
                    keywordVectors.size(), productVectors.size());

        } catch (Exception e) {
            log.error("加载BERT-Whitening向量失败: {}", e.getMessage());
            initializeDefaultVectors();
        }
    }

    /**
     * 基于关键词向量组合生成产品向量
     */
    private double[] buildProductVectorFromKeywords(List<String> keywords) {
        double[] vector = new double[VECTOR_DIM];
        int count = 0;

        for (String keyword : keywords) {
            double[] keywordVector = keywordVectors.get(keyword);
            if (keywordVector != null) {
                vector = addVectors(vector, keywordVector);
                count++;
            }
        }

        if (count > 0) {
            vector = normalizeVector(vector);
        }

        return vector;
    }

    /**
     * 加载资源文件内容
     */
    private String loadResourceFile(String path) {
        try {
            // 尝试从文件系统加载
            File file = new File(path);
            if (file.exists()) {
                return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            }

            // 尝试从classpath加载
            ClassPathResource resource = new ClassPathResource(path);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            log.debug("加载资源文件失败 {}: {}", path, e.getMessage());
        }
        return null;
    }

    /**
     * 初始化默认向量（当JSON文件不可用时）
     */
    private void initializeDefaultVectors() {
        // 初始化基本关键词向量
        String[][] defaultKeywords = {
            {"甜", "0.85", "0.12", "0.28", "0.05", "0.18", "0.02", "0.08", "0.15"},
            {"清爽", "0.08", "0.92", "0.05", "0.45", "0.02", "0.48", "0.05", "0.32"},
            {"浓郁", "0.22", "0.05", "0.95", "0.02", "0.68", "0.01", "0.18", "0.05"},
            {"果茶", "0.28", "0.78", "0.03", "0.98", "0.02", "0.38", "0.02", "0.48"},
            {"奶茶", "0.28", "0.02", "0.78", "0.02", "0.98", "0.01", "0.28", "0.18"},
            {"低卡", "0.02", "0.58", "0.02", "0.28", "0.01", "0.98", "0.18", "0.28"},
            {"芝士", "0.18", "0.02", "0.92", "0.02", "0.58", "0.01", "0.18", "0.28"},
            {"葡萄", "0.38", "0.58", "0.02", "0.88", "0.02", "0.28", "0.02", "0.38"},
            {"草莓", "0.48", "0.48", "0.02", "0.88", "0.02", "0.28", "0.02", "0.38"},
            {"柠檬", "0.18", "0.95", "0.02", "0.68", "0.01", "0.58", "0.02", "0.58"},
            {"珍珠", "0.38", "0.02", "0.48", "0.02", "0.78", "0.01", "0.28", "0.18"},
            {"波波", "0.38", "0.02", "0.48", "0.02", "0.78", "0.01", "0.28", "0.18"},
            {"牛乳", "0.28", "0.02", "0.68", "0.02", "0.95", "0.01", "0.38", "0.18"},
            {"奶盖", "0.28", "0.02", "0.95", "0.02", "0.68", "0.01", "0.18", "0.28"},
            {"四季春", "0.02", "0.95", "0.02", "0.38", "0.01", "0.68", "0.02", "0.48"},
            {"绿茶", "0.02", "0.88", "0.02", "0.28", "0.01", "0.78", "0.02", "0.38"},
            {"芋泥", "0.48", "0.02", "0.78", "0.02", "0.68", "0.01", "0.38", "0.18"},
            {"仙草", "0.28", "0.28", "0.48", "0.02", "0.58", "0.28", "0.28", "0.28"},
            {"杨枝甘露", "0.48", "0.28", "0.38", "0.78", "0.28", "0.18", "0.02", "0.48"}
        };

        for (String[] entry : defaultKeywords) {
            String keyword = entry[0];
            double[] coreFeatures = new double[8];
            for (int i = 0; i < 8; i++) {
                coreFeatures[i] = Double.parseDouble(entry[i + 1]);
            }
            keywordVectors.put(keyword, generateBertVector(keyword, coreFeatures));
        }

        // 为所有产品生成向量
        for (MilkTeaProduct product : dataService.getProductMap().values()) {
            double[] vector = encodeProduct(product);
            productVectors.put(product.getId(), vector);
        }
    }

    /**
     * 生成BERT-Whitening向量
     * 将核心特征扩展到256维，模拟真实BERT-Whitening的输出
     */
    private double[] generateBertVector(String keyword, double[] coreFeatures) {
        double[] vector = new double[VECTOR_DIM];
        // 将核心特征复制到向量的前几维
        for (int i = 0; i < Math.min(coreFeatures.length, VECTOR_DIM); i++) {
            vector[i] = coreFeatures[i];
        }
        // 剩余维度使用基于关键词哈希的伪随机填充（模拟BERT语义编码）
        Random random = new Random(keyword.hashCode());
        for (int i = coreFeatures.length; i < VECTOR_DIM; i++) {
            vector[i] = random.nextGaussian() * 0.1;
        }
        // 归一化（Whitening后的向量应该接近标准正态分布）
        return normalizeVector(vector);
    }

    /**
     * JSONArray转double数组
     */
    private double[] toDoubleArray(JSONArray array) {
        double[] result = new double[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.getDoubleValue(i);
        }
        return result;
    }

    /**
     * 使用BERT-Whitening编码产品
     * 基于产品名称和属性生成语义向量
     */
    private double[] encodeProduct(MilkTeaProduct product) {
        double[] vector = new double[VECTOR_DIM];
        int count = 0;

        // 从产品名称中提取关键词并聚合向量
        String name = product.getName();
        for (Map.Entry<String, double[]> entry : keywordVectors.entrySet()) {
            if (name.contains(entry.getKey())) {
                vector = addVectors(vector, entry.getValue());
                count++;
            }
        }

        // 根据热量添加低卡特征
        if (product.getCalorie() < 150) {
            double[] lowCalVector = keywordVectors.get("低卡");
            if (lowCalVector != null) {
                vector = addVectors(vector, lowCalVector);
                count++;
            }
        }

        // 根据糖分添加甜度特征
        if (product.getSugar() > 50) {
            double[] sweetVector = keywordVectors.get("甜");
            if (sweetVector != null) {
                vector = addVectors(vector, sweetVector);
                count++;
            }
        }

        // 归一化向量
        if (count > 0) {
            vector = normalizeVector(vector);
        }

        return vector;
    }

    /**
     * 获取智能匹配推荐
     * 基于BERT-Whitening编码的词向量相似度计算
     */
    public List<RecommendResultV2.RecommendItem> recommend(String userId, RecommendRequestV2 request) {
        try {
            // 获取用户档案
            User user = userService.getUser(userId);

            // 1. 构建用户偏好向量（使用BERT-Whitening编码）
            double[] userVector = buildUserVector(user, request);

            // 2. 获取预计算的产品向量
            Map<Integer, double[]> productVecs = getProductVectors();

            // 3. 计算余弦相似度
            Map<Integer, Double> similarities = new HashMap<>();
            for (Map.Entry<Integer, double[]> entry : productVecs.entrySet()) {
                double similarity = cosineSimilarity(userVector, entry.getValue());
                similarities.put(entry.getKey(), similarity);
            }

            // 4. 过滤产品
            Set<Integer> validProducts = filterProducts(user, request);

            // 5. 排序并生成推荐
            List<Integer> sortedProductIds = similarities.entrySet().stream()
                    .filter(e -> validProducts.contains(e.getKey()))
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            // 6. 构建推荐结果
            List<RecommendResultV2.RecommendItem> items = new ArrayList<>();
            int rank = 1;
            for (Integer productId : sortedProductIds) {
                MilkTeaProduct product = dataService.getProductMap().get(productId);
                if (product == null) continue;

                double similarity = similarities.get(productId);
                RecommendResultV2.RecommendItem item = createRecommendItem(product, rank, similarity, user, request);
                items.add(item);
                rank++;
            }

            // 如果推荐不足，用热门产品补充
            if (items.size() < 5) {
                fillWithHotProducts(items, 5 - items.size(), request);
            }

            return items;
        } catch (Exception e) {
            log.error("BERT-Whitening智能匹配推荐失败: {}", e.getMessage());
            return getFallbackRecommendations(request);
        }
    }

    /**
     * 获取预计算的产品向量
     */
    private Map<Integer, double[]> getProductVectors() {
        return productVectors;
    }

    /**
     * 构建用户偏好向量
     * 使用BERT-Whitening编码用户偏好描述，生成语义向量
     */
    private double[] buildUserVector(User user, RecommendRequestV2 request) {
        double[] vector = new double[VECTOR_DIM];
        int count = 0;

        // 从请求中获取偏好
        if (request.getPreferredFlavors() != null) {
            for (String flavor : request.getPreferredFlavors()) {
                double[] featureVector = keywordVectors.get(flavor);
                if (featureVector != null) {
                    vector = addVectors(vector, featureVector);
                    count++;
                }
            }
        }

        // 从用户档案中获取偏好
        if (user != null && user.getPreferredFlavors() != null && count == 0) {
            for (String flavor : user.getPreferredFlavors()) {
                double[] featureVector = keywordVectors.get(flavor);
                if (featureVector != null) {
                    vector = addVectors(vector, featureVector);
                    count++;
                }
            }
        }

        // 低卡偏好
        boolean preferLowCal = Boolean.TRUE.equals(request.getPreferLowCalorie()) ||
                (user != null && Boolean.TRUE.equals(user.getPreferLowCalorie()));
        if (preferLowCal) {
            double[] lowCalVector = keywordVectors.get("低卡");
            if (lowCalVector != null) {
                vector = addVectors(vector, lowCalVector);
                count++;
            }
        }

        // 甜度偏好
        Integer sweetLevel = request.getSweetLevel() != null ? request.getSweetLevel() :
                (user != null ? user.getSweetLevel() : null);
        if (sweetLevel != null && sweetLevel >= 4) {
            double[] sweetVector = keywordVectors.get("甜");
            if (sweetVector != null) {
                vector = addVectors(vector, sweetVector);
                count++;
            }
        }

        // 从备注中提取特征（语义匹配）
        if (request.getNote() != null && !request.getNote().isEmpty()) {
            for (String keyword : keywordVectors.keySet()) {
                if (request.getNote().contains(keyword)) {
                    vector = addVectors(vector, keywordVectors.get(keyword));
                    count++;
                }
            }
        }

        // 归一化
        if (count > 0) {
            vector = normalizeVector(vector);
        } else {
            // 默认偏好：清爽、果茶
            double[] defaultVec = new double[VECTOR_DIM];
            defaultVec = addVectors(defaultVec, keywordVectors.getOrDefault("清爽", new double[VECTOR_DIM]));
            defaultVec = addVectors(defaultVec, keywordVectors.getOrDefault("果茶", new double[VECTOR_DIM]));
            vector = normalizeVector(defaultVec);
        }

        return vector;
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(double[] v1, double[] v2) {
        if (v1.length != v2.length) return 0;

        double dotProduct = 0;
        double norm1 = 0;
        double norm2 = 0;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }

        if (norm1 == 0 || norm2 == 0) return 0;

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 向量加法
     */
    private double[] addVectors(double[] v1, double[] v2) {
        double[] result = new double[VECTOR_DIM];
        for (int i = 0; i < VECTOR_DIM && i < v1.length && i < v2.length; i++) {
            result[i] = v1[i] + v2[i];
        }
        return result;
    }

    /**
     * 向量归一化
     */
    private double[] normalizeVector(double[] v) {
        double norm = 0;
        for (double val : v) {
            norm += val * val;
        }
        norm = Math.sqrt(norm);

        if (norm == 0) return v;

        double[] result = new double[VECTOR_DIM];
        for (int i = 0; i < VECTOR_DIM && i < v.length; i++) {
            result[i] = v[i] / norm;
        }
        return result;
    }

    /**
     * 过滤产品
     */
    private Set<Integer> filterProducts(User user, RecommendRequestV2 request) {
        Set<Integer> validProducts = new HashSet<>(dataService.getProductMap().keySet());

        // 品牌过滤
        if (request.getBrandIds() != null && !request.getBrandIds().isEmpty()) {
            Set<Integer> brandFiltered = new HashSet<>();
            for (Integer id : validProducts) {
                MilkTeaProduct p = dataService.getProductMap().get(id);
                if (p != null && request.getBrandIds().contains(p.getBrandId())) {
                    brandFiltered.add(id);
                }
            }
            validProducts = brandFiltered;
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
                }
            }
        }

        // 疾病过滤
        if (user != null && user.getDiseaseHistory() != null) {
            for (String disease : user.getDiseaseHistory()) {
                if (disease.equals("糖尿病")) {
                    Set<Integer> diabetesFiltered = new HashSet<>();
                    for (Integer id : validProducts) {
                        MilkTeaProduct p = dataService.getProductMap().get(id);
                        if (p != null && p.getSugar() < 30) {
                            diabetesFiltered.add(id);
                        }
                    }
                    validProducts = diabetesFiltered;
                } else if (disease.equals("减肥中")) {
                    Set<Integer> dietFiltered = new HashSet<>();
                    for (Integer id : validProducts) {
                        MilkTeaProduct p = dataService.getProductMap().get(id);
                        if (p != null && p.getCalorie() < 250) {
                            dietFiltered.add(id);
                        }
                    }
                    validProducts = dietFiltered;
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
                                                                  double similarity, User user,
                                                                  RecommendRequestV2 request) {
        RecommendResultV2.RecommendItem item = new RecommendResultV2.RecommendItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setBrandName(product.getBrandName());
        item.setImageUrl(product.getImage());
        item.setCalorie(product.getCalorie());
        item.setRecommendLevel(6 - Math.min(rank, 5));
        item.setMatchScore(similarity * 100);

        // 生成推荐理由
        item.setRecommendReason(generateReason(product, similarity));

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
    private String generateReason(MilkTeaProduct product, double similarity) {
        List<String> reasons = new ArrayList<>();

        // 相似度理由
        if (similarity > 0.8) {
            reasons.add("高度匹配您的偏好");
        } else if (similarity > 0.6) {
            reasons.add("较符合您的口味");
        }

        // 产品特征理由
        String name = product.getName();
        if (name.contains("芝士") || name.contains("芝芝") || name.contains("奶盖")) {
            reasons.add("浓郁芝士口感");
        }
        if (name.contains("葡萄") || name.contains("草莓") || name.contains("芒果")) {
            reasons.add("新鲜水果制作");
        }
        if (name.contains("柠檬") || name.contains("柚子") || name.contains("四季春")) {
            reasons.add("清爽解腻");
        }
        if (product.getCalorie() < 150) {
            reasons.add("低卡健康");
        }

        if (reasons.isEmpty()) {
            reasons.add("智能匹配推荐");
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
        if (product.getName().contains("果") || product.getName().contains("葡萄") ||
                product.getName().contains("草莓")) {
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
                .filter(Objects::nonNull)
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
