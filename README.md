# 奶茶君

> 让每一杯奶茶都喝得明明白白

## 项目简介

奶茶君是一个基于 Spring Boot 的奶茶热量计算与智能推荐系统，帮助用户了解奶茶热量、做出更健康的饮品选择，并提供三种智能推荐算法。

## 功能特性

### 1. 用户系统
- 数字账号登录（新用户自动注册）
- 个人档案管理（疾病史、过敏信息、口味偏好）
- 历史选择记录

### 2. 三种智能推荐
- **Deepseek AI辅助推荐**：基于AI大模型，结合用户偏好和档案智能生成
- **传统推荐（协同过滤）**：基于相似用户的喜好进行推荐
- **智能匹配（BERT-Whitening）**：使用BERT-Whitening编码模型生成产品语义向量，计算余弦相似度进行推荐

#### 2.1 三种推荐方法对比分析

| 维度 | Deepseek AI推荐 | 协同过滤(ItemCF) | BERT-Whitening词向量 |
|------|----------------|-----------------|---------------------|
| **冷启动处理** | ✅ 优秀（无需历史数据） | ❌ 较差（依赖交互数据） | ✅ 优秀（基于内容特征） |
| **响应速度** | ⚠️ 较慢（3-5秒，API调用） | ✅ 快速（<100ms） | ✅ 快速（<50ms，预计算） |
| **个性化程度** | ✅ 高（理解复杂需求） | ✅ 高（基于行为模式） | ⚠️ 中等（基于显式偏好） |
| **可解释性** | ✅ 强（自然语言解释） | ⚠️ 中等（相似用户行为） | ✅ 强（匹配分数+关键词） |
| **部署成本** | ⚠️ 需API调用费用 | ✅ 无额外成本 | ✅ 无额外成本 |
| **数据依赖** | 无 | 用户行为数据 | 产品特征数据 |

**适用场景对比：**

| 场景 | 推荐方法 | 原因 |
|------|---------|------|
| 新用户首次访问 | Deepseek AI / BERT-Whitening | 无历史行为数据，协同过滤失效 |
| 高并发实时推荐 | BERT-Whitening / 协同过滤 | 预计算向量，响应速度快 |
| 复杂需求理解 | Deepseek AI | 大模型能理解自然语言描述 |
| 精准个性化 | 协同过滤 | 基于用户真实行为模式 |
| 低成本部署 | 协同过滤 / BERT-Whitening | 无API调用费用 |
| 隐私敏感场景 | 协同过滤 / BERT-Whitening | 数据本地处理，无需上传 |

**实际测试示例：**

**示例1：新用户冷启动场景**
```
用户输入：偏好清爽、果茶，低卡需求
- Deepseek AI: 推荐葡萄柚绿妍、百香果四季春（AI生成，含详细解释）
- 协同过滤: 返回热门商品（无个性化，因为无历史数据）
- BERT-Whitening: 推荐霸气玉油柑(100分)、纯绿妍茶底(100分)、冰鲜柠檬水(95.4分)
```
👉 结论：冷启动场景下，Deepseek AI和BERT-Whitening效果更好

**示例2：老用户精准推荐场景**
```
用户历史：多次选择芝士类饮品，高评分
- Deepseek AI: 推荐芝士奶盖系列（基于用户画像）
- 协同过滤: 推荐芝芝莓莓、芝士茉莉翠兰（基于相似用户行为）
- BERT-Whitening: 推荐含"芝士"关键词的产品
```
👉 结论：有丰富行为数据时，协同过滤更精准

**示例3：复杂需求理解场景**
```
用户输入："我今天心情不好，想喝点甜的安慰自己，但又不想太胖"
- Deepseek AI: 理解情感需求+健康约束，推荐芝士奶盖（半糖）+解释
- 协同过滤: 无法理解自然语言，返回热门商品
- BERT-Whitening: 匹配"甜"关键词，但无法理解情感需求
```
👉 结论：复杂语义理解场景，Deepseek AI优势明显

#### 2.2 BERT-Whitening编码优势

