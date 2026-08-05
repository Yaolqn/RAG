package com.example.rag.service;

import com.example.rag.model.DocumentChunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 检索服务
 * 负责根据用户查询检索相关文档块
 */
@Service
public class RetrievalService {

    @Autowired
    private EmbeddingService embeddingService;  // 嵌入服务

    @Autowired
    private VectorStoreService vectorStoreService;  // 向量存储服务
    
    @Autowired
    private RerankService rerankService;  // 重排序服务

    @Autowired
    private MetricsService metricsService;  // 指标服务
    
    @Value("${retrieval.top-k:3}")
    private int defaultTopK;  // 默认检索返回的最相关文档块数量
    
    @Value("${retrieval.rerank.enabled:true}")
    private boolean rerankEnabled;  // 是否启用重排序
    
    @Value("${retrieval.rerank.top-k:5}")
    private int rerankTopK;  // 重排序前检索的候选块数量
    
    @Value("${retrieval.rerank.final-top-k:3}")
    private int rerankFinalTopK;  // 重排序后返回的最终块数量

    /**
 * 根据查询检索相关文档块（使用默认topK，支持重排序）
 * @param query 用户查询
 * @return 相关文档块列表
 */
    public List<DocumentChunk> retrieve(String query) {
        return retrieve(query, defaultTopK, null);
    }

    /**
 * 根据查询检索相关文档块
 * @param query 用户查询
 * @param topK 返回的最相关文档块数量
 * @return 相关文档块列表
 */
    public List<DocumentChunk> retrieve(String query, int topK) {
        return retrieve(query, topK, null);
    }

    /**
 * 根据查询检索相关文档块（支持文档隔离）
 * @param query 用户查询
 * @param topK 返回的最相关文档块数量
 * @param documentId 文档ID（可选，如果指定则只在该文档内搜索）
 * @return 相关文档块列表
 */
    public List<DocumentChunk> retrieve(String query, int topK, String documentId) {
        long startTime = System.currentTimeMillis();
        
        // 生成查询的嵌入向量
        List<Float> queryEmbedding = embeddingService.generateEmbedding(query);
        
        // 如果启用重排序，先检索更多候选块
        int candidateTopK = rerankEnabled ? rerankTopK : topK;
        
        // 在向量存储中搜索相似文档块
        List<DocumentChunk> candidates = vectorStoreService.similaritySearch(queryEmbedding, candidateTopK, documentId);
        
        // 如果启用重排序，对候选块进行重排序
        if (rerankEnabled && candidates.size() > topK) {
            System.out.println("启用重排序，候选块数量: " + candidates.size() + ", 最终返回: " + topK);
            long rerankStartTime = System.currentTimeMillis();
            List<DocumentChunk> rerankedResults = rerankService.rerank(query, candidates, topK);
            long rerankDuration = System.currentTimeMillis() - rerankStartTime;
            metricsService.recordRerank(rerankDuration);
            
            long totalDuration = System.currentTimeMillis() - startTime;
            System.out.println("检索总耗时: " + totalDuration + "ms (含重排序: " + rerankDuration + "ms)");
            return rerankedResults;
        }
        
        // 否则直接返回前topK个结果
        List<DocumentChunk> results = candidates.stream()
                .limit(topK)
                .collect(Collectors.toList());
        
        long totalDuration = System.currentTimeMillis() - startTime;
        System.out.println("检索总耗时: " + totalDuration + "ms");
        
        return results;
    }

    /**
 * 格式化检索到的文档块为上下文字符串
 * @param chunks 文档块列表
 * @return 格式化的上下文字符串
 */
    public String formatContext(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(chunk -> String.format("[来源: %s]\n%s", chunk.getSource(), chunk.getContent()))
                .collect(Collectors.joining("\n\n"));
    }
}
