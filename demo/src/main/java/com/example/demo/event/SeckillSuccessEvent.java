package com.example.demo.event;

import lombok.Getter;

@Getter
public class SeckillSuccessEvent extends BaseEvent {

    private final Long activityId;
    private final Long userId;
    private final String orderNo;

    public SeckillSuccessEvent(Object source, Long activityId, Long userId, String orderNo) {
        super(source);
        this.activityId = activityId;
        this.userId = userId;
        this.orderNo = orderNo;
    }

    @Override
    public String getEventType() {
        return "SECKILL_SUCCESS";
    }
}