**什么是BERT-Whitening？**

BERT-Whitening是一种句子嵌入优化技术，通过白化变换(Whitening)改善BERT原始向量的各向同性问题。

```
原始BERT向量 → 各向异性（向量分布不均匀）
      ↓
Whitening变换 → 各向同性（向量分布均匀）
      ↓
降维处理 → 256维语义向量
```

**为什么选择BERT-Whitening？**

| 对比维度 | 原始BERT | BERT-Whitening | Word2Vec |
|---------|---------|----------------|----------|
| **语义理解** | ✅ 深度语义 | ✅ 深度语义 | ⚠️ 浅层语义 |
| **向量质量** | ⚠️ 各向异性 | ✅ 各向同性 | ✅ 各向同性 |
| **计算开销** | ❌ 高（768维） | ✅ 低（可降维） | ✅ 低 |
| **中文支持** | ✅ 原生支持 | ✅ 原生支持 | ⚠️ 需分词 |
| **无监督训练** | ✅ | ✅ | ❌ 需语料 |

**核心优势：**

1. **解决各向异性问题**
   - 原始BERT向量倾向于聚集在向量空间的狭窄区域
   - Whitening变换使向量均匀分布，提升余弦相似度的区分度
   - 示例：`similarity("芝士奶盖", "芝士葡萄")` 在Whitening后更准确

2. **支持降维加速**
   - 原始BERT: 768维，计算开销大
   - BERT-Whitening: 可降至256/128维，速度提升3-6倍
   - 本项目使用256维，平衡精度与性能

3. **预计算+缓存友好**
   - 产品向量可离线预计算，存储为JSON
   - 在线推荐只需向量检索，响应时间<50ms
   - 适合高并发场景

**实际效果示例：**

```
产品A: "芝士奶盖绿茶" → 向量 [0.28, 0.02, 0.95, 0.02, 0.68, ...]
产品B: "芝士茉莉翠兰" → 向量 [0.18, 0.02, 0.92, 0.02, 0.58, ...]
产品C: "冰鲜柠檬水"   → 向量 [0.18, 0.95, 0.02, 0.68, 0.01, ...]

用户偏好: "浓郁芝士口感" → 向量 [0.22, 0.05, 0.95, 0.02, 0.68, ...]

相似度计算:
- 产品A vs 用户偏好: 0.95 (高度匹配 ✓)
- 产品B vs 用户偏好: 0.88 (较好匹配 ✓)
- 产品C vs 用户偏好: 0.32 (不匹配 ✗)
```

**技术实现细节：**

```java
// BERT-Whitening编码流程
String productName = "芝士奶盖绿茶";

// 1. 提取关键词
keywords = ["芝士", "奶盖", "绿茶"];

// 2. 获取关键词的BERT-Whitening向量
keywordVectors = [
  "芝士" → [0.18, 0.02, 0.92, ...],  // 浓郁、奶香特征
  "奶盖" → [0.28, 0.02, 0.95, ...],  // 口感特征
  "绿茶" → [0.02, 0.88, 0.02, ...]   // 清爽特征
];

// 3. 聚合生成产品向量
productVector = aggregate(keywordVectors);
// → [0.16, 0.31, 0.63, ...] (归一化后)

// 4. 计算与用户偏好的余弦相似度
similarity = cosineSimilarity(userVector, productVector);
// → 0.95
```

**与其它方法的对比：**

| 方法 | 优点 | 缺点 | 本项目为何不选 |
|------|------|------|---------------|
| TF-IDF | 简单快速 | 无法捕捉语义 | 无法理解"芝士"和"芝芝"相似 |
| Word2Vec | 语义向量 | 需大规模语料 | 中文奶茶领域语料不足 |
| 原始BERT | 深度语义 | 各向异性、计算慢 | 向量区分度差 |
| **BERT-Whitening** | 深度语义+各向同性+可降维 | 需预计算 | ✅ 最佳选择 |

