package com.datalabeling.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket配置
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 配置消息代理
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用简单消息代理，用于向客户端发送消息
        registry.enableSimpleBroker("/topic", "/queue");
        // 设置应用程序目的地前缀
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * 注册STOMP端点
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册一个STOMP的endpoint，并指定使用SockJS协议
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*") // 允许跨域
            .withSockJS(); // 启用SockJS
    }
}
