package com.sakana.search.repository;

import com.sakana.search.document.DishDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * 菜品搜索仓库（#SEARCH-001）
 */
@Repository
public interface DishSearchRepository extends ElasticsearchRepository<DishDocument, Long> {
}
