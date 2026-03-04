package com.example.demo.event;

import com.example.demo.common.Constants;
import com.example.demo.mapper.EventLogMapper;
import com.example.demo.model.entity.EventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 事件总线 — 封装 Spring ApplicationEvent + 持久化
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventBus {

    private final ApplicationEventPublisher publisher;
    private final EventLogMapper eventLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 发布事件（异步监听器处理）
     */
    public void publish(BaseEvent event) {
        persistEvent(event);
        publisher.publishEvent(event);
        log.debug("事件发布: type={}, id={}", event.getEventType(), event.getEventId());
    }

    private void persistEvent(BaseEvent event) {
        try {
            EventLog eventLog = new EventLog();
            eventLog.setEventType(event.getEventType());
            eventLog.setEventData(objectMapper.writeValueAsString(event));
            eventLog.setSource(event.getSource().getClass().getSimpleName());
            eventLog.setStatus(Constants.EVENT_PENDING);
            eventLog.setRetryCount(0);
            eventLogMapper.insert(eventLog);
        } catch (Exception e) {
            log.error("事件持久化失败: {}", event.getEventType(), e);
        }
    }
}
