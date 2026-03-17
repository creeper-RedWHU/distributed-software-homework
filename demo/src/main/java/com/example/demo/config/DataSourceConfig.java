package com.example.demo.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 读写分离数据源配置
 * master: 主库（读写）
 * slave: 从库（只读）
 * 通过 RoutingDataSource + AOP 自动切换
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    @Bean("masterDataSource")
    public DataSource masterDataSource(
            @Value("${spring.datasource.master.url}") String url,
            @Value("${spring.datasource.master.username}") String username,
            @Value("${spring.datasource.master.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("master-pool");
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setMaximumPoolSize(20);
        ds.setMinimumIdle(5);
        ds.setConnectionTimeout(30000);
        ds.setIdleTimeout(600000);
        log.info("初始化 Master 数据源: {}", url);
        return ds;
    }

    @Bean("slaveDataSource")
    public DataSource slaveDataSource(
            @Value("${spring.datasource.slave.url}") String url,
            @Value("${spring.datasource.slave.username}") String username,
            @Value("${spring.datasource.slave.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("slave-pool");
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setMaximumPoolSize(20);
        ds.setMinimumIdle(5);
        ds.setConnectionTimeout(30000);
        ds.setIdleTimeout(600000);
        ds.setReadOnly(true);
        log.info("初始化 Slave 数据源: {}", url);
        return ds;
    }

    @Bean
    @Primary
    public DataSource dataSource(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("slaveDataSource") DataSource slaveDataSource) {
        RoutingDataSource routingDataSource = new RoutingDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceContextHolder.MASTER, masterDataSource);
        targetDataSources.put(DataSourceContextHolder.SLAVE, slaveDataSource);
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);
        log.info("初始化路由数据源: master + slave");
        return routingDataSource;
    }
}
