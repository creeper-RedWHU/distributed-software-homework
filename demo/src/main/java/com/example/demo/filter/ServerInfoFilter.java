package com.example.demo.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 在响应头中添加服务器端口信息
 * 用于负载均衡时验证请求分发到了哪个后端实例
 */
@Component
@Order(1)
public class ServerInfoFilter implements Filter {

    @Value("${server.port:8080}")
    private String serverPort;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setHeader("X-Server-Port", serverPort);
        chain.doFilter(request, response);
    }
}
