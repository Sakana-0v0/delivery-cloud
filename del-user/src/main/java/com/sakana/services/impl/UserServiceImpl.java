package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.dao.entity.User;
import com.sakana.dao.entity.UserEventOutbox;
import com.sakana.dao.mapper.UserEventOutboxMapper;
import com.sakana.dao.mapper.UserMapper;
import com.sakana.dto.request.admin.AdminUserListQuery;
import com.sakana.dto.request.admin.UserStatusReq;
import com.sakana.services.impl.UserErrorCode;
import com.sakana.exceptions.BizException;
import com.sakana.dto.request.RegisterReq;
import com.sakana.security.TokenBlacklist;
import com.sakana.services.UserService;
import com.sakana.services.VerifyCodeService;
import com.sakana.web.vo.AdminUserPageResp;
import com.sakana.web.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户服务实现（C 端 + B 端）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserEventOutboxMapper userEventOutboxMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerifyCodeService verifyCodeService;
    private final TokenBlacklist tokenBlacklist;

    // ==================== C 端 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(RegisterReq req) {
        verifyCodeService.verifyCode(req.getEmail(), req.getCode());

        long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, req.getUsername())
                        .eq(User::getIsDeleted, 0)
        );
        if (usernameCount > 0) {
            throw new BizException(UserErrorCode.USERNAME_EXISTS);
        }

        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            long phoneCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getPhone, req.getPhone())
                            .eq(User::getIsDeleted, 0)
            );
            if (phoneCount > 0) {
                throw new BizException(UserErrorCode.PHONE_EXISTS);
            }
        }

        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            long emailCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getEmail, req.getEmail())
                            .eq(User::getIsDeleted, 0)
            );
            if (emailCount > 0) {
                throw new BizException(UserErrorCode.EMAIL_EXISTS);
            }
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getUsername());
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setStatus(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);

        log.info("[用户注册] 成功: userId={}, username={}", user.getId(), user.getUsername());

        // 写入 outbox 表（同一事务），由 UserEventOutboxRelay 投递到 MQ
        // 使用 payload JSON 字段存储事件数据
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", generateEventId());
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        payload.put("email", user.getEmail());

        UserEventOutbox outbox = new UserEventOutbox();
        outbox.setEventType("REGISTER");
        outbox.setUserId(user.getId());
        outbox.setPayload(payload);
        outbox.setStatus(0); // NEW
        outbox.setRetryCount(0);
        outbox.setCreateTime(LocalDateTime.now());
        outbox.setUpdateTime(LocalDateTime.now());
        userEventOutboxMapper.insert(outbox);

        log.info("[用户注册] outbox 已写入: eventId={}", payload.get("eventId"));

        return user;
    }

    @Override
    public User getByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .eq(User::getIsDeleted, 0)
        );
    }


    // ==================== QQ 第三方登录 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User getOrCreateQqUser(String openId, String nickname, String avatar) {
        // 1. 尝试查找已有用户
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getOpenId, openId)
                        .eq(User::getIsDeleted, 0)
        );
        if (existing != null) {
            // 更新昵称和头像（可能变化）
            boolean updated = false;
            if (nickname != null && !nickname.equals(existing.getQqNickname())) {
                existing.setQqNickname(nickname);
                updated = true;
            }
            if (avatar != null && !avatar.equals(existing.getQqAvatar())) {
                existing.setQqAvatar(avatar);
                updated = true;
            }
            if (updated) {
                existing.setUpdateTime(LocalDateTime.now());
                userMapper.updateById(existing);
                log.info("[QQ登录] 用户信息已更新: userId={}, openId={}", existing.getId(), openId);
            }
            return existing;
        }

        // 2. 不存在则创建新用户
        String username = "qq_" + openId.substring(0, Math.min(8, openId.length()));
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(null);  // QQ 用户无密码
        newUser.setNickname(nickname != null ? nickname : username);
        newUser.setOpenId(openId);
        newUser.setQqNickname(nickname);
        newUser.setQqAvatar(avatar);
        newUser.setStatus(0);
        newUser.setCreateTime(LocalDateTime.now());
        newUser.setUpdateTime(LocalDateTime.now());
        userMapper.insert(newUser);
        log.info("[QQ登录] 新用户注册: userId={}, username={}, openId={}", newUser.getId(), username, openId);
        return newUser;
    }

    // ==================== B 端（管理后台） ====================

    @Override
    public AdminUserPageResp adminGetPage(AdminUserListQuery query) {
        int page = query.getPage() == null ? 1 : query.getPage();
        int size = query.getSize() == null ? 10 : query.getSize();

        Page<User> pageParam = new Page<>(page, size);
        pageParam.addOrder(OrderItem.desc("create_time"));

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getIsDeleted, 0);
        if (query.getStatus() != null) {
            wrapper.eq(User::getStatus, query.getStatus());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = query.getKeyword();
            wrapper.and(w -> w.like(User::getUsername, kw)
                    .or().like(User::getNickname, kw)
                    .or().like(User::getPhone, kw)
                    .or().like(User::getEmail, kw));
        }

        Page<User> pageResult = userMapper.selectPage(pageParam, wrapper);

        // 转换为 AdminUserVO，避免返回 password 字段
        List<AdminUserVO> voList = pageResult.getRecords().stream()
                .map(this::toAdminUserVO)
                .collect(Collectors.toList());

        AdminUserPageResp resp = new AdminUserPageResp();
        resp.setTotal(pageResult.getTotal());
        resp.setPage(page);
        resp.setSize(size);
        resp.setRecords(voList);
        return resp;
    }

    @Override
    public User adminGetDetail(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getIsDeleted() == 1) {
            throw new BizException(UserErrorCode.PARAM_INVALID, "用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminChangeStatus(Long userId, UserStatusReq req, Long adminId, String adminName) {
        Integer target = req.getStatus();
        if (target == null || (target != 0 && target != 1)) {
            throw new BizException(UserErrorCode.PARAM_INVALID, "状态值必须为 0 或 1");
        }

        User exist = userMapper.selectById(userId);
        if (exist == null || exist.getIsDeleted() == 1) {
            throw new BizException(UserErrorCode.PARAM_INVALID, "用户不存在");
        }

        Integer previous = exist.getStatus();
        if (previous.equals(target)) {
            log.info("[管理员改用户状态-无操作] adminId={}, userId={}, status={}",
                    adminId, userId, target);
            return;
        }

        exist.setStatus(target);
        userMapper.updateById(exist);

        // 冻结时强制踢下线
        if (target == 1) {
            tokenBlacklist.kickOut(userId);
        }

        log.warn("[管理员改用户状态] adminId={}({}), userId={}, username={}, from={} to={}",
                adminId, adminName, userId, exist.getUsername(), previous, target);
    }

    /**
     * 将 User 实体转换为 AdminUserVO（排除 password 字段）
     */
    private AdminUserVO toAdminUserVO(User user) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }

    private String generateEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

