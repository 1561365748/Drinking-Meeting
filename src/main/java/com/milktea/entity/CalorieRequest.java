package com.milktea.entity;

import lombok.Data;
import java.util.List;

/**
 * 热量计算请求
 */
@Data
public class CalorieRequest {
    private Integer brandId;            // 品牌ID
    private Integer productId;          // 产品ID
    private String size;                // 容量: 小杯/中杯/大杯
    private List<Integer> toppingIds;   // 小料ID列表
    private String toppingImageBase64;  // 上传的小料图片(Base64)
}
