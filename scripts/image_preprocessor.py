# -*- coding: utf-8 -*-
"""
图片预处理脚本 - 使用OpenCV实现

功能：
1. 去噪 (Denoising)
2. 低光照增强 (Low-light Enhancement)
3. 去除杂物干扰 (Remove Artifacts)

使用方法：
    python image_preprocessor.py --input input.jpg --output output.jpg
    python image_preprocessor.py --base64 <base64_string>  # 输出base64
"""

import argparse
import base64
import json
import sys
import numpy as np
import cv2
from typing import Tuple, Optional


class ImagePreprocessor:
    """图片预处理器"""

    def __init__(self):
        self.processed_steps = []

    def denoise(self, image: np.ndarray, strength: int = 10) -> np.ndarray:
        """
        去噪处理

        Args:
            image: 输入图片
            strength: 去噪强度 (1-20)

        Returns:
            去噪后的图片
        """
        if len(image.shape) == 3:
            # 彩色图片使用fastNlMeansDenoisingColored
            denoised = cv2.fastNlMeansDenoisingColored(
                image,
                None,
                h=strength,           # 亮度去噪强度
                hColor=strength,      # 颜色去噪强度
                templateWindowSize=7,
                searchWindowSize=21
            )
        else:
            # 灰度图片
            denoised = cv2.fastNlMeansDenoising(
                image,
                None,
                h=strength,
                templateWindowSize=7,
                searchWindowSize=21
            )

        self.processed_steps.append("去噪")
        return denoised

    def enhance_low_light(
        self,
        image: np.ndarray,
        gamma: float = 1.2,
        clip_limit: float = 2.0
    ) -> np.ndarray:
        """
        低光照增强

        使用Gamma校正 + CLAHE (对比度受限自适应直方图均衡化)

        Args:
            image: 输入图片
            gamma: Gamma值 (>1 变亮, <1 变暗)
            clip_limit: CLAHE裁剪限制

        Returns:
            增强后的图片
        """
        # 转换到LAB色彩空间
        if len(image.shape) == 3:
            lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
            l, a, b = cv2.split(lab)

            # 对L通道应用CLAHE
            clahe = cv2.createCLAHE(clipLimit=clip_limit, tileGridSize=(8, 8))
            l_enhanced = clahe.apply(l)

            # 合并通道
            lab_enhanced = cv2.merge([l_enhanced, a, b])
            enhanced = cv2.cvtColor(lab_enhanced, cv2.COLOR_LAB2BGR)
        else:
            # 灰度图片直接应用CLAHE
            clahe = cv2.createCLAHE(clipLimit=clip_limit, tileGridSize=(8, 8))
            enhanced = clahe.apply(image)

        # Gamma校正
        inv_gamma = 1.0 / gamma
        table = np.array([
            ((i / 255.0) ** inv_gamma) * 255
            for i in np.arange(0, 256)
        ]).astype("uint8")
        enhanced = cv2.LUT(enhanced, table)

        self.processed_steps.append("低光照增强")
        return enhanced

    def remove_artifacts(
        self,
        image: np.ndarray,
        kernel_size: int = 3,
        method: str = "morphological"
    ) -> np.ndarray:
        """
        去除杂物干扰

        Args:
            image: 输入图片
            kernel_size: 核大小
            method: 方法 ("morphological" 或 "median")

        Returns:
            处理后的图片
        """
        if method == "morphological":
            # 形态学操作去除小噪点
            kernel = cv2.getStructuringElement(
                cv2.MORPH_ELLIPSE,
                (kernel_size, kernel_size)
            )

            if len(image.shape) == 3:
                # 对每个通道处理
                channels = cv2.split(image)
                processed_channels = []
                for channel in channels:
                    # 开运算去除小亮点
                    opened = cv2.morphologyEx(channel, cv2.MORPH_OPEN, kernel)
                    # 闭运算填充小暗点
                    closed = cv2.morphologyEx(opened, cv2.MORPH_CLOSE, kernel)
                    processed_channels.append(closed)
                result = cv2.merge(processed_channels)
            else:
                opened = cv2.morphologyEx(image, cv2.MORPH_OPEN, kernel)
                result = cv2.morphologyEx(opened, cv2.MORPH_CLOSE, kernel)

        elif method == "median":
            # 中值滤波去除椒盐噪声
            result = cv2.medianBlur(image, kernel_size)

        else:
            result = image

        self.processed_steps.append("去除杂物干扰")
        return result

    def sharpen(self, image: np.ndarray, strength: float = 0.5) -> np.ndarray:
        """
        锐化增强

        Args:
            image: 输入图片
            strength: 锐化强度

        Returns:
            锐化后的图片
        """
        # 使用拉普拉斯锐化
        kernel = np.array([
            [0, -1, 0],
            [-1, 5, -1],
            [0, -1, 0]
        ]) * strength
        kernel[1, 1] = 1 + 4 * strength

        sharpened = cv2.filter2D(image, -1, kernel)

        self.processed_steps.append("锐化")
        return sharpened

    def preprocess(
        self,
        image: np.ndarray,
        denoise_strength: int = 10,
        gamma: float = 1.2,
        remove_artifacts: bool = True,
        sharpen_result: bool = True
    ) -> np.ndarray:
        """
        完整预处理流程

        Args:
            image: 输入图片
            denoise_strength: 去噪强度
            gamma: 低光照增强Gamma值
            remove_artifacts: 是否去除杂物
            sharpen_result: 是否锐化

        Returns:
            预处理后的图片
        """
        self.processed_steps = []

        # 1. 去噪
        result = self.denoise(image, strength=denoise_strength)

        # 2. 低光照增强
        result = self.enhance_low_light(result, gamma=gamma)

        # 3. 去除杂物干扰
        if remove_artifacts:
            result = self.remove_artifacts(result)

        # 4. 锐化
        if sharpen_result:
            result = self.sharpen(result, strength=0.3)

        return result

    def get_steps(self) -> list:
        """获取处理步骤列表"""
        return self.processed_steps


