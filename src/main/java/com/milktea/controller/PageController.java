package com.milktea.controller;

import com.milktea.entity.*;
import com.milktea.service.CalorieService;
import com.milktea.service.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 页面控制器
 */
@Controller
@RequiredArgsConstructor
public class PageController {

    private final CalorieService calorieService;
    private final DataService dataService;

    /**
     * 旧首页 - 重定向到登录页
     */
    @GetMapping("/")
    public String index(Model model) {
        return "redirect:/login";
    }

    /**
     * 登录页面
     */
    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("pageTitle", "登录 - 奶茶君");
        return "login";
    }

    /**
     * 登录后的首页
     */
    @GetMapping("/home")
    public String homePage(Model model) {
        model.addAttribute("pageTitle", "首页 - 奶茶君");
        return "home";
    }

    /**
     * 完善信息页面（新用户注册）
     */
    @GetMapping("/profile/setup")
    public String profileSetupPage(Model model) {
        model.addAttribute("pageTitle", "完善信息 - 奶茶君");
        return "profile_setup";
    }

    /**
     * 热量计算页面
     */
    @GetMapping("/calorie")
    public String caloriePage(Model model) {
        model.addAttribute("pageTitle", "热量计算 - 奶茶君");
        model.addAttribute("brands", calorieService.getAllBrands());
        model.addAttribute("toppings", calorieService.getAllToppings());
        return "calorie";
    }

    /**
     * 推荐页面
     */
    @GetMapping("/recommend")
    public String recommendPage(Model model) {
        model.addAttribute("pageTitle", "智能推荐 - 奶茶君");
        model.addAttribute("brands", dataService.getBrandMap().values());
        return "recommend";
    }

    /**
     * 推荐结果页面
     */
    @GetMapping("/recommend/result")
    public String recommendResultPage(Model model) {
        model.addAttribute("pageTitle", "推荐结果 - 奶茶君");
        return "recommend_result";
    }

    /**
     * 个人中心页面
     */
    @GetMapping("/profile")
    public String profilePage(Model model) {
        model.addAttribute("pageTitle", "个人中心 - 奶茶君");
        return "profile";
    }

    /**
     * 历史记录页面
     */
    @GetMapping("/history")
    public String historyPage(Model model) {
        model.addAttribute("pageTitle", "历史记录 - 奶茶君");
        return "history";
    }

    /**
     * 关于页面
     */
    @GetMapping("/about")
    public String aboutPage(Model model) {
        model.addAttribute("pageTitle", "关于我们 - 奶茶君");
        return "about";
    }
}
