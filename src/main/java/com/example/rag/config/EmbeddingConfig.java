package com.example.rag.config;

import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 火山引擎 Ark 服务配置类
 * 配置 ArkService Bean 和模型参数
 */
@Configuration
public class EmbeddingConfig {

    @Value("${volcengine.api-key:${ARK_API_KEY:}}")
    private String apiKey;  // 火山引擎 API Key

    @Value("${volcengine.embedding.model:ep-20260420014217-l6bqr}")
    private String embeddingModel;  // 嵌入模型 ID

    @Value("${volcengine.chat.model:ep-20260419235315-sv4kp}")
    private String chatModel;  // 聊天模型 ID

    /**
 * 配置火山引擎 Ark Service Bean
 * @return ArkService 实例
 */
    @Bean
    public ArkService arkService() {
        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        
        return ArkService.builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .apiKey(apiKey)
                .build();
    }

    /**
 * 配置嵌入模型 Bean
 * @return 嵌入模型 ID
 */
    @Bean
    public String embeddingModel() {
        return embeddingModel;
    }

    /**
 * 配置聊天模型 Bean
 * @return 聊天模型 ID
 */
    @Bean
    public String chatModel() {
        return chatModel;
    }
}
