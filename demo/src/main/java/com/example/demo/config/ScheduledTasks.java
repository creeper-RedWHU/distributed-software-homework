package com.example.demo.config;

import com.example.demo.common.Constants;
import com.example.demo.mapper.SeckillActivityMapper;
import com.example.demo.model.entity.SeckillActivity;
import com.example.demo.service.OrderService;
import com.example.demo.service.StockService;
import com.example.demo.util.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final OrderService orderService;
    private final StockService stockService;
    private final SeckillActivityMapper activityMapper;
    private final RedisLockUtil redisLockUtil;

    /**
     * 每 30 秒扫描过期订单并关闭
     */
    @Scheduled(fixedRate = 30000)
    public void closeExpiredOrders() {
        String lockKey = "lock:timeout:scan";
        String requestId = redisLockUtil.tryLock(lockKey, 25000);
        if (requestId == null) return;

        try {
            int count = orderService.closeExpiredOrders();
            if (count > 0) {
                log.info("定时任务: 关闭 {} 个过期订单", count);
            }
        } finally {
            redisLockUtil.unlock(lockKey, requestId);
        }
    }

    /**
     * 每分钟检查即将开始的活动，执行库存预热
     */
    @Scheduled(fixedRate = 60000)
    public void warmupUpcomingActivities() {
        LocalDateTime upcoming = LocalDateTime.now().plusMinutes(5);
        List<SeckillActivity> activities = activityMapper.selectActivitiesToStart(upcoming);
        for (SeckillActivity activity : activities) {
            String lockKey = "lock:warmup:" + activity.getId();
            String requestId = redisLockUtil.tryLock(lockKey, 55000);
            if (requestId != null) {
                try {
                    stockService.warmupStock(activity.getId());
                    activityMapper.updateStatus(activity.getId(), Constants.ACTIVITY_IN_PROGRESS);
                } finally {
                    redisLockUtil.unlock(lockKey, requestId);
                }
            }
        }
    }

    /**
     * 每分钟检查已到结束时间的活动
     */
    @Scheduled(fixedRate = 60000)
    public void endExpiredActivities() {
        List<SeckillActivity> activities = activityMapper.selectActivitiesToEnd(LocalDateTime.now());
        for (SeckillActivity activity : activities) {
            activityMapper.updateStatus(activity.getId(), Constants.ACTIVITY_ENDED);
            log.info("活动自动结束: id={}, name={}", activity.getId(), activity.getName());
        }
    }
}
