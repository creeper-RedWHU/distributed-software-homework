package com.example.cloudlab.gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class FallbackController {

    @GetMapping("/fallback/stock")
    public Map<String, Object> stockFallback() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "gateway-service");
        data.put("message", "gateway circuit breaker fallback");
        data.put("time", LocalDateTime.now().toString());
        return data;
    }
}
