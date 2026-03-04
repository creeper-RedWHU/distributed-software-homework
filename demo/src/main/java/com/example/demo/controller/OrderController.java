package com.example.demo.controller;

import com.example.demo.common.ErrorCode;
import com.example.demo.common.Result;
import com.example.demo.hook.HookContext;
import com.example.demo.hook.HookManager;
import com.example.demo.hook.HookPoint;
import com.example.demo.hook.HookResult;
import com.example.demo.model.entity.Order;
import com.example.demo.model.vo.OrderVO;
import com.example.demo.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final HookManager hookManager;

    /**
     * 查询我的订单
     */
    @GetMapping
    public Result<List<OrderVO>> listOrders(
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Order> orders = orderService.getByUserId(userId, status, page, size);
        List<OrderVO> voList = orders.stream().map(orderService::toVO).collect(Collectors.toList());
        return Result.success(voList);
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderNo}")
    public Result<OrderVO> getOrder(@PathVariable String orderNo) {
        Order order = orderService.getByOrderNo(orderNo);
        if (order == null) {
            return Result.fail(ErrorCode.ORDER_NOT_FOUND);
        }
        return Result.success(orderService.toVO(order));
    }

    /**
     * 支付订单
     */
    @PostMapping("/{orderNo}/pay")
    public Result<Map<String, Object>> payOrder(@PathVariable String orderNo) {
        HookContext ctx = new HookContext();
        ctx.put("orderNo", orderNo);
        HookResult hookResult = hookManager.executeHooks(HookPoint.BEFORE_ORDER_PAY, ctx);
        if (!hookResult.isProceed()) {
            return Result.fail(ErrorCode.ORDER_STATUS_ERROR.getCode(), hookResult.getMessage());
        }

        boolean success = orderService.payOrder(orderNo);
        if (!success) {
            return Result.fail(ErrorCode.ORDER_STATUS_ERROR);
        }

        hookManager.executeHooks(HookPoint.AFTER_ORDER_PAY, ctx);
        return Result.success(Map.of("orderNo", orderNo, "status", "PAID"));
    }

    /**
     * 取消订单
     */
    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancelOrder(@PathVariable String orderNo) {
        HookContext ctx = new HookContext();
        ctx.put("orderNo", orderNo);
        HookResult hookResult = hookManager.executeHooks(HookPoint.BEFORE_ORDER_CANCEL, ctx);
        if (!hookResult.isProceed()) {
            return Result.fail(ErrorCode.ORDER_STATUS_ERROR.getCode(), hookResult.getMessage());
        }

        boolean success = orderService.cancelOrder(orderNo);
        if (!success) {
            return Result.fail(ErrorCode.ORDER_STATUS_ERROR);
        }

        hookManager.executeHooks(HookPoint.AFTER_ORDER_CANCEL, ctx);
        return Result.success();
    }
}
