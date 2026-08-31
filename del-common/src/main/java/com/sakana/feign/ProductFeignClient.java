package com.sakana.feign;

import com.sakana.feign.vo.ProductSnapshotVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商品服务 Feign 客户端
 * <p>
 * 供 del-order、del-cart 等服务调用 del-product。
 */
@FeignClient(name = "del-product", contextId = "productFeignClient")
public interface ProductFeignClient {

    /**
     * 获取商品快照
     *
     * @param id 商品ID
     * @return 商品快照信息
     */
    @GetMapping("/api/v1/products/{id}/snapshot")
    ProductSnapshotVO getProductSnapshot(@PathVariable("id") Long id);
}
