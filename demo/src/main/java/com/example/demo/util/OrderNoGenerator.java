package com.example.demo.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 订单号生成器（简化版雪花算法）
 * 格式: SK + yyyyMMddHHmmss + 6位序列号
 */
public final class OrderNoGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    private OrderNoGenerator() {}

    public static String generate() {
        String timePart = LocalDateTime.now().format(FMT);
        long seq = SEQUENCE.incrementAndGet() % 1000000;
        return "SK" + timePart + String.format("%06d", seq);
    }
}
