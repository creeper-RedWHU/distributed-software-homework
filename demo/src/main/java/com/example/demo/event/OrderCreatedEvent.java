package com.example.demo.event;

import lombok.Getter;
import java.math.BigDecimal;

@Getter
public class OrderCreatedEvent extends BaseEvent {

    private final String orderNo;
    private final Long userId;
    private final Long activityId;
    private final BigDecimal totalAmount;

    public OrderCreatedEvent(Object source, String orderNo, Long userId, Long activityId, BigDecimal totalAmount) {
        super(source);
        this.orderNo = orderNo;
        this.userId = userId;
        this.activityId = activityId;
        this.totalAmount = totalAmount;
    }

    @Override
    public String getEventType() {
        return "ORDER_CREATED";
    }
}
