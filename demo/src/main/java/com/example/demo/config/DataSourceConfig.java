package com.example.demo.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;

/**
 * 数据源配置：读写分离 + ShardingSphere分库分表
 *
 * 架构:
 * - master/slave: 读写分离（通过 RoutingDataSource + AOP 自动切换）
 * - ShardingSphere: 包装主库，实现订单表分表（t_seckill_order -> t_seckill_order_0, t_seckill_order_1）
 * - 分库策略: 按 user_id 路由（演示用单库，生产可扩展为多库）
 * - 分表策略: 按 order_id (id字段) 取模路由到不同物理表
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

    /**
     * 创建读写分离路由数据源（RoutingDataSource + AOP切面自动切换master/slave）
     */
    @Bean("routingDataSource")
    public DataSource routingDataSource(
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

    /**
     * ShardingSphere 分表数据源（包装路由数据源，实现订单表分表）
     *
     * 分表规则:
     * - 逻辑表: t_seckill_order
     * - 物理表: t_seckill_order_0, t_seckill_order_1
     * - 分表键: id (订单ID，雪花算法生成，基因法嵌入userId)
     * - 分表算法: id % 2
     *
     * 分库规则（概念演示，单库模式）:
     * - 分库键: user_id
     * - 生产环境可扩展为: ds_${user_id % N} 路由到不同物理库
     */
    @Bean
    @Primary
    public DataSource dataSource(
            @Qualifier("routingDataSource") DataSource routingDataSource) throws SQLException {
        // ShardingSphere 数据源映射（单库模式，使用路由数据源）
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        dataSourceMap.put("ds0", routingDataSource);

        // 分表规则配置
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();

        // 订单表分表规则: t_seckill_order -> ds0.t_seckill_order_0, ds0.t_seckill_order_1
        ShardingTableRuleConfiguration orderTableRule = new ShardingTableRuleConfiguration(
                "t_seckill_order", "ds0.t_seckill_order_${0..1}");
        orderTableRule.setTableShardingStrategy(
                new StandardShardingStrategyConfiguration("id", "order-id-mod"));
        shardingRuleConfig.getTables().add(orderTableRule);

        // 分表算法: id % 2
        Properties modProps = new Properties();
        modProps.setProperty("sharding-count", "2");
        shardingRuleConfig.getShardingAlgorithms().put("order-id-mod",
                new AlgorithmConfiguration("MOD", modProps));

        // ShardingSphere 全局属性
        Properties props = new Properties();
        props.setProperty("sql-show", "true"); // 打印实际SQL，便于观察分表效果

        DataSource shardingDataSource = ShardingSphereDataSourceFactory.createDataSource(
                dataSourceMap, Collections.singletonList(shardingRuleConfig), props);

        log.info("初始化 ShardingSphere 分表数据源: t_seckill_order -> t_seckill_order_0, t_seckill_order_1");
        return shardingDataSource;
    }
}
