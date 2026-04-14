package com.example.cloudlab.stock.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StockProbeService {

    @SentinelResource(value = "stockHotspot", blockHandler = "handleBlock", fallback = "handleFallback")
    public Map<String, Object> hotspot(String instanceId) {
        if ("fail".equalsIgnoreCase(instanceId)) {
            throw new IllegalStateException("manual failure for fallback");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", "hotspot");
        data.put("instanceId", instanceId);
        data.put("time", LocalDateTime.now().toString());
        return data;
    }

    public Map<String, Object> slow(long delayMs, String serverPort) throws InterruptedException {
        Thread.sleep(delayMs);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", "slow");
        data.put("delayMs", delayMs);
        data.put("servedBy", serverPort);
        data.put("time", LocalDateTime.now().toString());
        return data;
    }

    public Map<String, Object> handleBlock(String instanceId, BlockException exception) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", "blocked");
        data.put("instanceId", instanceId);
        data.put("rule", exception.getRule());
        data.put("message", "sentinel flow control triggered");
        return data;
    }

    public Map<String, Object> handleFallback(String instanceId, Throwable throwable) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", "fallback");
        data.put("instanceId", instanceId);
        data.put("message", throwable.getMessage());
        return data;
    }
}
