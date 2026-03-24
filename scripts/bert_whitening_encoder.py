# -*- coding: utf-8 -*-
"""
BERT-Whitening 向量编码器

功能：
1. 加载中文BERT模型：原始向量 [768维]（各向异性）
2. 实现Whitening变换（改善向量各向同性）：(x - mean) @ W
3. 支持降维（默认256维）
4. 批量处理句子/词语，输出JSON格式向量

使用方法：
    python bert_whitening_encoder.py --input input.txt --output vectors.json
    python bert_whitening_encoder.py --texts "芝士奶盖" "清爽果茶" --output vectors.json
"""

import argparse
import json
import numpy as np
from typing import List, Dict, Optional
import torch
from transformers import BertTokenizer, BertModel


class BertWhiteningEncoder:
    """
    BERT-Whitening 编码器

    原理：
    1. 使用BERT获取句子/词语的embedding
    2. 通过Whitening变换消除各向异性
    3. 可选降维，提升计算效率
    """

    def __init__(
        self,
        model_name: str = "bert-base-chinese",
        device: str = None,
        n_components: int = 256
    ):
        """
        初始化编码器

        Args:
            model_name: BERT模型名称，默认中文BERT
            device: 计算设备，默认自动选择
            n_components: 降维后的维度，默认256
        """
        self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
        self.n_components = n_components

        print(f"正在加载模型: {model_name}")
        print(f"使用设备: {self.device}")

        # 加载BERT模型和分词器
        self.tokenizer = BertTokenizer.from_pretrained(model_name)
        self.model = BertModel.from_pretrained(model_name)
        self.model.to(self.device)
        self.model.eval()

        # Whitening参数（需要训练后设置）
        self.kernel = None  # 白化变换核
        self.bias = None    # 偏置项

        print(f"模型加载完成，原始维度: {self.model.config.hidden_size}")

    def get_bert_embeddings(self, texts: List[str]) -> np.ndarray:
        """
        获取BERT原始embeddings

        Args:
            texts: 文本列表

        Returns:
            embeddings: [N, hidden_size] 的numpy数组
        """
        embeddings = []

        print(f"正在编码 {len(texts)} 个文本...")

        with torch.no_grad():
            for i, text in enumerate(texts):
                if (i + 1) % 100 == 0:
                    print(f"  进度: {i + 1}/{len(texts)}")

                # 分词并编码
                inputs = self.tokenizer(
                    text,
                    return_tensors="pt",
                    padding=True,
                    truncation=True,
                    max_length=128
                )
                inputs = {k: v.to(self.device) for k, v in inputs.items()}

                # 获取BERT输出
                outputs = self.model(**inputs)

                # 使用[CLS] token的embedding作为句子表示
                # 也可以使用mean pooling: outputs.last_hidden_state.mean(dim=1)
                cls_embedding = outputs.last_hidden_state[:, 0, :].cpu().numpy()
                embeddings.append(cls_embedding[0])

        return np.array(embeddings)

    def compute_whitening_params(self, embeddings: np.ndarray):
        """
        计算Whitening变换参数

        原理：
        1. 计算embeddings的协方差矩阵
        2. 特征值分解
        3. 取前n_components个主成分作为变换核

        Args:
            embeddings: [N, D] 的embedding矩阵
        """
        print("正在计算Whitening参数...")

        # 1. 计算均值并中心化
        self.mean = np.mean(embeddings, axis=0)
        centered = embeddings - self.mean

        # 2. 计算协方差矩阵
        cov = np.dot(centered.T, centered) / len(centered)

        # 3. 特征值分解
        eigenvalues, eigenvectors = np.linalg.eigh(cov)

        # 4. 按特征值降序排列
        idx = np.argsort(eigenvalues)[::-1]
        eigenvalues = eigenvalues[idx]
        eigenvectors = eigenvectors[:, idx]

        # 5. 取前n_components个成分
        n_comp = min(self.n_components, len(eigenvalues))
        self.kernel = eigenvectors[:, :n_comp]
        self.selected_eigenvalues = eigenvalues[:n_comp]

        # 6. 白化变换: W = V * diag(1/sqrt(lambda))
        # 添加小常数防止除零
        eps = 1e-6
        self.whitening_kernel = self.kernel / np.sqrt(self.selected_eigenvalues + eps)

        print(f"Whitening参数计算完成，降维至 {n_comp} 维")
        print(f"保留方差比例: {sum(self.selected_eigenvalues) / sum(eigenvalues):.2%}")

    def transform(self, embeddings: np.ndarray) -> np.ndarray:
        """
        应用Whitening变换

        Args:
            embeddings: [N, D] 原始embeddings

        Returns:
            whitened: [N, n_components] 白化后的向量
        """
        if self.whitening_kernel is None:
            raise ValueError("请先调用 compute_whitening_params() 计算变换参数")

        # 中心化
        centered = embeddings - self.mean

        # 白化变换
        whitened = np.dot(centered, self.whitening_kernel)

        # L2归一化
        norms = np.linalg.norm(whitened, axis=1, keepdims=True)
        whitened = whitened / (norms + 1e-8)

        return whitened

    def encode(self, texts: List[str], fit: bool = True) -> np.ndarray:
        """
        编码文本为BERT-Whitening向量

        Args:
            texts: 文本列表
            fit: 是否重新计算Whitening参数

        Returns:
            vectors: [N, n_components] 的向量矩阵
        """
        # 获取原始BERT embeddings
        embeddings = self.get_bert_embeddings(texts)

        # 计算或应用Whitening参数
        if fit:
            self.compute_whitening_params(embeddings)

        return self.transform(embeddings)

    def save_whitening_params(self, filepath: str):
        """保存Whitening参数"""
        params = {
            "mean": self.mean.tolist(),
            "whitening_kernel": self.whitening_kernel.tolist(),
            "selected_eigenvalues": self.selected_eigenvalues.tolist(),
            "n_components": self.n_components
        }
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(params, f, ensure_ascii=False, indent=2)
        print(f"Whitening参数已保存至: {filepath}")

    def load_whitening_params(self, filepath: str):
        """加载Whitening参数"""
        with open(filepath, "r", encoding="utf-8") as f:
            params = json.load(f)

        self.mean = np.array(params["mean"])
        self.whitening_kernel = np.array(params["whitening_kernel"])
        self.selected_eigenvalues = np.array(params["selected_eigenvalues"])
        self.n_components = params["n_components"]
        print(f"Whitening参数已加载，维度: {self.n_components}")

    def save_whitening_params_to_project(self, filepath: str):
        """
        保存Whitening参数为项目格式（用于Java加载）
        """
        params = {
            "mean": self.mean.tolist(),
            "kernel": self.whitening_kernel.tolist(),
            "eigenvalues": self.selected_eigenvalues.tolist(),
            "dimensions": self.n_components
        }
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(params, f, ensure_ascii=False, indent=2)
        print(f"Whitening参数已保存（项目格式）: {filepath}")


