package com.example.rag.service;

import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbeddingInput;
import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbeddingRequest;
import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbeddingResult;
import com.volcengine.ark.runtime.service.ArkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 嵌入向量服务
 * 负责调用火山引擎 Ark API 生成文本的嵌入向量
 */
@Service
public class EmbeddingService {

    @Autowired
    private ArkService arkService;  // 火山引擎 Ark 服务

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;  // Redis 模板（可选）

    @Value("${volcengine.embedding.model}")
    private String model;  // 嵌入模型 ID

    @Value("${embedding.cache.enabled:true}")
    private boolean cacheEnabled;  // 是否启用缓存

    @Value("${embedding.cache.ttl:86400}")
    private long cacheTtl;  // 缓存过期时间（秒），默认 24 小时

    /**
 * 生成单个文本的嵌入向量（带缓存）
 * @param text 输入文本
 * @return 嵌入向量（Float 列表）
 */
    public List<Float> generateEmbedding(String text) {
        // 尝试从缓存获取
        if (cacheEnabled && redisTemplate != null) {
            String cacheKey = generateCacheKey(text);
            List<Float> cachedEmbedding = (List<Float>) redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedEmbedding != null) {
                System.out.println("从缓存获取嵌入向量，文本长度: " + text.length());
                return cachedEmbedding;
            }
        }
        
        System.out.println("开始生成嵌入，模型: " + model);
        System.out.println("输入文本长度: " + text.length());
        
        // 构建多模态嵌入输入
        List<MultimodalEmbeddingInput> inputs = new ArrayList<>();
        inputs.add(MultimodalEmbeddingInput.builder()
                .type("text")
                .text(text)
                .build());

        // 构建嵌入请求
        MultimodalEmbeddingRequest request = MultimodalEmbeddingRequest.builder()
                .model(model)
                .input(inputs)
                .build();

        // 调用火山引擎 API 生成嵌入向量
        System.out.println("发送请求到火山引擎 API...");
        MultimodalEmbeddingResult result = arkService.createMultiModalEmbeddings(request);
        System.out.println("API 返回结果: " + result);
        
        // 根据火山引擎 SDK 的实际 API 结构提取嵌入向量
        List<Float> embedding = extractEmbedding(result);
        
        // 将结果存入缓存
        if (cacheEnabled && redisTemplate != null && embedding != null) {
            String cacheKey = generateCacheKey(text);
            redisTemplate.opsForValue().set(cacheKey, embedding, cacheTtl, TimeUnit.SECONDS);
            System.out.println("嵌入向量已缓存，TTL: " + cacheTtl + " 秒");
        }
        
        return embedding;
    }
    
    /**
 * 从 API 结果中提取嵌入向量
 * @param result API 返回结果
 * @return 嵌入向量
 */
    private List<Float> extractEmbedding(MultimodalEmbeddingResult result) {
        if (result != null) {
            System.out.println("结果不为 null，尝试提取嵌入向量...");
            try {
                Object data = result.getData();
                System.out.println("Data 对象类型: " + (data != null ? data.getClass().getName() : "null"));
                
                // 处理 data 直接是 MultimodalEmbedding 对象的情况
                if (data instanceof com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbedding) {
                    // data 直接是 MultimodalEmbedding 对象
                    List<Double> doubleEmbedding = ((com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbedding) data).getEmbedding();
                    System.out.println("成功获取嵌入向量，维度: " + doubleEmbedding.size());
                    // 将 Double 转换为 Float
                    List<Float> floatEmbedding = new ArrayList<>(doubleEmbedding.size());
                    for (Double d : doubleEmbedding) {
                        floatEmbedding.add(d.floatValue());
                    }
                    return floatEmbedding;
                } else if (data instanceof List) {
                    // 处理 data 是 List 的情况
                    List<?> dataList = (List<?>) data;
                    System.out.println("Data 是 List，大小: " + dataList.size());
                    
                    if (!dataList.isEmpty()) {
                        Object firstItem = dataList.get(0);
                        System.out.println("第一个元素类型: " + firstItem.getClass().getName());
                        
                        if (firstItem instanceof com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbedding) {
                            List<Double> doubleEmbedding = ((com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbedding) firstItem).getEmbedding();
                            System.out.println("成功获取嵌入向量，维度: " + doubleEmbedding.size());
                            // 将 Double 转换为 Float
                            List<Float> floatEmbedding = new ArrayList<>(doubleEmbedding.size());
                            for (Double d : doubleEmbedding) {
                                floatEmbedding.add(d.floatValue());
                            }
                            return floatEmbedding;
                        }
                    }
                } else {
                    System.out.println("Data 类型未知: " + data.getClass().getName());
                }
            } catch (Exception e) {
                System.out.println("提取嵌入向量时发生异常: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("API 返回结果为 null");
        }
        
        throw new RuntimeException("Failed to generate embedding - 请查看控制台日志了解详细错误信息");
    }
    
    /**
 * 生成缓存键（基于文本内容的 MD5 哈希）
 * @param text 输入文本
 * @return 缓存键
 */
    private String generateCacheKey(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest((model + ":" + text).getBytes());
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return "embedding:" + hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // 如果 MD5 不可用，使用简单的哈希
            return "embedding:" + (model + ":" + text).hashCode();
        }
    }

    /**
     * 批量生成文本的嵌入向量
     * 遍历文本列表，对每个文本调用generateEmbedding方法生成对应的嵌入向量
     *
     * @param texts 需要生成嵌入向量的文本列表
     * @return 二维浮点数列表，每个元素是对应文本的嵌入向量，顺序与输入文本列表一致
     */
    public List<List<Float>> generateEmbeddings(List<String> texts) {
        List<List<Float>> embeddings = new ArrayList<>();
        
        /*
         * 逐个处理文本列表中的每个文本，生成对应的嵌入向量并添加到结果列表中
         */
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        
        return embeddings;
    }
}
