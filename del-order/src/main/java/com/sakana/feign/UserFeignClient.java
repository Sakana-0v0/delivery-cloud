package com.sakana.feign;

import com.sakana.feign.vo.UserContactVO;
import com.sakana.web.vo.R;
import com.sakana.web.vo.UserAddressVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务 Feign 客户端（供 del-order 内部调用）
 */
@FeignClient(name = "del-user", contextId = "orderUserFeignClient")
public interface UserFeignClient {

    /**
     * 根据地址ID查询收货地址（内部接口，供订单服务调用）
     */
    @GetMapping("/internal/admin/addresses/{id}")
    R<UserAddressVO> getAddressById(@PathVariable("id") Long id);

    /**
     * 获取用户联系信息（用户名 + 邮箱），用于订单 outbox 等场景
     */
    @GetMapping("/internal/users/{id}/contact")
    R<UserContactVO> getUserContact(@PathVariable("id") Long id);
}
