package com.example.demo.event;

import lombok.Getter;

@Getter
public class StockEmptyEvent extends BaseEvent {

    private final Long activityId;

    public StockEmptyEvent(Object source, Long activityId) {
        super(source);
        this.activityId = activityId;
    }

    @Override
    public String getEventType() {
        return "STOCK_EMPTY";
    }
}
