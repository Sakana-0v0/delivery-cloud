package com.sakana.services;

import com.sakana.web.vo.MessagePageResp;
import com.sakana.web.vo.MessageVO;

import java.util.List;

/**
 * 消息服务接口
 */
public interface MessageService {

    // ==================== C 端 ====================

    /**
     * 查询用户消息（分页）
     */
    MessagePageResp listByUser(Long userId, Integer isRead, int page, int size);

    /**
     * 未读消息数
     */
    long getUnreadCount(Long userId);

    /**
     * 标记单条已读
     */
    void markRead(Long userId, Long messageId);

    /**
     * 全部已读
     */
    void markAllRead(Long userId);

    // ==================== 内部 / MQ 消费 ====================

    /**
     * 写入一条站内消息（其他服务 / MQ 消费调用）
     */
    MessageVO saveMessage(Long userId, String type, String title, String content, String bizId);

    /**
     * 全量列表（管理后台）
     */
    List<MessageVO> adminListAll(Long userId, String type, Integer isRead, int page, int size);
}
