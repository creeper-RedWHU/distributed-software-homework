package com.example.demo.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 事件基类 — 所有业务事件的父类
 */
@Getter
public abstract class BaseEvent extends ApplicationEvent {

    private final String eventId;
    private final LocalDateTime eventTime;

    protected BaseEvent(Object source) {
        super(source);
        this.eventId = UUID.randomUUID().toString();
        this.eventTime = LocalDateTime.now();
    }

    public abstract String getEventType();
}
