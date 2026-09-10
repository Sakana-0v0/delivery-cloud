package com.sakana.search.service;

import lombok.Data;

/**
 * 解析后的查询条件（#SEARCH-001）
 */
@Data
public class ParsedQuery {

    /** 搜索关键词 */
    private String keyword;

    /** 热量上限 */
    private Integer maxCalories;

    /** 脂肪上限 */
    private Float maxFat;

    /** 蛋白下限 */
    private Float minProtein;

    /** 分类 */
    private String category;

    public boolean hasNutritionFilter() {
        return maxCalories != null || maxFat != null || minProtein != null;
    }
}
