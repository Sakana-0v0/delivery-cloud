package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.dao.entity.Message;
import com.sakana.dao.mapper.MessageMapper;
import com.sakana.web.vo.MessageVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MessageServiceImpl 业务测试
 *
 * <p>覆盖：写入消息、标记已读、全部已读、未读数。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class MessageServiceImplTest {

    @Mock
    private MessageMapper messageMapper;

    private MessageServiceImpl service;

    private Message newMessage(Long id, Long userId, String type, int isRead) {
        Message m = new Message();
        m.setId(id);
        m.setUserId(userId);
        m.setType(type);
        m.setTitle("测试消息");
        m.setContent("内容");
        m.setIsRead(isRead);
        m.setBizId("BIZ" + id);
        return m;
    }

    private MessageServiceImpl newService() {
        // 通过 @RequiredArgsConstructor 构造注入 mock mapper
        return new MessageServiceImpl(messageMapper);
    }

    // ==================== saveMessage ====================

    @Test
    void saveMessage_returnsVoWithId() {
        MessageServiceImpl svc = newService();
        when(messageMapper.insert(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(100L);
            return 1;
        });

        MessageVO vo = svc.saveMessage(1L, "order", "订单已支付", "您的订单... ", "ORD20260101");
        assertNotNull(vo);
        assertEquals(100L, vo.getId());
        assertEquals("order", vo.getType());
        assertEquals("订单已支付", vo.getTitle());

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insert(captor.capture());
        assertEquals(Integer.valueOf(0), captor.getValue().getIsRead(), "新消息默认未读");
    }

    // ==================== markRead ====================

    @Test
    void markRead_ownerAndUnread_updates() {
        MessageServiceImpl svc = newService();
        Message m = newMessage(1L, 100L, "order", 0);
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(m);

        svc.markRead(100L, 1L);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(1), captor.getValue().getIsRead());
    }

    @Test
    void markRead_otherUser_noOp() {
        MessageServiceImpl svc = newService();
        // 模拟 mapper 真实行为：selectOne 带 userId 条件，userId 不匹配时返回 null
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        svc.markRead(100L, 1L);

        verify(messageMapper, never()).updateById(any(Message.class));
    }

    @Test
    void markRead_alreadyRead_noOp() {
        MessageServiceImpl svc = newService();
        Message m = newMessage(1L, 100L, "order", 1);  // 已是已读
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(m);

        svc.markRead(100L, 1L);

        verify(messageMapper, never()).updateById(any(Message.class));
    }

    // ==================== markAllRead ====================

    @Test
    void markAllRead_updatesAllUnreadForUser() {
        MessageServiceImpl svc = newService();
        when(messageMapper.update(any(Message.class), any(LambdaQueryWrapper.class))).thenReturn(3);

        svc.markAllRead(100L);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messageMapper).update(captor.capture(), wrapperCaptor.capture());
        assertEquals(Integer.valueOf(1), captor.getValue().getIsRead());
    }

    // ==================== getUnreadCount ====================

    @Test
    void getUnreadCount_returnsCountFromDb() {
        MessageServiceImpl svc = newService();
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        long count = svc.getUnreadCount(100L);
        assertEquals(5L, count);
    }

    @Test
    void getUnreadCount_empty_returnsZero() {
        MessageServiceImpl svc = newService();
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertEquals(0L, svc.getUnreadCount(100L));
    }
}
