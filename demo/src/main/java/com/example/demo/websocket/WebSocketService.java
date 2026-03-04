package com.example.demo.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * WebSocket 推送服务 — 统一推送接口
 * Event 监听器通过此服务推送消息给客户端
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final WebSocketSessionManager sessionManager;

    /**
     * 发送消息给指定用户
     */
    public void sendToUser(Long userId, Map<String, Object> data) {
        data.put("timestamp", System.currentTimeMillis());
        sessionManager.sendToUser(userId, data);
    }

    /**
     * 广播给活动的所有在线用户
     */
    public void broadcastToActivity(Long activityId, Map<String, Object> data) {
        data.put("timestamp", System.currentTimeMillis());
        sessionManager.broadcastToActivity(activityId, data);
    }
}
