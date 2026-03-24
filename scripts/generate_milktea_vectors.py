# -*- coding: utf-8 -*-
"""
奶茶项目专用：生成BERT-Whitening向量

用于生成项目所需的预训练向量文件 data/bert_vectors.json
"""

from bert_whitening_encoder import BertWhiteningEncoder, batch_encode
import json

# 奶茶项目关键词列表
KEYWORDS = [
    # 口味特征
    "清爽", "浓郁", "香甜", "醇厚", "丝滑", "回甘",
    "果茶", "奶茶", "芝士", "奶盖", "珍珠", "椰果",
    # 健康特征
    "低卡", "无糖", "少糖", "半糖", "热饮", "冷饮",
    # 原料特征
    "绿茶", "红茶", "乌龙", "茉莉", "柠檬", "芒果",
    "葡萄", "草莓", "桃子", "百香果", "杨梅", "柚子",
    # 口感特征
    "酸甜", "苦涩", "清香", "花香", "果香", "奶香"
]

# 产品名称列表（从项目数据中提取）
PRODUCTS = [
    # 喜茶
    "多肉葡萄", "芝芝莓莓", "芝士奶盖绿茶", "芝士茉莉翠兰",
    "纯绿妍茶底", "霸气玉油柑", "冰鲜柠檬水",
    # 奈雪
    "霸气橙子", "霸气芝士草莓", "宝藏茶", "鸭屎香柠檬茶",
    # 蜜雪冰城
    "冰鲜柠檬水", "珍珠奶茶", "草莓摇摇奶昔", "芒果冰沙",
    # 茶百道
    "杨枝甘露", "豆乳玉麒麟", "超级杯水果茶", "芋泥啵啵奶茶",
    # 古茗
    "超A芝士葡萄", "芝士茉莉绿茶", "杨枝甘露", "黑糖珍珠鲜奶",
    # 一点点
    "四季春茶", "阿萨姆红茶", "茉莉绿茶", "冰淇淋红茶",
    # CoCo都可
    "奶茶三兄弟", "鲜芋青稞牛奶", "双拼奶茶", "茉莉绿茶",
    # 书亦烧仙草
    "烧仙草", "杨枝甘露", "葡萄多肉", "草莓啵啵"
]


def generate_project_vectors():
    """生成项目所需的向量文件"""

    print("=" * 50)
    print("奶茶项目 BERT-Whitening 向量生成")
    print("=" * 50)

    # 合并所有需要编码的文本
    all_texts = list(set(KEYWORDS + PRODUCTS))

    print(f"\n待编码文本数量: {len(all_texts)}")
    print(f"  - 关键词: {len(KEYWORDS)}")
    print(f"  - 产品名: {len(PRODUCTS)}")

    # 编码
    encoder = BertWhiteningEncoder(
        model_name="bert-base-chinese",
        n_components=256
    )

    # 获取向量
    vectors = encoder.encode(all_texts, fit=True)

    # 构建输出格式（与项目兼容）
    output = {
        "keywords": {},
        "products": {},
        "metadata": {
            "model": "bert-base-chinese",
            "method": "bert-whitening",
            "dimensions": 256,
            "keyword_count": len(KEYWORDS),
            "product_count": len(PRODUCTS)
        }
    }

    for text, vector in zip(all_texts, vectors):
        vector_list = [round(v, 6) for v in vector.tolist()]
        if text in KEYWORDS:
            output["keywords"][text] = vector_list
        if text in PRODUCTS:
            output["products"][text] = vector_list

    # 保存向量文件
    output_path = "../data/bert_vectors.json"
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print(f"\n向量文件已保存: {output_path}")

    # 保存Whitening参数
    params_path = "../data/whitening_params.json"
    encoder.save_whitening_params(params_path)

    # 计算相似度示例
    print("\n" + "=" * 50)
    print("相似度示例")
    print("=" * 50)

    def cosine_sim(v1, v2):
        return sum(a * b for a, b in zip(v1, v2))

    # 示例1: 芝士相关
    cheese = output["keywords"]["芝士"]
    cheese_grape = output["products"]["芝芝莓莓"]
    cheese_tea = output["products"]["芝士奶盖绿茶"]
    lemon = output["products"]["冰鲜柠檬水"]

    print(f"\n'芝士' vs '芝芝莓莓': {cosine_sim(cheese, cheese_grape):.4f}")
    print(f"'芝士' vs '芝士奶盖绿茶': {cosine_sim(cheese, cheese_tea):.4f}")
    print(f"'芝士' vs '冰鲜柠檬水': {cosine_sim(cheese, lemon):.4f}")

    # 示例2: 清爽相关
    fresh = output["keywords"]["清爽"]
    lemon_tea = output["products"]["冰鲜柠檬水"]
    milk_tea = output["products"]["珍珠奶茶"]

    print(f"\n'清爽' vs '冰鲜柠檬水': {cosine_sim(fresh, lemon_tea):.4f}")
    print(f"'清爽' vs '珍珠奶茶': {cosine_sim(fresh, milk_tea):.4f}")


if __name__ == "__main__":
    generate_project_vectors()
