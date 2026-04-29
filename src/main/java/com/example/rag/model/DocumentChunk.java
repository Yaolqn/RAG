package com.example.rag.model;

import java.util.List;

/**
 * 文档块模型
 * 表示文档的一个分块，包含内容、嵌入向量和元数据
 */
public class DocumentChunk {
    private String id;  // 文档块唯一 ID
    private String documentId;  // 文档唯一 ID（用于文档隔离）
    private String content;  // 文档块内容
    private List<Float> embedding;  // 嵌入向量
    private String source;  // 文档来源（文件名）
    private int chunkIndex;  // 文档块索引
    private int totalChunks;  // 总文档块数
    private double similarity;  // 相似度分数

    // 无参构造函数
    public DocumentChunk() {
    }

    // 全参构造函数
    public DocumentChunk(String id, String documentId, String content, List<Float> embedding, String source, int chunkIndex, int totalChunks) {
        this.id = id;
        this.documentId = documentId;
        this.content = content;
        this.embedding = embedding;
        this.source = source;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Float> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Float> embedding) {
        this.embedding = embedding;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }
}
