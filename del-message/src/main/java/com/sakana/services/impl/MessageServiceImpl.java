package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.dao.entity.Message;
import com.sakana.dao.mapper.MessageMapper;
import com.sakana.services.MessageService;
import com.sakana.web.vo.MessagePageResp;
import com.sakana.web.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;

    @Override
    public MessagePageResp listByUser(Long userId, Integer isRead, int page, int size) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getUserId, userId)
                .eq(Message::getIsDeleted, 0);
        if (isRead != null) {
            wrapper.eq(Message::getIsRead, isRead);
        }
        wrapper.orderByDesc(Message::getCreateTime);

        Page<Message> pg = new Page<>(page, size);
        Page<Message> result = messageMapper.selectPage(pg, wrapper);

        MessagePageResp resp = new MessagePageResp();
        resp.setTotal(result.getTotal());
        resp.setPage((long) page);
        resp.setSize((long) size);
        resp.setRecords(toVOList(result.getRecords()));
        return resp;
    }

    @Override
    public long getUnreadCount(Long userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0)
                .eq(Message::getIsDeleted, 0));
    }

    @Override
    public void markRead(Long userId, Long messageId) {
        Message message = messageMapper.selectOne(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getId, messageId)
                        .eq(Message::getUserId, userId)
                        .eq(Message::getIsDeleted, 0)
        );
        if (message != null && message.getIsRead() == 0) {
            message.setIsRead(1);
            messageMapper.updateById(message);
            log.info("[消息] 已标记已读: messageId={}, userId={}", messageId, userId);
        }
    }

    @Override
    public void markAllRead(Long userId) {
        Message update = new Message();
        update.setIsRead(1);
        messageMapper.update(update, new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0)
                .eq(Message::getIsDeleted, 0));
        log.info("[消息] 全部已读: userId={}", userId);
    }

    @Override
    public MessageVO saveMessage(Long userId, String type, String title, String content, String bizId) {
        Message m = new Message();
        m.setUserId(userId);
        m.setType(type);
        m.setTitle(title);
        m.setContent(content);
        m.setIsRead(0);
        m.setBizId(bizId);
        messageMapper.insert(m);
        log.info("[消息写入] userId={}, type={}, title={}, bizId={}", userId, type, title, bizId);
        return toVO(m);
    }

    @Override
    public List<MessageVO> adminListAll(Long userId, String type, Integer isRead, int page, int size) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) wrapper.eq(Message::getUserId, userId);
        if (type != null && !type.isBlank()) wrapper.eq(Message::getType, type);
        if (isRead != null) wrapper.eq(Message::getIsRead, isRead);
        wrapper.orderByDesc(Message::getCreateTime);

        Page<Message> pg = new Page<>(page, size);
        Page<Message> result = messageMapper.selectPage(pg, wrapper);
        return toVOList(result.getRecords());
    }

    private List<MessageVO> toVOList(List<Message> records) {
        List<MessageVO> list = new ArrayList<>(records.size());
        for (Message m : records) list.add(toVO(m));
        return list;
    }

    private MessageVO toVO(Message m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setUserId(m.getUserId());
        vo.setType(m.getType());
        vo.setTitle(m.getTitle());
        vo.setContent(m.getContent());
        vo.setIsRead(m.getIsRead());
        vo.setBizId(m.getBizId());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }
}
