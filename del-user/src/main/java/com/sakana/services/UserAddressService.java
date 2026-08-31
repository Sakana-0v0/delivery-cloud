package com.sakana.services;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakana.dao.entity.UserAddress;
import com.sakana.web.vo.AdminAddressPageResp;
import com.sakana.web.vo.UserAddressVO;

import java.util.List;

/**
 * 收货地址服务（C 端 + 管理后台）
 */
public interface UserAddressService extends IService<UserAddress> {

    // ==================== C 端 ====================

    /**
     * 查询用户的所有收货地址
     */
    List<UserAddressVO> listByUser(Long userId);

    /**
     * 查询用户单个地址（带所有权校验）
     */
    UserAddressVO getByUserAndId(Long userId, Long addressId);

    /**
     * 新增收货地址
     */
    Long createAddress(Long userId, UserAddressVO req);

    /**
     * 修改收货地址
     */
    void updateAddress(Long userId, Long addressId, UserAddressVO req);

    /**
     * 删除收货地址
     */
    void deleteAddress(Long userId, Long addressId);

    /**
     * 设为默认地址
     */
    void setDefault(Long userId, Long addressId);

    // ==================== 管理后台 ====================

    /**
     * 管理后台：收货地址分页查询
     */
    AdminAddressPageResp adminGetPage(Long userId, String keyword, int page, int size);

    /**
     * 管理后台：强制删除收货地址
     */
    void adminDeleteAddress(Long addressId);
}
