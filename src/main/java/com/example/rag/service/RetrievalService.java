package com.example.rag.service;

import com.example.rag.model.DocumentChunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    
    @Autowired
    private BM25Service bm25Service;  // BM25关键词检索服务
    
    @Value("${retrieval.top-k:3}")
    private int defaultTopK;  // 默认检索返回的最相关文档块数量
    
    @Value("${retrieval.rerank.enabled:true}")
    private boolean rerankEnabled;  // 是否启用重排序
    
    @Value("${retrieval.rerank.top-k:5}")
    private int rerankTopK;  // 重排序前检索的候选块数量
    
    @Value("${retrieval.rerank.final-top-k:3}")
    private int rerankFinalTopK;  // 重排序后返回的最终块数量
    
    @Value("${retrieval.bm25.enabled:false}")
    private boolean bm25Enabled;  // 是否启用BM25关键词检索
    
    @Value("${retrieval.bm25.weight:0.3}")
    private double bm25Weight;  // BM25权重（混合检索时）
    
    @Value("${retrieval.bm25.top-k:5}")
    private int bm25TopK;  // BM25检索的候选块数量

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
        
        // 如果启用BM25，使用混合检索
        if (bm25Enabled) {
            return hybridRetrieve(query, topK, documentId, startTime);
        }
        
        // 否则使用纯向量检索
        return vectorRetrieve(query, topK, documentId, startTime);
    }
    
    /**
     * 纯向量检索
     */
    private List<DocumentChunk> vectorRetrieve(String query, int topK, String documentId, long startTime) {
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
     * 混合检索（向量 + BM25）
     */
    private List<DocumentChunk> hybridRetrieve(String query, int topK, String documentId, long startTime) {
        System.out.println("启用混合检索（向量 + BM25）");
        
        // 1. 向量检索 语义相似度
        List<Float> queryEmbedding = embeddingService.generateEmbedding(query);
        List<DocumentChunk> vectorResults = vectorStoreService.similaritySearch(queryEmbedding, bm25TopK, documentId);
        
        // 2. BM25检索 关键词
        List<DocumentChunk> bm25Results = bm25Service.search(query, bm25TopK, documentId);
        
        // 3. 合并结果并计算混合分数
        Map<String, DocumentChunk> mergedResults = new HashMap<>();
        
        // 归一化向量相似度分数
        double maxVectorScore = vectorResults.stream()
                .mapToDouble(DocumentChunk::getSimilarity)
                .max()
                .orElse(1.0);
        
        for (DocumentChunk chunk : vectorResults) {
            double normalizedVectorScore = chunk.getSimilarity() / maxVectorScore;
            chunk.setHybridScore(normalizedVectorScore * (1 - bm25Weight));
            mergedResults.put(chunk.getId(), chunk);
        }
        
        // 归一化BM25分数
        double maxBM25Score = bm25Results.stream()
                .mapToDouble(DocumentChunk::getScore)
                .max()
                .orElse(1.0);
        
        for (DocumentChunk chunk : bm25Results) {
            double normalizedBM25Score = chunk.getScore() / maxBM25Score;
            double bm25Contribution = normalizedBM25Score * bm25Weight;
            
            if (mergedResults.containsKey(chunk.getId())) {
                // 已存在，累加分数
                DocumentChunk existing = mergedResults.get(chunk.getId());
                existing.setHybridScore(existing.getHybridScore() + bm25Contribution);
            } else {
                // 不存在，添加新结果
                chunk.setHybridScore(bm25Contribution);
                mergedResults.put(chunk.getId(), chunk);
            }
        }
        
        // 4. 按混合分数排序并返回topK
        List<DocumentChunk> results = mergedResults.values().stream()
                .sorted((a, b) -> Double.compare(b.getHybridScore(), a.getHybridScore()))
                .limit(topK)
                .collect(Collectors.toList());
        
        long totalDuration = System.currentTimeMillis() - startTime;
        System.out.println("混合检索总耗时: " + totalDuration + "ms, 返回文档块数量: " + results.size());
        
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