def batch_encode(
    texts: List[str],
    output_file: str,
    model_name: str = "bert-base-chinese",
    n_components: int = 256,
    whitening_params_file: str = None
) -> Dict[str, List[float]]:
    """
    批量编码文本并保存为JSON

    Args:
        texts: 文本列表
        output_file: 输出JSON文件路径
        model_name: BERT模型名称
        n_components: 降维维度
        whitening_params_file: 预训练的Whitening参数文件

    Returns:
        vectors_dict: {文本: 向量} 字典
    """
    # 初始化编码器
    encoder = BertWhiteningEncoder(
        model_name=model_name,
        n_components=n_components
    )

    # 加载预训练参数或重新计算
    if whitening_params_file:
        encoder.load_whitening_params(whitening_params_file)
        vectors = encoder.encode(texts, fit=False)
    else:
        vectors = encoder.encode(texts, fit=True)

    # 构建输出字典
    vectors_dict = {}
    for text, vector in zip(texts, vectors):
        vectors_dict[text] = vector.tolist()

    # 保存结果
    output_data = {
        "model": model_name,
        "n_components": n_components,
        "vectors": vectors_dict
    }

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)

    print(f"\n编码完成！")
    print(f"  文本数量: {len(texts)}")
    print(f"  向量维度: {n_components}")
    print(f"  输出文件: {output_file}")

    # 保存Whitening参数供后续使用
    params_file = output_file.replace(".json", "_whitening_params.json")
    encoder.save_whitening_params(params_file)

    return vectors_dict


def main():
    parser = argparse.ArgumentParser(description="BERT-Whitening 向量编码器")
    parser.add_argument("--input", "-i", help="输入文件路径（每行一个文本）")
    parser.add_argument("--texts", "-t", nargs="+", help="直接输入文本")
    parser.add_argument("--output", "-o", default="vectors.json", help="输出文件路径")
    parser.add_argument("--model", "-m", default="bert-base-chinese", help="BERT模型名称")
    parser.add_argument("--dim", "-d", type=int, default=256, help="降维维度")
    parser.add_argument("--params", "-p", help="预训练Whitening参数文件")

    args = parser.parse_args()

    # 获取输入文本
    if args.texts:
        texts = args.texts
    elif args.input:
        with open(args.input, "r", encoding="utf-8") as f:
            texts = [line.strip() for line in f if line.strip()]
    else:
        # 默认示例
        texts = [
            "芝士奶盖",
            "清爽果茶",
            "浓郁奶茶",
            "低卡饮品",
            "珍珠奶茶",
            "柠檬绿茶",
            "芒果冰沙",
            "红豆牛奶"
        ]
        print("使用默认示例文本...")

    # 执行编码
    batch_encode(
        texts=texts,
        output_file=args.output,
        model_name=args.model,
        n_components=args.dim,
        whitening_params_file=args.params
    )


if __name__ == "__main__":
    main()
