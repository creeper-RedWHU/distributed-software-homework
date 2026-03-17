package com.example.demo.config;

import com.example.demo.annotation.ReadOnly;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 读写分离 AOP 切面
 * 拦截带有 @ReadOnly 注解的方法，自动切换到从库
 */
@Slf4j
@Aspect
@Component
@Order(-1) // 确保在事务之前执行
public class ReadWriteAspect {

    @Around("@annotation(readOnly)")
    public Object aroundReadOnly(ProceedingJoinPoint joinPoint, ReadOnly readOnly) throws Throwable {
        try {
            DataSourceContextHolder.useSlave();
            log.debug("切换到从库: {}.{}", joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName());
            return joinPoint.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }
}
