package com.example.demo.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EventLog {
    private Long id;
    private String eventType;
    private String eventData;
    private String source;
    private Integer status;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
