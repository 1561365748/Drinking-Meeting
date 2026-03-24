# Spring Boot 框架详解 - 以奶茶君项目为例

## 一、Spring Boot 核心原理

### 1.1 什么是 Spring Boot？

Spring Boot 是一个简化 Spring 应用开发的框架，它通过**约定优于配置**的理念，让你无需大量 XML 配置就能快速搭建应用。

```
传统 Spring 开发：
  需要配置大量 XML 文件 → 配置复杂 → 开发效率低

Spring Boot 开发：
  约定优于配置 → 自动装配 → 开箱即用
```

### 1.2 核心注解解析

```java
// MilkteaApplication.java - 启动类
@SpringBootApplication  // 这是一个组合注解，包含三个关键注解
public class MilkteaApplication {
    public static void main(String[] args) {
        SpringApplication.run(MilkteaApplication.class, args);
    }
}
```

**`@SpringBootApplication` 等价于以下三个注解的组合：**

| 注解 | 作用 |
|------|------|
| `@SpringBootConfiguration` | 标识这是一个配置类 |
| `@EnableAutoConfiguration` | **核心！** 启用自动装配，根据依赖自动配置 Bean |
| `@ComponentScan` | 自动扫描当前包及子包下的 `@Component`、`@Service`、`@Controller` 等 |

### 1.3 启动流程

```
用户执行 main()
    ↓
SpringApplication.run() 启动
    ↓
① 创建 ApplicationContext（应用上下文，即 Spring 容器）
    ↓
② 扫描 @Component 注解的类（Controller、Service、Repository 等）
    ↓
③ 自动装配：根据依赖自动配置 Bean
    ↓
④ 依赖注入：将 Bean 注入到需要的地方
    ↓
⑤ 启动内嵌 Tomcat 服务器
    ↓
⑥ 监听 8080 端口，等待请求
```

---

## 二、项目分层架构（MVC 模式）

### 2.1 目录结构

```
src/main/java/com/milktea/
├── MilkteaApplication.java     # 启动类（入口）
├── controller/                  # 控制器层 - 接收请求
│   ├── PageController.java     # 页面路由
│   └── ApiController.java      # API 接口
├── service/                     # 服务层 - 业务逻辑
│   ├── UserService.java        # 用户业务
│   ├── CalorieService.java     # 热量计算业务
│   └── DeepseekRecommendService.java  # AI 推荐业务
├── entity/                      # 实体层 - 数据模型
│   ├── User.java               # 用户实体
│   ├── MilkTeaProduct.java     # 产品实体
│   └── Topping.java            # 小料实体
├── algorithm/                   # 算法层 - 推荐算法
│   └── ItemCFRecommender.java  # 协同过滤算法
├── config/                      # 配置层
│   └── WebConfig.java          # Web 配置
└── util/                        # 工具类
    └── ImagePreprocessor.java  # 图片处理
```

### 2.2 三层架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        用户请求                              │
│                    http://localhost:8080/login              │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    Controller 层                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ @Controller / @RestController                       │   │
│  │ - 接收 HTTP 请求                                     │   │
│  │ - 解析请求参数                                       │   │
│  │ - 调用 Service 层处理业务                            │   │
│  │ - 返回响应（页面或 JSON）                            │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     Service 层                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ @Service                                             │   │
│  │ - 处理业务逻辑                                       │   │
│  │ - 调用数据访问层或算法层                             │   │
│  │ - 返回处理结果                                       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     Entity 层                                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ @Data (Lombok)                                       │   │
│  │ - 数据模型定义                                       │   │
│  │ - 属性和 getter/setter                               │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 三、请求处理流程（以登录为例）

### 3.1 完整调用链路

