package com.sakana.feign;

import com.sakana.enums.PayErrorCode;
import com.sakana.feign.vo.OrderSnapshotVO;
import com.sakana.web.vo.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * OrderClient 的 fallback。
 * <p>
 * 触发场景：del-order 不可达 / 接口异常 / 超时。
 * del-payment 必须立即失败（用户感知），不能静默默认成功。
 */
@Slf4j
@Component
public class OrderClientFallback implements FallbackFactory<OrderClient> {

    @Override
    public OrderClient create(Throwable cause) {
        return orderId -> {
            log.error("[Feign] OrderClient.getOrderForPayment fallback orderId={} cause={}",
                    orderId, cause.toString());
            return R.fail(PayErrorCode.ORDER_SERVICE_UNAVAILABLE.getCode(),
                    PayErrorCode.ORDER_SERVICE_UNAVAILABLE.getMessage());
        };
    }
}
