package com.example.rag.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 指标服务
 * 统一管理系统各项指标，包括延迟、缓存命中率、查询耗时等
 */
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // 计数器
    private Counter embeddingCacheHitCounter;
    private Counter embeddingCacheMissCounter;
    private Counter embeddingApiCallCounter;
    private Counter embeddingApiErrorCounter;
    private Counter milvusSearchCounter;
    private Counter milvusSearchErrorCounter;
    private Counter ragRequestCounter;
    private Counter ragErrorCounter;
    private Counter rerankCounter;

    // 计时器
    private Timer embeddingApiTimer;
    private Timer milvusSearchTimer;
    private Timer ragRequestTimer;
    private Timer rerankTimer;

    @Autowired
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    /**
     * 初始化所有指标
     */
    private void initMetrics() {
        // 嵌入向量缓存指标
        embeddingCacheHitCounter = Counter.builder("embedding.cache.hit")
                .description("嵌入向量缓存命中次数")
                .register(meterRegistry);
        
        embeddingCacheMissCounter = Counter.builder("embedding.cache.miss")
                .description("嵌入向量缓存未命中次数")
                .register(meterRegistry);

        // 嵌入向量API调用指标
        embeddingApiCallCounter = Counter.builder("embedding.api.call")
                .description("嵌入向量API调用次数")
                .register(meterRegistry);
        
        embeddingApiErrorCounter = Counter.builder("embedding.api.error")
                .description("嵌入向量API调用失败次数")
                .register(meterRegistry);
        
        embeddingApiTimer = Timer.builder("embedding.api.duration")
                .description("嵌入向量API调用耗时")
                .register(meterRegistry);

        // Milvus搜索指标
        milvusSearchCounter = Counter.builder("milvus.search.call")
                .description("Milvus搜索调用次数")
                .register(meterRegistry);
        
        milvusSearchErrorCounter = Counter.builder("milvus.search.error")
                .description("Milvus搜索失败次数")
                .register(meterRegistry);
        
        milvusSearchTimer = Timer.builder("milvus.search.duration")
                .description("Milvus搜索耗时")
                .register(meterRegistry);

        // RAG请求指标
        ragRequestCounter = Counter.builder("rag.request.call")
                .description("RAG请求次数")
                .register(meterRegistry);
        
        ragErrorCounter = Counter.builder("rag.request.error")
                .description("RAG请求失败次数")
                .register(meterRegistry);
        
        ragRequestTimer = Timer.builder("rag.request.duration")
                .description("RAG请求总耗时")
                .register(meterRegistry);

        // Rerank指标
        rerankCounter = Counter.builder("rerank.call")
                .description("重排序调用次数")
                .register(meterRegistry);
        
        rerankTimer = Timer.builder("rerank.duration")
                .description("重排序耗时")
                .register(meterRegistry);
    }

    // 嵌入向量缓存指标
    public void recordEmbeddingCacheHit() {
        embeddingCacheHitCounter.increment();
    }

    public void recordEmbeddingCacheMiss() {
        embeddingCacheMissCounter.increment();
    }

    public double getCacheHitRate() {
        double hits = embeddingCacheHitCounter.count();
        double misses = embeddingCacheMissCounter.count();
        double total = hits + misses;
        return total > 0 ? hits / total : 0.0;
    }

    // 嵌入向量API指标
    public void recordEmbeddingApiCall(long durationMs, boolean success) {
        embeddingApiCallCounter.increment();
        embeddingApiTimer.record(durationMs, TimeUnit.MILLISECONDS);
        if (!success) {
            embeddingApiErrorCounter.increment();
        }
    }

    // Milvus搜索指标
    public void recordMilvusSearch(long durationMs, boolean success) {
        milvusSearchCounter.increment();
        milvusSearchTimer.record(durationMs, TimeUnit.MILLISECONDS);
        if (!success) {
            milvusSearchErrorCounter.increment();
        }
    }

    // RAG请求指标
    public void recordRagRequest(long durationMs, boolean success) {
        ragRequestCounter.increment();
        ragRequestTimer.record(durationMs, TimeUnit.MILLISECONDS);
        if (!success) {
            ragErrorCounter.increment();
        }
    }

    // Rerank指标
    public void recordRerank(long durationMs) {
        rerankCounter.increment();
        rerankTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
