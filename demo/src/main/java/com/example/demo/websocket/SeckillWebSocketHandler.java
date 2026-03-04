package com.example.demo.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 秒杀 WebSocket 处理器
 *
 * 连接URL: ws://host/ws/seckill?userId={userId}&activityId={activityId}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Long userId = (Long) attrs.get("userId");
        Long activityId = (Long) attrs.get("activityId");
        sessionManager.register(userId, activityId, session);
        log.info("WebSocket 连接建立: userId={}, activityId={}", userId, activityId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端可以发送心跳 ping
        log.debug("收到客户端消息: {}", message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Map<String, Object> attrs = session.getAttributes();
        Long userId = (Long) attrs.get("userId");
        Long activityId = (Long) attrs.get("activityId");
        sessionManager.unregister(userId, activityId, session);
        log.info("WebSocket 连接关闭: userId={}, status={}", userId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 传输错误: sessionId={}", session.getId(), exception);
    }
}
