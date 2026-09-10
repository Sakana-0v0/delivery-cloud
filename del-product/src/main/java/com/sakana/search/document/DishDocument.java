package com.sakana.search.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ES 菜品文档实体（#SEARCH-001）
 *
 * <p>索引名：dish
 * <p>分词策略：nameTokens / categoryTokens 使用 keyword 类型，Java 端 Ansj 分词后存入
 * <p>id 改为 String：兼容数字 fid（878500671165435905）和 UUID fid（3969771cc1b2458489e9741b88697ae0）
 */
@Document(indexName = "dish")
@Setting(settingPath = "elasticsearch/dish-settings.json")
@Data
public class DishDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Integer)
    private Integer calories;

    @Field(type = FieldType.Float)
    private Float protein;

    @Field(type = FieldType.Float)
    private Float fat;

    @Field(type = FieldType.Boolean)
    private Boolean available;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime updatedAt;

    // ============ ★ Java 端 Ansj 分词结果（Wildcard 查询依赖） ============
    @Field(type = FieldType.Keyword)
    private String nameTokens;       // "番茄 鸡蛋 汤"

    @Field(type = FieldType.Keyword)
    private String categoryTokens;   // "汤品"

    // ============ ★ BUG-007：描述分词（属性词搜索） ============
    @Field(type = FieldType.Keyword)
    private String propertyTokens;   // "外皮 酥脆 牛肉 鲜嫩 适合 老人"

    // ============ ★ Embedding 向量（未来向量检索预留） ============
    @Field(type = FieldType.Dense_Vector, dims = 1536)
    private List<Float> embedding;
}

