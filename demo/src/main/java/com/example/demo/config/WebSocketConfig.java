package com.example.demo.config;

import com.example.demo.websocket.SeckillWebSocketHandler;
import com.example.demo.websocket.WebSocketHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final SeckillWebSocketHandler seckillHandler;
    private final WebSocketHandshakeInterceptor interceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(seckillHandler, "/ws/seckill")
                .addInterceptors(interceptor)
                .setAllowedOrigins("*");
    }
}
