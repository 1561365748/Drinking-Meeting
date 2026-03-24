package com.milktea.entity;

import lombok.Data;
import java.util.List;

/**
 * 奶茶品牌实体类
 */
@Data
public class MilkTeaBrand {
    private Integer id;
    private String name;
    private String logo;
    private List<MilkTeaProduct> products;
}
