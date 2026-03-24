package com.milktea.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 图片预处理工具类
 * 通过调用Python脚本实现：
 * 1. 去噪 (Denoising) - 使用fastNlMeansDenoisingColored
 * 2. 低光照增强 (Low-light Enhancement) - 使用CLAHE + Gamma校正
 * 3. 去除杂物干扰 (Remove Artifacts) - 使用形态学操作
 */
@Slf4j
@Component
public class ImagePreprocessor {

    @Value("${image.preprocessor.python.path:python}")
    private String pythonPath;

    @Value("${image.preprocessor.script.path:scripts/image_preprocessor.py}")
    private String scriptPath;

    @Value("${image.preprocessor.enabled:true}")
    private boolean enabled;

    @Value("${image.preprocessor.timeout:30}")
    private int timeoutSeconds;

    private boolean pythonAvailable = false;

    @PostConstruct
    public void init() {
        checkPythonEnvironment();
    }

    /**
     * 检查Python环境是否可用
     */
    private void checkPythonEnvironment() {
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonPath, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            if (completed && process.exitValue() == 0) {
                pythonAvailable = true;
                log.info("Python环境检测成功: {}", pythonPath);
            } else {
                log.warn("Python环境检测失败，图片预处理功能将被禁用");
            }
        } catch (Exception e) {
            log.warn("Python环境检测异常: {}, 图片预处理功能将被禁用", e.getMessage());
            pythonAvailable = false;
        }
    }

    /**
     * 预处理图片
     * 1. 去噪
     * 2. 低光照增强
     * 3. 去除杂物干扰
     *
     * @param imageBase64 Base64编码的图片
     * @return 预处理后的Base64图片
     */
    public String preprocess(String imageBase64) {
        try {
            // 解码Base64验证格式
            String base64Data = imageBase64;
            String prefix = "";
            if (base64Data.contains(",")) {
                String[] parts = base64Data.split(",");
                prefix = parts[0] + ",";
                base64Data = parts[1];
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64Data);

            // 如果预处理被禁用或Python不可用，返回原图
            if (!enabled || !pythonAvailable) {
                log.debug("图片预处理被禁用或Python不可用，返回原图");
                return imageBase64;
            }

            // 调用Python脚本进行预处理
            String result = callPythonPreprocessor(imageBase64);

            if (result != null && !result.isEmpty()) {
                return result;
            }

            return imageBase64;

        } catch (Exception e) {
            log.error("图片预处理失败: {}", e.getMessage());
            return imageBase64;
        }
    }

    /**
     * 调用Python预处理脚本
     *
     * @param base64Input Base64编码的图片
     * @return 预处理后的Base64图片，失败返回null
     */
    private String callPythonPreprocessor(String base64Input) {
        try {
            // 获取项目根目录
            String projectRoot = System.getProperty("user.dir");
            String scriptFullPath = projectRoot + File.separator + scriptPath.replace("/", File.separator);

            // 使用 ProcessBuilder 构建调用 Python 脚本的命令：
            ProcessBuilder pb = new ProcessBuilder(
                pythonPath,
                scriptFullPath,
                "--format", "json"
            );

            pb.redirectErrorStream(false);

            // 启动进程
            Process process = pb.start();

            // 写入输入（通过stdin）将 Base64 编码的图片数据写入脚本的标准输入流。
            try (OutputStreamWriter writer = new OutputStreamWriter(
                process.getOutputStream(), "UTF-8")) {
                writer.write(base64Input);
                writer.flush();
            }

            // 使用 BufferedReader 读取脚本的标准输出流。
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            // 读取错误输出
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), "UTF-8"))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            // 等待进程完成
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                log.error("Python脚本执行超时");
                return null;
            }

            if (process.exitValue() != 0) {
                log.error("Python脚本执行失败: {}", errorOutput.toString());
                return null;
            }

            // 解析JSON结果
            String jsonOutput = output.toString().trim();
            if (jsonOutput.isEmpty()) {
                log.error("Python脚本输出为空");
                return null;
            }

            JSONObject result = JSON.parseObject(jsonOutput);

            if (result.getBooleanValue("success")) {
                String processedImage = result.getString("image");
                List<String> steps = result.getList("steps", String.class);
                log.info("图片预处理完成: {}", String.join(" -> ", steps));
                return processedImage;
            } else {
                log.error("图片预处理失败: {}", result.getString("error"));
                return null;
            }

        } catch (Exception e) {
            log.error("调用Python预处理脚本异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 检测图片质量
     *
     * @param imageBase64 Base64编码的图片
     * @return 图片质量检测结果
     */
    public ImageQuality checkQuality(String imageBase64) {
        ImageQuality quality = new ImageQuality();

        try {
            String base64Data = imageBase64;
            if (base64Data.contains(",")) {
                base64Data = base64Data.split(",")[1];
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64Data);

            // 基于文件大小简单判断
            int size = imageBytes.length;
            if (size < 10000) {
                quality.setBlurry(true);
                quality.setMessage("图片可能模糊，建议重新拍摄");
            } else if (size > 5000000) {
                quality.setTooLarge(true);
                quality.setMessage("图片过大，正在压缩处理");
            } else {
                quality.setGood(true);
                quality.setMessage("图片质量良好");
            }

        } catch (Exception e) {
            quality.setError(true);
            quality.setMessage("图片解析失败: " + e.getMessage());
        }

        return quality;
    }

    /**
     * 图片质量检测结果
     */
    @lombok.Data
    public static class ImageQuality {
        private boolean good = false;
        private boolean blurry = false;
        private boolean tooLarge = false;
        private boolean error = false;
        private String message;
    }
}
