package com.example.demo.hook;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Hook 执行结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HookResult {

    private boolean proceed;    // true=继续  false=中止
    private String message;
    private Map<String, Object> data;

    public static HookResult ok() {
        return new HookResult(true, null, null);
    }

    public static HookResult abort(String message) {
        return new HookResult(false, message, null);
    }

    public static HookResult withData(Map<String, Object> data) {
        return new HookResult(true, null, data);
    }
}
