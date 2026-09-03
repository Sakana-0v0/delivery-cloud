package com.sakana.services;

import com.sakana.exceptions.BizException;
import com.sakana.integration.message.MessageChannel;
import com.sakana.services.impl.UserErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务
 *
 * <p>提供邮箱验证码的生成、发送、校验能力。
 * 验证码存储在 Redis 中，支持限流。
 * <p>
 * 注意：当前为本地打印桩（MessageChannel stub），
 * 不真正发送邮件，仅让流程跑通。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyCodeService {

    private static final String KEY_PREFIX = "del-user:verify:code:";
    private static final String FREQ_PREFIX = "del-user:verify:freq:";
    private static final String CODE_FREQ_PREFIX = "del-user:verify:code-freq:";

    private static final int CODE_LENGTH = 6;
    private static final long TTL_SECONDS = 5 * 60;         // 5分钟
    private static final long SEND_INTERVAL_SECONDS = 60;   // 1分钟内只能发一次
    private static final int DAILY_LIMIT = 100;              // 每天最多发100次

    private final StringRedisTemplate stringRedisTemplate;
    private final MessageChannel messageChannel;

    /**
     * 发送验证码到邮箱
     *
     * @param email 目标邮箱
     * @param scene 场景：register=注册，bind=绑定邮箱
     */
    public void sendCode(String email, String scene) {
        String codeKey = KEY_PREFIX + email;
        String freqKey = FREQ_PREFIX + email;
        String dailyKey = CODE_FREQ_PREFIX + email;

        // 1. 限流检查：1分钟内只能发一次
        String lastSend = stringRedisTemplate.opsForValue().get(freqKey);
        if (lastSend != null) {
            throw new BizException(UserErrorCode.PARAM_INVALID, "发送太频繁，请稍后再试");
        }

        // 2. 限流检查：每天最多发5次
        String dailyCount = stringRedisTemplate.opsForValue().get(dailyKey);
        if (dailyCount != null && Integer.parseInt(dailyCount) >= DAILY_LIMIT) {
            throw new BizException(UserErrorCode.PARAM_INVALID, "今日发送次数已用完，请明天再试");
        }

        // 3. 生成6位验证码
        String code = generateCode();

        // 4. 存入 Redis
        stringRedisTemplate.opsForValue().set(codeKey, code, TTL_SECONDS, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(freqKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // 5. 更新每日计数
        Long count = stringRedisTemplate.opsForValue().increment(dailyKey);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(dailyKey, getSecondsToMidnight(), TimeUnit.SECONDS);
        }

        // 6. 发送邮件（当前为桩，仅打印）
        messageChannel.send("email", email,
                "[外卖订餐] 您的验证码是 " + code + "，5分钟内有效",
                "verify-code", Map.of("code", code));

        log.info("[发送验证码] email={}, scene={}, code={}", email, scene, code);
    }

    /**
     * 校验验证码
     *
     * @param email 邮箱
     * @param code  用户输入的验证码
     */
    public void verifyCode(String email, String code) {
        if (email == null || code == null) {
            throw new BizException(UserErrorCode.PARAM_INVALID, "邮箱或验证码不能为空");
        }

        String key = KEY_PREFIX + email;
        String stored = stringRedisTemplate.opsForValue().get(key);

        if (stored == null) {
            throw new BizException(UserErrorCode.PARAM_INVALID, "验证码已过期，请重新获取");
        }

        if (!stored.equals(code)) {
            throw new BizException(UserErrorCode.PARAM_INVALID, "验证码错误");
        }

        // 校验成功后删除验证码（一次性）
        stringRedisTemplate.delete(key);
    }

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        Random random = new Random();
        int code = random.nextInt((int) Math.pow(10, CODE_LENGTH));
        return String.format("%0" + CODE_LENGTH + "d", code);
    }

    /**
     * 计算到当天午夜剩余秒数
     */
    private long getSecondsToMidnight() {
        long now = System.currentTimeMillis();
        long midnight = java.time.LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneOffset.of("+8")).toInstant().toEpochMilli();
        return (midnight - now) / 1000;
    }
}