```
用户在浏览器输入账号，点击登录
    ↓
【前端】login.html 发送 POST 请求
    fetch('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ userId: '123456' })
    })
    ↓
【网络】HTTP 请求到达服务器
    POST /api/auth/login
    Body: { "userId": "123456" }
    ↓
【Spring Boot】DispatcherServlet 分发请求
    ↓
【Controller】ApiController.login() 接收请求
    @PostMapping("/api/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");  // 获取参数
        boolean isNewUser = !userService.userExists(userId);  // 调用 Service
        User user = userService.loginOrRegister(userId);      // 调用 Service
        // ...
    }
    ↓
【Service】UserService 处理业务逻辑
    @Service
    public class UserService {
        public User loginOrRegister(String userId) {
            User user = userMap.get(userId);  // 查询用户
            if (user != null) {
                return user;  // 已存在，直接返回
            }
            // 不存在，创建新用户
            User newUser = new User();
            newUser.setUserId(userId);
            userMap.put(userId, newUser);
            saveUserData();  // 保存到 JSON 文件
            return newUser;
        }
    }
    ↓
【Entity】User 实体存储数据
    @Data
    public class User {
        private String userId;
        private List<String> preferredFlavors;
        // ...
    }
    ↓
【Controller】返回 JSON 响应
    return ResponseEntity.ok(response);
    // { "success": true, "isNewUser": true, "userName": "123456" }
    ↓
【前端】接收响应，跳转页面
    if (data.success) {
        window.location.href = '/home';
    }
```

### 3.2 代码对应关系

```
┌────────────────────────────────────────────────────────────────┐
│  文件位置                          │  代码                     │
├────────────────────────────────────────────────────────────────┤
│  ApiController.java:36           │  @PostMapping("/auth/login")│
│                                  │  public ResponseEntity login()│
│                    ──────────────┼─────────────────────────────│
│                                  │  userService.userExists()    │
│                                  │         ↓                    │
│  UserService.java:238            │  public boolean userExists() │
│                    ──────────────┼─────────────────────────────│
│                                  │  userService.loginOrRegister()│
│                                  │         ↓                    │
│  UserService.java:200            │  public User loginOrRegister()│
│                    ──────────────┼─────────────────────────────│
│                                  │  userMap.get(userId)         │
│                                  │         ↓                    │
│  User.java:12                    │  @Data public class User     │
└────────────────────────────────────────────────────────────────┘
```

---

## 四、依赖注入（Dependency Injection）

### 4.1 什么是依赖注入？

**问题场景：** Controller 需要调用 Service，传统方式需要手动创建对象：

```java
// ❌ 传统方式 - 手动创建对象
public class ApiController {
    private UserService userService = new UserService();  // 紧耦合
    private CalorieService calorieService = new CalorieService();
    // ... 每次都要 new，而且 Service 内部可能还有其他依赖
}
```

**Spring 方式 - 依赖注入：**

```java
// ✅ Spring 方式 - 依赖注入
@RestController
@RequiredArgsConstructor  // Lombok 注解，自动生成构造函数
public class ApiController {

    private final UserService userService;        // 只声明，不创建
    private final CalorieService calorieService;  // Spring 自动注入

    // Spring 容器会自动创建 UserService 实例并注入到这里
}
```

### 4.2 依赖注入原理

```
Spring 容器启动
    ↓
扫描所有 @Service、@Controller、@Repository 注解的类
    ↓
创建这些类的实例（Bean），放入容器中管理
    ↓
当发现 Controller 需要 UserService 时
    ↓
从容器中取出 UserService 实例
    ↓
通过构造函数注入到 Controller 中
```

### 4.3 本项目中的依赖注入

```java
// ApiController.java
@RestController                    // 1. 标记为控制器，Spring 会创建实例
@RequestMapping("/api")
@RequiredArgsConstructor            // 2. Lombok 生成构造函数
public class ApiController {

    private final CalorieService calorieService;      // 3. 声明依赖
    private final DataService dataService;
    private final UserService userService;
    private final DeepseekRecommendService deepseekRecommendService;
    // ... 共 7 个 Service 依赖

    // Lombok 生成的构造函数等价于：
    // public ApiController(CalorieService calorieService,
    //                      DataService dataService, ...) {
    //     this.calorieService = calorieService;
    //     this.dataService = dataService;
    //     ...
    // }
}

// UserService.java
@Service                            // 4. 标记为服务，Spring 会创建实例
public class UserService {
    // ...
}
```

### 4.4 常用注入方式对比

| 方式 | 代码 | 特点 |
|------|------|------|
| 构造函数注入（推荐） | `@RequiredArgsConstructor` + `private final XxxService` | 不可变，易测试 |
| Setter 注入 | `@Autowired` + `public void setXxx()` | 可选依赖 |
| 字段注入 | `@Autowired private XxxService` | 不推荐，难测试 |

---

## 五、Controller 详解

### 5.1 两种 Controller 类型

