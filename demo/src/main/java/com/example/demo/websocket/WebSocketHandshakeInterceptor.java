package com.example.demo.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器 — 解析 URL 参数并存入 Session Attributes
 */
@Slf4j
@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String userIdStr = servletRequest.getServletRequest().getParameter("userId");
            String activityIdStr = servletRequest.getServletRequest().getParameter("activityId");

            if (userIdStr != null) {
                attributes.put("userId", Long.parseLong(userIdStr));
            }
            if (activityIdStr != null) {
                attributes.put("activityId", Long.parseLong(activityIdStr));
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
