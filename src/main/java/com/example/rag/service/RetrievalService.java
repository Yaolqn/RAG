package com.example.rag.service;

import com.example.rag.model.DocumentChunk;
import org.springframework.beans.factory.annotation.Autowired;
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
        // 生成查询的嵌入向量
        List<Float> queryEmbedding = embeddingService.generateEmbedding(query);
        // 在向量存储中搜索相似文档块
        return vectorStoreService.similaritySearch(queryEmbedding, topK, documentId);
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
