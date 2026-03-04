package com.example.demo.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器
 * 管理用户会话和活动订阅关系
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** userId -> Session（点对点推送） */
    private final ConcurrentHashMap<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    /** activityId -> Set<Session>（广播推送） */
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> activitySessions = new ConcurrentHashMap<>();

    public void register(Long userId, Long activityId, WebSocketSession session) {
        if (userId != null) {
            userSessions.put(userId, session);
        }
        if (activityId != null) {
            activitySessions.computeIfAbsent(activityId, k -> ConcurrentHashMap.newKeySet()).add(session);
        }
        log.debug("WebSocket 注册: userId={}, activityId={}, sessionId={}", userId, activityId, session.getId());
    }

    public void unregister(Long userId, Long activityId, WebSocketSession session) {
        if (userId != null) {
            userSessions.remove(userId);
        }
        if (activityId != null) {
            Set<WebSocketSession> sessions = activitySessions.get(activityId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    activitySessions.remove(activityId);
                }
            }
        }
        log.debug("WebSocket 注销: userId={}, activityId={}", userId, activityId);
    }

    public void sendToUser(Long userId, Object message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            sendMessage(session, message);
        }
    }

    public void broadcastToActivity(Long activityId, Object message) {
        Set<WebSocketSession> sessions = activitySessions.get(activityId);
        if (sessions == null) return;

        String payload = toJson(message);
        if (payload == null) return;

        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (IOException e) {
                    log.error("WebSocket 广播失败: sessionId={}", session.getId(), e);
                }
            }
        });
    }

    private void sendMessage(WebSocketSession session, Object message) {
        try {
            String payload = toJson(message);
            if (payload != null) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.error("WebSocket 发送失败: sessionId={}", session.getId(), e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON 序列化失败", e);
            return null;
        }
    }

    public int getOnlineCount() {
        return userSessions.size();
    }
}
