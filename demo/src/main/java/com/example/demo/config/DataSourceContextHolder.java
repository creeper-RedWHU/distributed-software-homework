package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据源上下文持有者
 * 使用 ThreadLocal 保存当前线程使用的数据源 key
 */
@Slf4j
public class DataSourceContextHolder {

    public static final String MASTER = "master";
    public static final String SLAVE = "slave";

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void setDataSource(String dataSource) {
        log.debug("切换数据源: {}", dataSource);
        CONTEXT.set(dataSource);
    }

    public static String getDataSource() {
        String ds = CONTEXT.get();
        return ds == null ? MASTER : ds;
    }

    public static void useMaster() {
        setDataSource(MASTER);
    }

    public static void useSlave() {
        setDataSource(SLAVE);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
