package com.example.cloudlab.stock.controller;

import com.example.cloudlab.stock.config.DemoProperties;
import com.example.cloudlab.stock.service.StockProbeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RefreshScope
public class StockController {

    private final DemoProperties demoProperties;
    private final StockProbeService stockProbeService;

    @Value("${server.port}")
    private String serverPort;

    public StockController(DemoProperties demoProperties, StockProbeService stockProbeService) {
        this.demoProperties = demoProperties;
        this.stockProbeService = stockProbeService;
    }

    @GetMapping("/api/stocks/echo")
    public Map<String, Object> echo(@RequestParam(defaultValue = "gateway") String from) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "stock-service");
        data.put("servedBy", serverPort);
        data.put("from", from);
        data.put("message", demoProperties.getMessage());
        data.put("owner", demoProperties.getOwner());
        data.put("time", LocalDateTime.now().toString());
        return data;
    }

    @GetMapping("/api/stocks/config")
    public Map<String, Object> config() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", demoProperties.getMessage());
        data.put("owner", demoProperties.getOwner());
        data.put("servedBy", serverPort);
        return data;
    }

    @GetMapping("/api/stocks/hot")
    public Map<String, Object> hot(@RequestParam(defaultValue = "stock-node") String instanceId) {
        return stockProbeService.hotspot(instanceId);
    }

    @GetMapping("/api/stocks/slow")
    public Map<String, Object> slow(@RequestParam(defaultValue = "1500") long delayMs) throws InterruptedException {
        return stockProbeService.slow(delayMs, serverPort);
    }
}
