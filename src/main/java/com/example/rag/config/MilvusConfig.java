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

    @Value("${milvus.collection-name:rag_documents}")
    private String collectionName;

    @Value("${milvus.dimension:2048}")
    private int dimension;

    /**
     * 配置 Milvus Client
     * @return MilvusClientV2 实例
     */
    @Bean
    public MilvusClientV2 milvusClient() {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(host + ":" + port)
                .build();

        return new MilvusClientV2(connectConfig);
    }

    /**
     * 获取集合名称
     * @return 集合名称
     */
    @Bean
    public String milvusCollectionName() {
        return collectionName;
    }

    /**
     * 获取向量维度
     * @return 向量维度
     */
    @Bean
    public int milvusDimension() {
        return dimension;
    }
}
