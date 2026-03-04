package com.example.demo.controller;

import com.example.demo.common.ErrorCode;
import com.example.demo.common.Result;
import com.example.demo.hook.HookContext;
import com.example.demo.hook.HookManager;
import com.example.demo.hook.HookPoint;
import com.example.demo.hook.HookResult;
import com.example.demo.model.dto.SeckillRequest;
import com.example.demo.model.vo.SeckillActivityVO;
import com.example.demo.model.vo.SeckillResultVO;
import com.example.demo.service.OrderService;
import com.example.demo.service.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;
    private final OrderService orderService;
    private final HookManager hookManager;

    /**
     * 获取秒杀活动列表
     */
    @GetMapping("/activities")
    public Result<List<SeckillActivityVO>> listActivities(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(seckillService.listActivities(status, page, size));
    }

    /**
     * 获取秒杀活动详情
     */
    @GetMapping("/activities/{activityId}")
    public Result<SeckillActivityVO> getActivity(@PathVariable Long activityId) {
        SeckillActivityVO vo = seckillService.getActivityDetail(activityId);
        if (vo == null) {
            return Result.fail(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        return Result.success(vo);
    }

    /**
     * 执行秒杀 — 核心接口
     */
    @PostMapping("/execute")
    public Result<SeckillResultVO> execute(@Valid @RequestBody SeckillRequest request,
                                           @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId) {
        // Hook: BEFORE_SECKILL_EXECUTE
        HookContext ctx = new HookContext();
        ctx.put("activityId", request.getActivityId());
        ctx.put("userId", userId);
        HookResult hookResult = hookManager.executeHooks(HookPoint.BEFORE_SECKILL_EXECUTE, ctx);
        if (!hookResult.isProceed()) {
            return Result.fail(ErrorCode.SECKILL_HOOK_BLOCKED.getCode(), hookResult.getMessage());
        }

        // 执行秒杀
        Result<SeckillResultVO> result = seckillService.execute(request.getActivityId(), userId);

        // Hook: AFTER_SECKILL_EXECUTE
        if (result.getCode() == 200) {
            ctx.put("orderNo", result.getData().getOrderNo());
            hookManager.executeHooks(HookPoint.AFTER_SECKILL_EXECUTE, ctx);
        }

        return result;
    }

    /**
     * 查询秒杀结果（轮询备用，优先使用 WebSocket）
     */
    @GetMapping("/result")
    public Result<SeckillResultVO> getResult(@RequestParam Long activityId,
                                             @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId) {
        // 查询用户在该活动下的订单
        var orders = orderService.getByUserId(userId, null, 1, 1);
        for (var order : orders) {
            if (order.getActivityId().equals(activityId)) {
                String status = order.getStatus() == 0 || order.getStatus() == 1 ? "SUCCESS" : "FAILED";
                return Result.success(new SeckillResultVO(order.getOrderNo(), status));
            }
        }
        return Result.success(new SeckillResultVO(null, "QUEUING"));
    }
}