def decode_base64(base64_string: str) -> np.ndarray:
    """解码Base64图片"""
    # 移除data:image/xxx;base64,前缀
    if "," in base64_string:
        base64_string = base64_string.split(",")[1]

    image_data = base64.b64decode(base64_string)
    nparr = np.frombuffer(image_data, np.uint8)
    image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    return image


def encode_base64(image: np.ndarray, format: str = ".jpg") -> str:
    """编码图片为Base64"""
    _, buffer = cv2.imencode(format, image)
    base64_string = base64.b64encode(buffer).decode("utf-8")

    # 添加前缀
    mime_type = "image/jpeg" if format == ".jpg" else "image/png"
    return f"data:{mime_type};base64,{base64_string}"


def process_from_base64(base64_input: str, output_format: str = "json") -> str:
    """
    从Base64处理图片

    Args:
        base64_input: 输入Base64字符串
        output_format: 输出格式 ("json" 或 "base64")

    Returns:
        处理结果
    """
    try:
        # 解码图片
        image = decode_base64(base64_input)

        if image is None:
            return json.dumps({
                "success": False,
                "error": "无法解码图片"
            }, ensure_ascii=False)

        # 预处理
        preprocessor = ImagePreprocessor()
        processed = preprocessor.preprocess(image)

        # 编码结果
        output_base64 = encode_base64(processed)

        if output_format == "json":
            result = {
                "success": True,
                "image": output_base64,
                "steps": preprocessor.get_steps(),
                "original_size": f"{image.shape[1]}x{image.shape[0]}",
                "processed_size": f"{processed.shape[1]}x{processed.shape[0]}"
            }
            return json.dumps(result, ensure_ascii=False)
        else:
            return output_base64

    except Exception as e:
        return json.dumps({
            "success": False,
            "error": str(e)
        }, ensure_ascii=False)


def process_from_file(input_path: str, output_path: str):
    """
    从文件处理图片

    Args:
        input_path: 输入文件路径
        output_path: 输出文件路径
    """
    # 读取图片
    image = cv2.imread(input_path)

    if image is None:
        print(f"错误: 无法读取图片 {input_path}")
        return

    # 预处理
    preprocessor = ImagePreprocessor()
    processed = preprocessor.preprocess(image)

    # 保存结果
    cv2.imwrite(output_path, processed)

    print(f"处理完成!")
    print(f"  输入: {input_path}")
    print(f"  输出: {output_path}")
    print(f"  处理步骤: {' -> '.join(preprocessor.get_steps())}")


def main():
    parser = argparse.ArgumentParser(description="图片预处理工具")
    parser.add_argument("--input", "-i", help="输入图片路径")
    parser.add_argument("--output", "-o", help="输出图片路径")
    parser.add_argument("--base64", "-b", help="输入Base64编码的图片")
    parser.add_argument("--format", "-f", default="json",
                       choices=["json", "base64"],
                       help="输出格式 (json/base64)")

    args = parser.parse_args()

    if args.base64:
        # 从Base64处理
        result = process_from_base64(args.base64, args.format)
        print(result)

    elif args.input and args.output:
        # 从文件处理
        process_from_file(args.input, args.output)

    else:
        # 从stdin读取Base64 (用于Java调用)
        base64_input = sys.stdin.read().strip()
        if base64_input:
            result = process_from_base64(base64_input, "json")
            print(result)
        else:
            print(json.dumps({
                "success": False,
                "error": "请提供输入图片 (--input 或 --base64 或 stdin)"
            }, ensure_ascii=False))


if __name__ == "__main__":
    main()
