package com.example.rag.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置类
 * 配置 MilvusClient 用于向量存储和检索
 */
@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    // 扩展建议：如果你使用的是 Zilliz Cloud 或者设置了密码的 Milvus，最好预留 token 配置
    @Value("${milvus.token:}")
    private String token;

    /**
     * 配置 Milvus Client
     * @return MilvusClientV2 实例
     */
    @Bean
    public MilvusClientV2 milvusClient() {
        // 修复：补全 http:// 协议头，这是 V2 SDK 推荐的标准写法
        String uri = "http://" + host + ":" + port;

        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(uri);

        // 如果配置文件中配置了密码/Token，则注入
        if (token != null && !token.trim().isEmpty()) {
            builder.token(token);
        }

        return new MilvusClientV2(builder.build());
    }

    // 删除了 milvusCollectionName() 和 milvusDimension() 这两个 Bean
    // 因为你在 Service 已经通过 @Value 直接获取了，不需要把它们放进 Spring 容器中
}