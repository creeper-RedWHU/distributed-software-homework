package com.example.cloudlab.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import javax.annotation.PostConstruct;
import java.util.Set;

@Configuration
public class GatewaySentinelConfig {

    @Value("${spring.cloud.nacos.discovery.server-addr}")
    private String nacosServerAddr;

    @Value("${spring.cloud.nacos.discovery.group:DEFAULT_GROUP}")
    private String nacosGroup;

    private final GatewayBlockHandlers gatewayBlockHandlers;

    public GatewaySentinelConfig(GatewayBlockHandlers gatewayBlockHandlers) {
        this.gatewayBlockHandlers = gatewayBlockHandlers;
    }

    @Bean
    public GlobalFilter sentinelGatewayFilter() {
        return new OrderedGatewayFilterAdapter(new SentinelGatewayFilter(), Ordered.HIGHEST_PRECEDENCE);
    }

    @PostConstruct
    public void initGatewayRules() {
        GatewayCallbackManager.setBlockHandler(gatewayBlockHandlers.jsonBlockHandler());
        Converter<String, Set<GatewayFlowRule>> parser =
            source -> JSON.parseArray(source, GatewayFlowRule.class).stream().collect(java.util.stream.Collectors.toSet());
        ReadableDataSource<String, Set<GatewayFlowRule>> dataSource =
            new NacosDataSource<>(nacosServerAddr, nacosGroup, "gateway-service-sentinel-gateway.json", parser);
        com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager.register2Property(dataSource.getProperty());
    }

    private static final class OrderedGatewayFilterAdapter implements GlobalFilter, Ordered {

        private final SentinelGatewayFilter delegate;
        private final int order;

        private OrderedGatewayFilterAdapter(SentinelGatewayFilter delegate, int order) {
            this.delegate = delegate;
            this.order = order;
        }

        @Override
        public reactor.core.publisher.Mono<Void> filter(ServerWebExchange exchange,
                                                        org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
            return delegate.filter(exchange, chain);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }
}
