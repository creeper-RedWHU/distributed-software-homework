package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.model.entity.SeckillOrder;
import com.example.demo.model.vo.SeckillProductVO;
import com.example.demo.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @Value("${server.port:8080}")
    private String serverPort;

    @GetMapping("/list")
    public Result<List<SeckillProductVO>> getSeckillList() {
        log.info("[端口:{}] 查询秒杀列表", serverPort);
        return seckillService.getSeckillList();
    }

    @GetMapping("/{id}")
    public Result<SeckillProductVO> getSeckillDetail(@PathVariable Long id) {
        log.info("[端口:{}] 查询秒杀详情: seckillId={}", serverPort, id);
        return seckillService.getSeckillDetail(id);
    }

    /**
     * 执行秒杀（Kafka异步下单）
     * 返回orderId，客户端轮询 /api/seckill/order/status/{orderId} 查询结果
     */
    @PostMapping("/do")
    public Result<Long> doSeckill(@RequestParam Long userId, @RequestParam Long seckillId) {
        log.info("[端口:{}] 执行秒杀: userId={}, seckillId={}", serverPort, userId, seckillId);
        return seckillService.doSeckill(userId, seckillId);
    }

    /**
     * 查询订单处理状态（轮询接口）
     */
    @GetMapping("/order/status/{orderId}")
    public Result<Map<String, Object>> getOrderStatus(@PathVariable Long orderId) {
        return seckillService.getOrderStatus(orderId);
    }

    /**
     * 按用户ID查询订单列表
     */
    @GetMapping("/order/user/{userId}")
    public Result<List<SeckillOrder>> getOrdersByUser(@PathVariable Long userId) {
        log.info("[端口:{}] 查询用户订单: userId={}", serverPort, userId);
        return seckillService.getOrdersByUserId(userId);
    }

    /**
     * 按订单ID查询订单详情
     */
    @GetMapping("/order/{orderId}")
    public Result<SeckillOrder> getOrderById(@PathVariable Long orderId) {
        log.info("[端口:{}] 查询订单详情: orderId={}", serverPort, orderId);
        return seckillService.getOrderById(orderId);
    }

    @PostMapping("/order/{orderId}/pay")
    public Result<String> payOrder(@PathVariable Long orderId, @RequestParam Long userId) {
        log.info("[端口:{}] 支付订单: orderId={}, userId={}", serverPort, orderId, userId);
        return seckillService.payOrder(userId, orderId);
    }

    @PostMapping("/{id}/warm-stock")
    public Result<String> warmStock(@PathVariable Long id) {
        seckillService.warmUpSeckillStock(id);
        return Result.success("秒杀库存已预热");
    }
}
