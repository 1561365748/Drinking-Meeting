package com.milktea.service;

import com.milktea.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 热量计算服务
 */
@Service
@RequiredArgsConstructor
public class CalorieService {

    private final DataService dataService;

    /**
     * 计算奶茶热量
     */
    public CalorieResult calculateCalorie(CalorieRequest request) {
        CalorieResult result = new CalorieResult();

        // 获取产品信息
        MilkTeaProduct product = dataService.getProductMap().get(request.getProductId());
        if (product == null) {
            throw new RuntimeException("产品不存在");
        }

        MilkTeaBrand brand = dataService.getBrandMap().get(request.getBrandId());
        if (brand == null) {
            throw new RuntimeException("品牌不存在");
        }

        // 获取容量倍数
        double sizeMultiplier = dataService.getSizeMultiplier()
                .getOrDefault(request.getSize(), 1.0);

        // 计算基础热量
        int baseCalorie = (int) (product.getCalorie() * sizeMultiplier);
        int baseSugar = (int) (product.getSugar() * sizeMultiplier);
        int baseCarbs = (int) (product.getCarbs() * sizeMultiplier);

        // 累加小料热量
        List<CalorieResult.ToppingDetail> toppingDetails = new ArrayList<>();
        int toppingCalorie = 0;
        int toppingSugar = 0;
        int toppingCarbs = 0;

        if (request.getToppingIds() != null) {
            for (Integer toppingId : request.getToppingIds()) {
                Topping topping = dataService.getToppingMap().get(toppingId);
                if (topping != null) {
                    toppingCalorie += topping.getCalorie();
                    toppingSugar += topping.getSugar();
                    toppingCarbs += topping.getCarbs();

                    CalorieResult.ToppingDetail detail = new CalorieResult.ToppingDetail();
                    detail.setName(topping.getName());
                    detail.setCalorie(topping.getCalorie());
                    toppingDetails.add(detail);
                }
            }
        }

        // 计算总热量
        int totalCalorie = baseCalorie + toppingCalorie;
        int totalSugar = baseSugar + toppingSugar;
        int totalCarbs = baseCarbs + toppingCarbs;

        // 设置结果
        result.setProductName(product.getName());
        result.setBrandName(brand.getName());
        result.setSize(request.getSize());
        result.setBaseCalorie(baseCalorie);
        result.setToppingCalorie(toppingCalorie);
        result.setTotalCalorie(totalCalorie);
        result.setTotalSugar(totalSugar);
        result.setTotalCarbs(totalCarbs);
        result.setToppings(toppingDetails);

        // 判断热量等级
        result.setCalorieLevel(getCalorieLevel(totalCalorie));

        // 生成健康建议
        result.setHealthAdvice(generateHealthAdvice(totalCalorie, totalSugar, totalCarbs));

        // 生成运动建议
        result.setExerciseAdvices(generateExerciseAdvice(totalCalorie));

        return result;
    }

    /**
     * 判断热量等级
     */
    private String getCalorieLevel(int calorie) {
        if (calorie < 150) {
            return "低";
        } else if (calorie < 300) {
            return "中";
        } else if (calorie < 450) {
            return "高";
        } else {
            return "超高";
        }
    }

    /**
     * 生成健康建议
     */
    private String generateHealthAdvice(int calorie, int sugar, int carbs) {
        StringBuilder advice = new StringBuilder();

        // 热量建议
        if (calorie > 400) {
            advice.append("⚠️ 这杯奶茶热量较高，相当于约").append(calorie / 80).append("碗米饭的热量。");
            advice.append("建议分两次饮用或选择小杯。");
        } else if (calorie > 250) {
            advice.append("💡 这杯奶茶热量适中，建议搭配运动消耗。");
        } else {
            advice.append("✅ 这杯奶茶热量较低，是相对健康的选择。");
        }

        advice.append(" ");

        // 糖分建议
        if (sugar > 50) {
            advice.append("糖分含量较高，建议减少其他甜食摄入。");
        } else if (sugar > 30) {
            advice.append("糖分适中，符合日常推荐摄入量。");
        }

        advice.append(" ");

        // 碳水建议
        if (carbs > 60) {
            advice.append("碳水化合物偏高，健身减脂期间慎选。");
        }

        return advice.toString().trim();
    }

    /**
     * 生成运动建议
     */
    private List<CalorieResult.ExerciseAdvice> generateExerciseAdvice(int calorie) {
        List<CalorieResult.ExerciseAdvice> advices = new ArrayList<>();
        List<Exercise> exercises = new ArrayList<>(dataService.getExerciseMap().values());

        // 按强度分类选择运动
        Collections.shuffle(exercises);

        // 选择3种不同强度的运动
        List<Exercise> selected = exercises.stream()
                .limit(4)
                .collect(Collectors.toList());

        for (Exercise exercise : selected) {
            CalorieResult.ExerciseAdvice advice = new CalorieResult.ExerciseAdvice();
            advice.setExerciseName(exercise.getName());
            advice.setImage(exercise.getImage());
            advice.setDescription(exercise.getDescription());

            // 计算需要运动的时长
            double duration = (double) calorie / exercise.getCaloriePerHour();
            advice.setDuration(duration);
            advice.setDurationText(formatDuration(duration));

            advices.add(advice);
        }

        return advices;
    }

    /**
     * 格式化时长显示
     */
    private String formatDuration(double hours) {
        int totalMinutes = (int) (hours * 60);
        if (totalMinutes < 60) {
            return totalMinutes + "分钟";
        } else {
            int h = totalMinutes / 60;
            int m = totalMinutes % 60;
            if (m == 0) {
                return h + "小时";
            }
            return h + "小时" + m + "分钟";
        }
    }

    /**
     * 获取所有品牌
     */
    public List<MilkTeaBrand> getAllBrands() {
        return new ArrayList<>(dataService.getBrandMap().values());
    }

    /**
     * 获取品牌下的所有产品
     */
    public List<MilkTeaProduct> getProductsByBrand(Integer brandId) {
        MilkTeaBrand brand = dataService.getBrandMap().get(brandId);
        return brand != null ? brand.getProducts() : new ArrayList<>();
    }

    /**
     * 获取所有小料
     */
    public List<Topping> getAllToppings() {
        return new ArrayList<>(dataService.getToppingMap().values());
    }
}
