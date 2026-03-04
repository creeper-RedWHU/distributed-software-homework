package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.model.dto.ActivityCreateRequest;
import com.example.demo.model.dto.ProductCreateRequest;
import com.example.demo.model.entity.Product;
import com.example.demo.model.entity.SeckillActivity;
import com.example.demo.model.vo.StockCheckVO;
import com.example.demo.service.OrderService;
import com.example.demo.service.ProductService;
import com.example.demo.service.SeckillService;
import com.example.demo.service.StockService;
import com.example.demo.mapper.SeckillActivityMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final SeckillService seckillService;
    private final StockService stockService;
    private final OrderService orderService;
    private final SeckillActivityMapper activityMapper;

    // ===== 商品管理 =====

    @PostMapping("/products")
    public Result<Product> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return Result.success(productService.create(request));
    }

    @PatchMapping("/products/{productId}/status")
    public Result<Void> updateProductStatus(@PathVariable Long productId,
                                            @RequestBody Map<String, Integer> body) {
        productService.updateStatus(productId, body.get("status"));
        return Result.success();
    }

    // ===== 秒杀活动管理 =====

    @PostMapping("/seckill/activities")
    public Result<SeckillActivity> createActivity(@Valid @RequestBody ActivityCreateRequest request) {
        return Result.success(seckillService.createActivity(request));
    }

    @PostMapping("/seckill/activities/{activityId}/cancel")
    public Result<Void> cancelActivity(@PathVariable Long activityId) {
        seckillService.cancelActivity(activityId);
        return Result.success();
    }

    /**
     * 手动触发库存预热
     */
    @PostMapping("/seckill/activities/{activityId}/warmup")
    public Result<Void> warmupStock(@PathVariable Long activityId) {
        stockService.warmupStock(activityId);
        return Result.success();
    }

    /**
     * 库存对账
     */
    @GetMapping("/seckill/activities/{activityId}/stock-check")
    public Result<StockCheckVO> stockCheck(@PathVariable Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }

        Integer redisStock = stockService.getRedisStock(activityId);
        int mysqlStock = activity.getAvailableStock();
        int orderCount = orderService.countByActivityId(activityId);
        int soldCount = activity.getTotalStock() - mysqlStock;

        StockCheckVO vo = new StockCheckVO();
        vo.setActivityId(activityId);
        vo.setRedisStock(redisStock);
        vo.setMysqlStock(mysqlStock);
        vo.setConsistent(redisStock != null && redisStock.equals(mysqlStock));
        vo.setSoldCount(soldCount);
        vo.setOrderCount(orderCount);
        vo.setCheckedAt(LocalDateTime.now());

        return Result.success(vo);
    }
}
