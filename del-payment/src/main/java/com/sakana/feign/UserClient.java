package com.sakana.feign;

import com.sakana.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * del-payment → del-user 的内部 API。
 * <p>
 * 极低频使用：仅在 Redis 订单快照过期后兜底取邮箱。
 * 正常路径下 callback 不调用此 Feign（解耦关键设计）。
 */
@FeignClient(name = "del-user", contextId = "paymentUserClient", fallbackFactory = UserClientFallback.class)
public interface UserClient {

    /**
     * 极简用户视图：仅取 email / nickname / username。
     */
    @GetMapping("/internal/users/{id}/contact")
    R<Map<String, Object>> getUserContact(@PathVariable("id") Long id);
}
