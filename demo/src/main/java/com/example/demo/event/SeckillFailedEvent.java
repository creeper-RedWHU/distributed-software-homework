package com.example.demo.event;

import lombok.Getter;

@Getter
public class SeckillFailedEvent extends BaseEvent {

    private final Long activityId;
    private final Long userId;
    private final String orderNo;
    private final String reason;

    public SeckillFailedEvent(Object source, Long activityId, Long userId, String orderNo, String reason) {
        super(source);
        this.activityId = activityId;
        this.userId = userId;
        this.orderNo = orderNo;
        this.reason = reason;
    }

    @Override
    public String getEventType() {
        return "SECKILL_FAILED";
    }
}