### 3. 热量计算
- 支持8大主流奶茶品牌（喜茶、奈雪、蜜雪冰城、茶百道、古茗、一点点、CoCo都可、书亦烧仙草）
- 支持46+奶茶产品热量查询
- 支持30+小料热量累加计算
- 根据容量（小杯/中杯/大杯）自动调整热量
- 提供健康建议和运动消耗方案

### 4. 反馈系统
- 选择饮品后提供反馈（评分、糖度、温度、文字反馈）
- 反馈数据用于优化后续推荐

## 技术栈

| 类型 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2 + Java 17 |
| 模板引擎 | Thymeleaf |
| 前端框架 | Bootstrap 5.3 + Bootstrap Icons |
| AI推荐 | Deepseek API |
| 推荐算法 | 协同过滤(ItemCF) + BERT-Whitening词向量 |
| 数据格式 | JSON文件存储 |

## 项目结构

```
milktea/
├── pom.xml                         # Maven项目配置文件
├── mvnw.cmd                        # Maven Wrapper启动脚本（Windows）
├── 一键启动.bat                     # 一键启动脚本
├── README.md                       # 项目说明文档
├── INSTALL.md                      # 安装说明文档
│
├── data/                           # 数据文件目录
│   ├── milk_tea_data.json          # 奶茶品牌和产品数据
│   ├── topping_data.json           # 小料数据
│   ├── exercise_data.json          # 运动消耗数据
│   ├── popular_ranking.json        # 热门排行数据
│   └── user_data.json              # 用户数据（自动生成）
│
├── img/                            # 图片资源目录
│   ├── milktea/                    # 奶茶产品图片
│   │   ├── xicha/                  # 喜茶
│   │   ├── naixue/                 # 奈雪的茶
│   │   ├── mixue/                  # 蜜雪冰城
│   │   ├── chabaidao/              # 茶百道
│   │   ├── guming/                 # 古茗
│   │   ├── yidiandian/             # 一点点
│   │   ├── coco/                   # CoCo都可
│   │   └── shuyi/                  # 书亦烧仙草
│   ├── toppings/                   # 小料图片
│   └── exercises/                  # 运动示意图
│
├── src/main/java/com/milktea/      # Java源代码
│   ├── MilkteaApplication.java     # Spring Boot启动类
│   ├── controller/                 # 控制器层
│   │   ├── PageController.java     # 页面路由控制器
│   │   └── ApiController.java      # API接口控制器
│   ├── service/                    # 服务层
│   │   ├── DataService.java        # 数据加载服务
│   │   ├── UserService.java        # 用户管理服务
│   │   ├── CalorieService.java     # 热量计算服务
│   │   ├── DeepseekRecommendService.java   # Deepseek AI推荐
│   │   ├── TraditionalRecommendService.java # 传统协同过滤推荐
│   │   ├── SmartMatchRecommendService.java  # BERT-Whitening词向量匹配
│   │   └── ImageService.java       # 图片处理服务
│   ├── entity/                     # 实体类
│   │   ├── User.java               # 用户实体
│   │   ├── MilkTeaBrand.java       # 品牌实体
│   │   ├── MilkTeaProduct.java     # 产品实体
│   │   ├── Topping.java            # 小料实体
│   │   ├── Exercise.java           # 运动实体
│   │   ├── RecommendRequestV2.java # 推荐请求
│   │   └── RecommendResultV2.java  # 推荐结果
│   ├── algorithm/                  # 算法层
│   │   └── ItemCFRecommender.java  # 协同过滤推荐算法
│   ├── config/                     # 配置类
│   │   ├── WebConfig.java          # Web配置
│   │   └── GlobalExceptionHandler.java # 全局异常处理
│   └── util/                       # 工具类
│       └── ImagePreprocessor.java  # 图片预处理工具
│
├── src/main/resources/             # 资源文件
│   ├── application.yml             # 应用配置文件
│   ├── templates/                  # 页面模板（Thymeleaf）
│   │   ├── login.html              # 登录页
│   │   ├── home.html               # 首页
│   │   ├── profile_setup.html      # 新用户档案设置
│   │   ├── profile.html            # 个人中心
│   │   ├── recommend.html          # 推荐选择页
│   │   ├── recommend_result.html   # 推荐结果页
│   │   ├── calorie.html            # 热量计算页
│   │   ├── about.html              # 关于页面
│   │   └── index.html              # 默认首页
│   └── static/                     # 静态资源
│       ├── css/style.css           # 自定义样式
│       └── js/                     # JavaScript文件
│           ├── recommend.js        # 推荐页脚本
│           └── calorie.js          # 热量计算脚本
│
└── target/                         # 编译输出目录（自动生成）
```

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.6+（或使用项目自带的Maven Wrapper）

