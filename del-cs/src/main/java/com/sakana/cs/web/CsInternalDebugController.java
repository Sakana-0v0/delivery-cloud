package com.sakana.cs.web;

import com.sakana.cs.feign.ProductSearchFeignClient;
import com.sakana.search.dto.SearchResponse;
import com.sakana.web.vo.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class CsInternalDebugController {
    private final ProductSearchFeignClient searchFeign;

    @GetMapping("/debug/search")
    public R<Map<String, Object>> debugSearch(@RequestParam String query) {
        log.info("[Debug] query={}", query);
        try {
            R<SearchResponse> resp = searchFeign.search(query, 1, 5);
            log.info("[Debug] resp.code={}, resp.data={}", resp.getCode(), resp.getData());
            if (resp != null && resp.getData() != null && resp.getData().getProducts() != null) {
                return R.ok(Map.of(
                    "products", resp.getData().getProducts(),
                    "total", resp.getData().getTotal(),
                    "size", resp.getData().getProducts().size()
                ));
            }
            return R.ok(Map.of("error", "no data", "resp", resp));
        } catch (Exception e) {
            log.error("[Debug] exception", e);
            return R.fail(500, e.getMessage());
        }
    }
}

