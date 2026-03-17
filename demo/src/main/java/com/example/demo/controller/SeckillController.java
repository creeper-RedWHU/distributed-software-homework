package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.model.vo.SeckillProductVO;
import com.example.demo.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * 秒杀商品列表
     * GET /api/seckill/list
     */
    @GetMapping("/list")
    public Result<List<SeckillProductVO>> getSeckillList() {
        log.info("[端口:{}] 查询秒杀列表", serverPort);
        return seckillService.getSeckillList();
    }

    /**
     * 秒杀商品详情
     * GET /api/seckill/{id}
     */
    @GetMapping("/{id}")
    public Result<SeckillProductVO> getSeckillDetail(@PathVariable Long id) {
        log.info("[端口:{}] 查询秒杀详情: seckillId={}", serverPort, id);
        return seckillService.getSeckillDetail(id);
    }

    /**
     * 执行秒杀
     * POST /api/seckill/do?userId=1&seckillId=1
     */
    @PostMapping("/do")
    public Result<Long> doSeckill(@RequestParam Long userId, @RequestParam Long seckillId) {
        log.info("[端口:{}] 执行秒杀: userId={}, seckillId={}", serverPort, userId, seckillId);
        return seckillService.doSeckill(userId, seckillId);
    }

    /**
     * 预热秒杀库存
     * POST /api/seckill/{id}/warm-stock
     */
    @PostMapping("/{id}/warm-stock")
    public Result<String> warmStock(@PathVariable Long id) {
        seckillService.warmUpSeckillStock(id);
        return Result.success("秒杀库存已预热");
    }
}
