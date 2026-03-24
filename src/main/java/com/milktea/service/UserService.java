package com.milktea.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.milktea.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 用户服务
 * 负责用户数据的加载、保存和管理
 */
@Slf4j
@Service
public class UserService {

    // 用户数据存储路径
    private static final String USER_DATA_FILE = "data/user_data.json";

    // 用户数据缓存
    @Getter
    private Map<String, User> userMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadUserData();
    }

    /**
     * 加载用户数据
     */
    private void loadUserData() {
        try {
            Path path = Paths.get(USER_DATA_FILE);
            if (Files.exists(path)) {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                JSONObject obj = JSON.parseObject(json);

                if (obj.containsKey("users")) {
                    @SuppressWarnings("unchecked")
                    List<Map> users = obj.getList("users", Map.class);
                    for (Map userMap : users) {
                        User user = parseUser(userMap);
                        this.userMap.put(user.getUserId(), user);
                    }
                }
                log.info("加载用户数据完成，共 {} 个用户", userMap.size());
            } else {
                // 创建默认数据文件
                saveUserData();
                log.info("创建用户数据文件");
            }
        } catch (Exception e) {
            log.error("加载用户数据失败: {}", e.getMessage());
        }
    }

    /**
     * 解析用户对象
     */
    @SuppressWarnings("unchecked")
    private User parseUser(Map<String, Object> map) {
        User user = new User();
        user.setUserId((String) map.get("userId"));

        // 解析疾病史
        List<String> diseaseHistory = (List<String>) map.get("diseaseHistory");
        if (diseaseHistory != null) {
            user.setDiseaseHistory(new ArrayList<>(diseaseHistory));
        }

        // 解析忌口
        List<String> allergies = (List<String>) map.get("allergies");
        if (allergies != null) {
            user.setAllergies(new ArrayList<>(allergies));
        }

        // 解析口味偏好
        List<String> preferredFlavors = (List<String>) map.get("preferredFlavors");
        if (preferredFlavors != null) {
            user.setPreferredFlavors(new ArrayList<>(preferredFlavors));
        }

        // 解析甜度偏好
        if (map.get("sweetLevel") != null) {
            user.setSweetLevel(((Number) map.get("sweetLevel")).intValue());
        }

        // 解析低卡偏好
        if (map.get("preferLowCalorie") != null) {
            user.setPreferLowCalorie((Boolean) map.get("preferLowCalorie"));
        }

        // 解析历史选择
        List<Map<String, Object>> historySelections = (List<Map<String, Object>>) map.get("historySelections");
        if (historySelections != null) {
            List<User.UserSelection> selections = new ArrayList<>();
            for (var selMap : historySelections) {
                User.UserSelection selection = new User.UserSelection();
                selection.setProductId((Integer) selMap.get("productId"));
                selection.setProductName((String) selMap.get("productName"));
                selection.setBrandName((String) selMap.get("brandName"));

                List<Integer> toppingIds = (List<Integer>) selMap.get("toppingIds");
                if (toppingIds != null) {
                    selection.setToppingIds(new ArrayList<>(toppingIds));
                }

                List<String> toppingNames = (List<String>) selMap.get("toppingNames");
                if (toppingNames != null) {
                    selection.setToppingNames(new ArrayList<>(toppingNames));
                }

                selection.setSugarLevel((String) selMap.get("sugarLevel"));
                selection.setTemperature((String) selMap.get("temperature"));

                if (selMap.get("likeRating") != null) {
                    selection.setLikeRating(((Number) selMap.get("likeRating")).intValue());
                }

                selection.setFeedback((String) selMap.get("feedback"));
                selection.setRecommendSource((String) selMap.get("recommendSource"));

                // 解析时间
                String selectionTime = (String) selMap.get("selectionTime");
                if (selectionTime != null) {
                    try {
                        selection.setSelectionTime(LocalDateTime.parse(selectionTime));
                    } catch (Exception ignored) {}
                }

                selections.add(selection);
            }
            user.setHistorySelections(selections);
        }

        // 解析时间
        String registerTime = (String) map.get("registerTime");
        if (registerTime != null) {
            try {
                user.setRegisterTime(LocalDateTime.parse(registerTime));
            } catch (Exception ignored) {}
        }

        String lastLoginTime = (String) map.get("lastLoginTime");
        if (lastLoginTime != null) {
            try {
                user.setLastLoginTime(LocalDateTime.parse(lastLoginTime));
            } catch (Exception ignored) {}
        }

        return user;
    }

    /**
     * 保存用户数据
     */
    public synchronized void saveUserData() {
        try {
            Path path = Paths.get(USER_DATA_FILE);

            // 确保目录存在
            if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }

            // 构建JSON
            JSONObject root = new JSONObject();
            root.put("users", new ArrayList<>(userMap.values()));

            // 写入文件
            String json = JSON.toJSONString(root, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
            Files.writeString(path, json, StandardCharsets.UTF_8);

            log.debug("用户数据保存成功");
        } catch (IOException e) {
            log.error("保存用户数据失败: {}", e.getMessage());
        }
    }

    /**
     * 用户登录/注册
     * @param userId 数字用户ID
     * @return 用户对象（已存在则返回，不存在则创建新用户）
     */
    public User loginOrRegister(String userId) {
        // 验证用户ID是否为纯数字
        if (!isValidUserId(userId)) {
            return null;
        }

        User user = userMap.get(userId);
        if (user != null) {
            // 已存在，更新登录时间
            user.setLastLoginTime(LocalDateTime.now());
            saveUserData();
            return user;
        }

        // 不存在，创建新用户
        User newUser = new User();
        newUser.setUserId(userId);
        newUser.setRegisterTime(LocalDateTime.now());
        newUser.setLastLoginTime(LocalDateTime.now());
        userMap.put(userId, newUser);
        saveUserData();

        return newUser;
    }

    /**
     * 验证用户ID格式（纯数字）
     */
    public boolean isValidUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        return userId.matches("\\d+");
    }

    /**
     * 检查用户是否存在
     */
    public boolean userExists(String userId) {
        return userMap.containsKey(userId);
    }

    /**
     * 获取用户
     */
    public User getUser(String userId) {
        return userMap.get(userId);
    }

    /**
     * 更新用户档案
     */
    public void updateUserProfile(String userId, List<String> diseaseHistory,
                                   List<String> allergies, List<String> preferredFlavors,
                                   Integer sweetLevel, Boolean preferLowCalorie) {
        User user = userMap.get(userId);
        if (user != null) {
            if (diseaseHistory != null) {
                user.setDiseaseHistory(diseaseHistory);
            }
            if (allergies != null) {
                user.setAllergies(allergies);
            }
            if (preferredFlavors != null) {
                user.setPreferredFlavors(preferredFlavors);
            }
            if (sweetLevel != null) {
                user.setSweetLevel(sweetLevel);
            }
            if (preferLowCalorie != null) {
                user.setPreferLowCalorie(preferLowCalorie);
            }
            saveUserData();
        }
    }

    /**
     * 添加用户选择记录
     */
    public void addUserSelection(String userId, User.UserSelection selection) {
        User user = userMap.get(userId);
        if (user != null) {
            if (selection.getSelectionTime() == null) {
                selection.setSelectionTime(LocalDateTime.now());
            }
            user.getHistorySelections().add(selection);

            // 根据反馈更新用户偏好
            updatePreferenceFromFeedback(user, selection);

            saveUserData();
        }
    }

    /**
     * 根据反馈更新用户偏好
     */
    private void updatePreferenceFromFeedback(User user, User.UserSelection selection) {
        // 如果喜欢程度>=4，将该产品的特征添加到用户偏好
        if (selection.getLikeRating() != null && selection.getLikeRating() >= 4) {
            String productName = selection.getProductName();

            // 根据产品名称推断口味偏好
            if (productName != null) {
                List<String> flavors = user.getPreferredFlavors();

                if (productName.contains("芝士") || productName.contains("芝芝") || productName.contains("浓郁")) {
                    if (!flavors.contains("浓郁")) {
                        flavors.add("浓郁");
                    }
                }
                if (productName.contains("葡萄") || productName.contains("草莓") || productName.contains("芒果") ||
                    productName.contains("果") || productName.contains("柠") || productName.contains("柚")) {
                    if (!flavors.contains("果茶")) {
                        flavors.add("果茶");
                    }
                }
                if (productName.contains("奶茶") || productName.contains("牛乳") || productName.contains("拿铁")) {
                    if (!flavors.contains("奶茶")) {
                        flavors.add("奶茶");
                    }
                }
                if (productName.contains("柠") || productName.contains("柚") || productName.contains("四季春") ||
                    productName.contains("绿")) {
                    if (!flavors.contains("清爽")) {
                        flavors.add("清爽");
                    }
                }
            }
        }
    }

    /**
     * 获取用户历史选择的产品ID列表
     */
    public List<Integer> getUserHistoryProductIds(String userId) {
        User user = userMap.get(userId);
        if (user == null || user.getHistorySelections() == null) {
            return new ArrayList<>();
        }

        return user.getHistorySelections().stream()
                .map(User.UserSelection::getProductId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取用户高评分的产品ID列表
     */
    public List<Integer> getUserLikedProductIds(String userId) {
        User user = userMap.get(userId);
        if (user == null || user.getHistorySelections() == null) {
            return new ArrayList<>();
        }

        return user.getHistorySelections().stream()
                .filter(s -> s.getLikeRating() != null && s.getLikeRating() >= 4)
                .map(User.UserSelection::getProductId)
                .distinct()
                .collect(Collectors.toList());
    }
}
