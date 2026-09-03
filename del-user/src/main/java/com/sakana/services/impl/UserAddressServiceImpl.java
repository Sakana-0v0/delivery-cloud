package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakana.dao.entity.User;
import com.sakana.dao.entity.UserAddress;
import com.sakana.dao.mapper.UserAddressMapper;
import com.sakana.dao.mapper.UserMapper;
import com.sakana.services.impl.UserErrorCode;
import com.sakana.exceptions.BizException;
import com.sakana.services.UserAddressService;
import com.sakana.web.vo.AdminAddressPageResp;
import com.sakana.web.vo.AdminAddressVO;
import com.sakana.web.vo.UserAddressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收货地址服务实现（C 端 + 管理后台）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress> implements UserAddressService {

    private final UserMapper userMapper;

    // ==================== C 端 ====================

    @Override
    public List<UserAddressVO> listByUser(Long userId) {
        List<UserAddress> list = lambdaQuery()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDeleted, 0)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getCreateTime)
                .list();
        if (list.isEmpty()) return List.of();
        return toVOList(list, null);
    }

    @Override
    public UserAddressVO getByUserAndId(Long userId, Long addressId) {
        UserAddress addr = getById(addressId);
        if (addr == null || addr.getIsDeleted() == 1 || !addr.getUserId().equals(userId)) {
            throw new BizException(UserErrorCode.ADDRESS_NOT_FOUND);
        }
        return toVO(addr, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAddress(Long userId, UserAddressVO req) {
        UserAddress addr = new UserAddress();
        BeanUtils.copyProperties(req, addr);
        addr.setId(null);
        addr.setUserId(userId);
        addr.setIsDefault(req.getIsDefault() == null ? 0 : req.getIsDefault());

        // 若设为默认，先清掉该用户其他默认
        if (addr.getIsDefault() == 1) {
            clearDefault(userId);
        }
        save(addr);

        log.info("[收货地址创建] userId={}, addressId={}", userId, addr.getId());
        return addr.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAddress(Long userId, Long addressId, UserAddressVO req) {
        UserAddress exist = getById(addressId);
        if (exist == null || exist.getIsDeleted() == 1 || !exist.getUserId().equals(userId)) {
            throw new BizException(UserErrorCode.ADDRESS_NOT_FOUND);
        }
        BeanUtils.copyProperties(req, exist, "id", "userId", "createTime");
        if (req.getIsDefault() != null && req.getIsDefault() == 1) {
            clearDefault(userId);
        }
        updateById(exist);
        log.info("[收货地址修改] userId={}, addressId={}", userId, addressId);
    }

    @Override
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress exist = getById(addressId);
        if (exist == null || exist.getIsDeleted() == 1 || !exist.getUserId().equals(userId)) {
            throw new BizException(UserErrorCode.ADDRESS_NOT_FOUND);
        }
        removeById(addressId);
        log.info("[收货地址删除] userId={}, addressId={}", userId, addressId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long addressId) {
        UserAddress exist = getById(addressId);
        if (exist == null || exist.getIsDeleted() == 1 || !exist.getUserId().equals(userId)) {
            throw new BizException(UserErrorCode.ADDRESS_NOT_FOUND);
        }
        clearDefault(userId);
        exist.setIsDefault(1);
        updateById(exist);
        log.info("[设置默认地址] userId={}, addressId={}", userId, addressId);
    }

    // ==================== 管理后台 ====================

    @Override
    public AdminAddressPageResp adminGetPage(Long userId, String keyword, int page, int size) {
        LambdaQueryWrapper<UserAddress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(userId != null, UserAddress::getUserId, userId)
               .eq(UserAddress::getIsDeleted, 0);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(UserAddress::getReceiver, keyword)
                             .or()
                             .like(UserAddress::getPhone, keyword));
        }

        wrapper.orderByDesc(UserAddress::getCreateTime);

        Page<UserAddress> addressPage = page(new Page<>(page, size), wrapper);

        // 批量查询用户信息
        Set<Long> userIds = addressPage.getRecords().stream()
                .map(UserAddress::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<AdminAddressVO> records = addressPage.getRecords().stream()
                .map(addr -> toAdminVO(addr, userMap.get(addr.getUserId())))
                .collect(Collectors.toList());

        AdminAddressPageResp result = new AdminAddressPageResp();
        result.setTotal(addressPage.getTotal());
        result.setPage((int) addressPage.getCurrent());
        result.setSize((int) addressPage.getSize());
        result.setRecords(records);

        return result;
    }

    @Override
    public void adminDeleteAddress(Long addressId) {
        UserAddress exist = getById(addressId);
        if (exist == null || exist.getIsDeleted() == 1) {
            throw new BizException(UserErrorCode.ADDRESS_NOT_FOUND);
        }
        removeById(addressId);
        log.info("[管理后台删除收货地址] addressId={}, userId={}", addressId, exist.getUserId());
    }

    @Override
    public UserAddressVO getAddressById(Long addressId) {
        UserAddress addr = getById(addressId);
        if (addr == null || addr.getIsDeleted() == 1) {
            return null;
        }
        return toVO(addr, null);
    }

    // ==================== 私有方法 ====================

    private void clearDefault(Long userId) {
        lambdaUpdate()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1)
                .set(UserAddress::getIsDefault, 0)
                .update();
    }

    private List<UserAddressVO> toVOList(List<UserAddress> list, Map<Long, User> userMap) {
        List<UserAddressVO> result = new ArrayList<>(list.size());
        for (UserAddress addr : list) {
            User u = userMap != null ? userMap.get(addr.getUserId()) : null;
            result.add(toVO(addr, u));
        }
        return result;
    }

    private UserAddressVO toVO(UserAddress addr, User user) {
        UserAddressVO vo = new UserAddressVO();
        BeanUtils.copyProperties(addr, vo);
        StringBuilder sb = new StringBuilder();
        if (addr.getProvince() != null) sb.append(addr.getProvince());
        if (addr.getCity() != null) sb.append(addr.getCity());
        if (addr.getDistrict() != null) sb.append(addr.getDistrict());
        if (addr.getDetail() != null) sb.append(addr.getDetail());
        vo.setFullAddress(sb.toString());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
        }
        return vo;
    }

    private AdminAddressVO toAdminVO(UserAddress addr, User user) {
        AdminAddressVO vo = new AdminAddressVO();
        BeanUtils.copyProperties(addr, vo);

        StringBuilder sb = new StringBuilder();
        if (addr.getProvince() != null) sb.append(addr.getProvince());
        if (addr.getCity() != null) sb.append(addr.getCity());
        if (addr.getDistrict() != null) sb.append(addr.getDistrict());
        if (addr.getDetail() != null) sb.append(addr.getDetail());
        vo.setFullAddress(sb.toString());

        if (user != null) {
            vo.setUsername(user.getUsername());
        }
        return vo;
    }
}