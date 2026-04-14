package com.example.cloudlab.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;

@Component
public class GatewayBlockHandlers {

    public BlockRequestHandler jsonBlockHandler() {
        return (exchange, throwable) -> ServerResponse
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":429,\"message\":\"gateway rate limit triggered\"}");
    }
}