```java
// 1. @Controller - 返回页面（HTML）
@Controller
public class PageController {

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("pageTitle", "登录 - 奶茶君");
        return "login";  // 返回 login.html 模板
    }
}

// 2. @RestController - 返回 JSON 数据
@RestController
@RequestMapping("/api")
public class ApiController {

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        // ...
        return ResponseEntity.ok(response);  // 返回 JSON
    }
}
```

### 5.2 常用注解

| 注解 | 作用 | 示例 |
|------|------|------|
| `@GetMapping` | 处理 GET 请求 | `@GetMapping("/home")` |
| `@PostMapping` | 处理 POST 请求 | `@PostMapping("/login")` |
| `@RequestMapping` | 通用映射 | `@RequestMapping("/api")` |
| `@PathVariable` | 获取 URL 路径参数 | `@PathVariable String userId` |
| `@RequestParam` | 获取查询参数 | `@RequestParam String keyword` |
| `@RequestBody` | 获取请求体 JSON | `@RequestBody Map<String, String> body` |

### 5.3 实际例子

```java
// 路径参数
@GetMapping("/user/{userId}")
public ResponseEntity getUser(@PathVariable String userId) {
    // URL: /api/user/123456
    // userId = "123456"
}

// 查询参数
@GetMapping("/search")
public ResponseEntity search(@RequestParam String keyword) {
    // URL: /api/search?keyword=奶茶
    // keyword = "奶茶"
}

// 请求体
@PostMapping("/calorie/calculate")
public ResponseEntity calculate(@RequestBody CalorieRequest request) {
    // Body: {"brandId":1,"productId":101,"size":"中杯"}
    // 自动映射到 CalorieRequest 对象
}
```

---

## 六、Service 层详解

### 6.1 Service 的职责

```java
@Service
public class UserService {

    // 1. 数据存储（本项目用 Map，通常用数据库）
    private Map<String, User> userMap = new ConcurrentHashMap<>();

    // 2. 初始化方法 - 启动时执行
    @PostConstruct
    public void init() {
        loadUserData();  // 从 JSON 文件加载用户数据
    }

    // 3. 业务方法
    public User loginOrRegister(String userId) {
        User user = userMap.get(userId);
        if (user != null) {
            user.setLastLoginTime(LocalDateTime.now());
            return user;
        }
        // 创建新用户...
    }

    // 4. 数据持久化
    public synchronized void saveUserData() {
        // 保存到 JSON 文件
    }
}
```

### 6.2 生命周期注解

| 注解 | 执行时机 | 用途 |
|------|---------|------|
| `@PostConstruct` | Bean 创建后 | 初始化数据 |
| `@PreDestroy` | Bean 销毁前 | 释放资源 |

---

## 七、Entity 层详解

### 7.1 实体类定义

```java
@Data  // Lombok 自动生成 getter/setter/toString/equals/hashCode
public class User {
    private String userId;
    private List<String> diseaseHistory = new ArrayList<>();
    private List<String> allergies = new ArrayList<>();
    private List<String> preferredFlavors = new ArrayList<>();
    private Integer sweetLevel = 3;
    private Boolean preferLowCalorie = false;
    private List<UserSelection> historySelections = new ArrayList<>();
    private LocalDateTime registerTime;
    private LocalDateTime lastLoginTime;

    // 内部类
    @Data
    public static class UserSelection {
        private Integer productId;
        private String productName;
        private String brandName;
        private Integer likeRating;
        // ...
    }
}
```

### 7.2 Lombok 常用注解

| 注解 | 作用 |
|------|------|
| `@Data` | 生成 getter/setter/toString/equals/hashCode |
| `@Getter/@Setter` | 只生成 getter 或 setter |
| `@NoArgsConstructor` | 无参构造函数 |
| `@AllArgsConstructor` | 全参构造函数 |
| `@RequiredArgsConstructor` | final 字段构造函数 |
| `@Slf4j` | 自动生成 log 日志对象 |

---

## 八、模板引擎（Thymeleaf）

### 8.1 工作原理

```
Controller 返回 "login"
    ↓
Thymeleaf 模板引擎
    ↓
查找 templates/login.html
    ↓
解析模板语法（${pageTitle}）
    ↓
生成完整 HTML
    ↓
返回给浏览器
```

### 8.2 Controller 与模板的交互

```java
// PageController.java
@GetMapping("/calorie")
public String caloriePage(Model model) {
    model.addAttribute("pageTitle", "热量计算 - 奶茶君");  // 传递数据
    model.addAttribute("brands", calorieService.getAllBrands());
    model.addAttribute("toppings", calorieService.getAllToppings());
    return "calorie";  // 返回模板名
}
```

