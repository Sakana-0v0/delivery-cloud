package com.sakana.stats.impl;

import com.sakana.dao.entity.Product;
import com.sakana.dao.mapper.ProductMapper;
import com.sakana.stats.ProductStatsService;
import com.sakana.stats.dto.HotProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品统计服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductStatsServiceImpl implements ProductStatsService {

    private final ProductMapper productMapper;

    /** 最大返回数量限制 */
    private static final int MAX_LIMIT = 100;
    /** 默认返回数量 */
    private static final int DEFAULT_LIMIT = 10;

    @Override
    public List<HotProductVO> getHotProducts(int limit) {
        // 参数校验：确保 limit 在有效范围内（防御性编程）
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }

        // 使用带参数绑定的 Mapper 方法查询热卖商品，避免 SQL 注入风险
        List<Product> products = productMapper.selectHotProducts(limit);

        // 转换为 VO
        List<HotProductVO> voList = products.stream()
                .map(product -> {
                    HotProductVO vo = new HotProductVO();
                    vo.setProductId(product.getId());
                    vo.setProductName(product.getName());
                    vo.setCover(product.getCover());
                    vo.setSales(product.getSales());
                    vo.setRealPrice(product.getRealPrice());
                    return vo;
                })
                .collect(Collectors.toList());

        log.info("[ProductStats] getHotProducts: limit={}, resultSize={}", limit, voList.size());
        return voList;
    }
}
