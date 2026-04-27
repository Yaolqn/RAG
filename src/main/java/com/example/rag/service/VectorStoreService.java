package com.example.rag.service;

import com.example.rag.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量存储服务
 * 使用内存存储文档块及其嵌入向量，提供相似度搜索功能
 */
@Service
public class VectorStoreService {

    // 向量存储，使用 ConcurrentHashMap 作为内存存储
    private final ConcurrentHashMap<String, DocumentChunk> chunkStore = new ConcurrentHashMap<>();  // 文档块存储（按 ID 索引）
    private final List<DocumentChunk> chunks = new ArrayList<>();  // 文档块列表

    /**
 * 添加文档块到向量存储
 * @param documentChunks 文档块列表
 */
    //向量数据库
    public void addChunks(List<DocumentChunk> documentChunks) {
        for (DocumentChunk chunk : documentChunks) {
            chunkStore.put(chunk.getId(), chunk);
            chunks.add(chunk);
        }
    }

    /**
 * 基于余弦相似度搜索相关文档块
 * @param queryEmbedding 查询嵌入向量
 * @param topK 返回的最相关文档块数量
 * @return 按相似度排序的文档块列表
 */
    public List<DocumentChunk> similaritySearch(List<Float> queryEmbedding, int topK) {
        List<DocumentChunk> results = new ArrayList<>();
        
        // 计算每个文档块与查询向量的相似度
        //// 余弦相似度公式
        //similarity = (A · B) / (||A|| × ||B||)
        //// 点积 / (向量A模 × 向量B模)
        for (DocumentChunk chunk : chunks) {
            if (chunk.getEmbedding() != null) {
                double similarity = cosineSimilarity(queryEmbedding, chunk.getEmbedding());
                chunk.setSimilarity(similarity);
                results.add(chunk);
            }
        }
        
        // 按相似度降序排序
        results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        
        // 返回 topK 个最相关的文档块
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    /**
 * 清空向量存储
 */
    public void clear() {
        chunkStore.clear();
        chunks.clear();
    }

    /**
 * 获取存储的文档块数量
 * @return 文档块数量
 */
    public int size() {
        return chunks.size();
    }

    /**
 * 计算两个向量的余弦相似度
 * @param vectorA 向量 A
 * @param vectorB 向量 B
 * @return 余弦相似度（0-1 之间）
 */
    private double cosineSimilarity(List<Float> vectorA, List<Float> vectorB) {
        double dotProduct = 0.0;  // 点积
        double normA = 0.0;  // 向量 A 的模
        double normB = 0.0;  // 向量 B 的模
        
        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += Math.pow(vectorA.get(i), 2);
            normB += Math.pow(vectorB.get(i), 2);
        }
        
        // 防止除以零
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
