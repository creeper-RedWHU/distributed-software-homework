package com.example.demo.event;

import lombok.Getter;

@Getter
public class OrderTimeoutEvent extends BaseEvent {

    private final String orderNo;
    private final Long userId;
    private final Long activityId;

    public OrderTimeoutEvent(Object source, String orderNo, Long userId, Long activityId) {
        super(source);
        this.orderNo = orderNo;
        this.userId = userId;
        this.activityId = activityId;
    }

    @Override
    public String getEventType() {
        return "ORDER_TIMEOUT";
    }
}