### 方式一：一键启动（推荐）
双击运行 `一键启动.bat`，脚本会自动：
1. 检测Java环境
2. 如无Java则提示下载安装
3. 启动Spring Boot项目

### 方式二：命令行启动
```bash
# 进入项目目录
cd E:\TestCL\milktea

# 使用Maven Wrapper启动
mvnw.cmd spring-boot:run

# 或使用系统Maven
mvn spring-boot:run
```

### 访问系统
打开浏览器访问：http://localhost:8080

## 页面导航

```
登录页 (/login)
    │
    ├─→ 首页 (/home) ←─────────────────┐
    │       │                          │
    │       ├─→ 智能推荐 (/recommend)  │
    │       │       │                  │
    │       │       └─→ 推荐结果       │
    │       │                          │
    │       ├─→ 热量计算 (/calorie)    │
    │       │                          │
    │       └─→ 个人中心 (/profile) ───┘
    │               │
    │               └─→ 历史记录
    │
    └─→ 新用户 → 档案设置 (/profile/setup)
```

## API接口

### 用户认证
```
POST /api/auth/login
Body: { "userId": "123456" }
Response: { "success": true, "isNewUser": false, "user": {...} }
```

### 获取推荐（三种方法）
```
POST /api/recommend/all
Body: {
  "brandIds": [1, 2],
  "preferredFlavors": ["清爽", "果茶"],
  "sweetLevel": 3,
  "note": "今天想喝点清爽的"
}
```

### 更新用户档案
```
POST /api/user/profile
Body: {
  "diseaseHistory": ["无"],
  "allergies": ["无"],
  "preferredFlavors": ["清爽", "果茶"],
  "favoriteBrandId": 1
}
```

### 提交反馈
```
POST /api/user/feedback
Body: {
  "productName": "多肉葡萄",
  "brandName": "喜茶",
  "rating": 5,
  "sugarLevel": "少糖",
  "temperature": "去冰",
  "feedback": "很好喝！"
}
```

### 热量计算
```
POST /api/calorie/calculate
Body: {
  "brandId": 1,
  "productId": 101,
  "size": "中杯",
  "toppingIds": [1, 3, 4]
}
```

## 配置说明

### Deepseek API配置
在 `src/main/resources/application.yml` 中配置：
```yaml
deepseek:
  api:
    key: your-api-key-here
    url: https://api.deepseek.com/v1/chat/completions
```

## 健康建议

- **低热量 (<150大卡)**: 相对健康，可放心饮用
- **中等热量 (150-300大卡)**: 适中，建议适量
- **高热量 (300-450大卡)**: 建议分次饮用或配合运动
- **超高热量 (>450大卡)**: 建议谨慎选择，控制饮用频率

## 注意事项

1. 热量数据仅供参考，实际热量可能因制作方式不同而有所差异
2. 建议每日添加糖摄入不超过25g
3. Deepseek API需要网络连接和有效API Key

## 版本历史

- v1.1.0 (2026-03-21)
  - 新增三种推荐算法（Deepseek AI、传统推荐、智能匹配）
  - 新增用户系统和反馈系统
  - 改用JSON文件存储用户数据
  - 优化页面导航和用户体验

- v1.0.0 (2024-03-19)
  - 初始版本发布
  - 支持热量计算、智能推荐功能

## 开源协议

MIT License

---

茶遇** - 让每一杯奶茶都喝得明明白白
