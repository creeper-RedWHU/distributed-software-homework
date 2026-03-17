package com.example.demo.annotation;

import java.lang.annotation.*;

/**
 * 标记方法使用从库（只读）数据源
 * 用于读写分离场景
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReadOnly {
}
