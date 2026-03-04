package com.example.demo.hook;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hook 上下文 — 在 Hook 链中传递数据
 */
public class HookContext {

    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        return (T) data.get(key);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public void putAll(Map<String, Object> values) {
        data.putAll(values);
    }

    public Map<String, Object> toMap() {
        return Map.copyOf(data);
    }
}