```html
<!-- templates/calorie.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title th:text="${pageTitle}">标题</title>
</head>
<body>
    <!-- 遍历品牌 -->
    <select id="brandSelect">
        <option th:each="brand : ${brands}"
                th:value="${brand.id}"
                th:text="${brand.name}">
        </option>
    </select>
</body>
</html>
```

---

## 九、静态资源加载

### 9.1 静态资源目录

```
src/main/resources/
├── static/              # 静态资源
│   ├── css/style.css   # CSS 文件
│   └── js/recommend.js # JavaScript 文件
└── templates/           # 模板文件
    ├── login.html
    └── home.html
```

### 9.2 访问规则

| 资源位置 | 访问 URL |
|---------|---------|
| `static/css/style.css` | `/css/style.css` |
| `static/js/recommend.js` | `/js/recommend.js` |
| `templates/login.html` | 通过 Controller 返回 |

---

## 十、完整请求流程图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           用户请求流程                                   │
└─────────────────────────────────────────────────────────────────────────┘

用户访问 http://localhost:8080/calorie
    │
    ▼
┌─────────────────┐
│ Tomcat 服务器   │  监听 8080 端口
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ DispatcherServlet│  Spring 核心分发器
└────────┬────────┘
         │
         ▼ 查找 @GetMapping("/calorie")
         │
┌─────────────────┐
│ PageController  │  @Controller
│ caloriePage()   │
└────────┬────────┘
         │
         ├──────────────────────┐
         │                      │
         ▼                      ▼
┌─────────────────┐    ┌─────────────────┐
│ CalorieService  │    │ DataService     │  @Service
│ getAllBrands()  │    │ getBrandMap()   │
└────────┬────────┘    └────────┬────────┘
         │                      │
         └──────────┬───────────┘
                    │
                    ▼
         ┌─────────────────┐
         │ MilkTeaBrand    │  @Data Entity
         │ MilkTeaProduct  │
         └────────┬────────┘
                  │
                  ▼
         ┌─────────────────┐
         │ Model           │  数据放入 Model
         │ - brands        │
         │ - toppings      │
         └────────┬────────┘
                  │
                  ▼ return "calorie"
         ┌─────────────────┐
         │ Thymeleaf       │  模板引擎
         │ calorie.html    │
         └────────┬────────┘
                  │
                  ▼
         ┌─────────────────┐
         │ HTML 响应       │  返回完整页面
         └─────────────────┘
```

---

## 十一、关键配置文件

### 11.1 application.yml

```yaml
server:
  port: 8080                    # 服务器端口

spring:
  thymeleaf:
    cache: false                # 开发时关闭缓存，修改立即生效

deepseek:
  api:
    key: your-api-key           # Deepseek API 密钥
    url: https://api.deepseek.com/v1/chat/completions
```

### 11.2 pom.xml（Maven 依赖）

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Thymeleaf 模板引擎 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>

    <!-- Lombok 简化代码 -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

---

## 十二、总结

### Spring Boot 核心概念

| 概念 | 作用 | 本项目示例 |
|------|------|-----------|
| **启动类** | 程序入口 | `MilkteaApplication.java` |
| **Controller** | 接收请求，返回响应 | `PageController`、`ApiController` |
| **Service** | 业务逻辑处理 | `UserService`、`CalorieService` |
| **Entity** | 数据模型 | `User`、`MilkTeaProduct` |
| **依赖注入** | 自动管理对象 | `@RequiredArgsConstructor` |
| **模板引擎** | 生成动态页面 | `Thymeleaf` + `templates/` |

### 文件关联图

```
MilkteaApplication.java (启动)
         │
         ├── 扫描 → controller/
         │              ├── PageController.java ──→ templates/*.html
         │              └── ApiController.java ──→ 返回 JSON
         │
         ├── 扫描 → service/
         │              ├── UserService.java ──→ entity/User.java
         │              └── CalorieService.java ──→ entity/MilkTeaProduct.java
         │
         └── 配置 → application.yml
```

---

**学习建议：**

1. 从 `MilkteaApplication.java` 开始，理解启动流程
2. 跟踪一个完整请求（如登录），从 Controller → Service → Entity
3. 理解依赖注入：看 `@RequiredArgsConstructor` 如何自动注入 Service
4. 尝试添加一个新的 API，体会整个流程
