package com.sakana.feign;

import com.sakana.enums.PayErrorCode;
import com.sakana.web.vo.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class UserClientFallback implements FallbackFactory<UserClient> {

    @Override
    public UserClient create(Throwable cause) {
        return userId -> {
            log.error("[Feign] UserClient.getUserContact fallback userId={} cause={}",
                    userId, cause.toString());
            return R.fail(PayErrorCode.USER_SERVICE_UNAVAILABLE.getCode(),
                    PayErrorCode.USER_SERVICE_UNAVAILABLE.getMessage());
        };
    }
}
