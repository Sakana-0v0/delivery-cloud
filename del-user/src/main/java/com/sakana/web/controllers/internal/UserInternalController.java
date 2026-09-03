package com.sakana.web.controllers.internal;

import com.sakana.dao.entity.User;
import com.sakana.dao.mapper.UserMapper;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户内部接口（供其他服务调用获取用户联系信息）
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserMapper userMapper;

    /**
     * 获取用户联系信息（用户名 + 邮箱），供订单等服务使用
     */
    @GetMapping("/{id}/contact")
    @Operation(summary = "获取用户联系信息（内部）")
    public R<UserContactVO> getContact(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return R.fail(404, "用户不存在");
        }
        UserContactVO vo = new UserContactVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        return R.ok(vo);
    }
}
