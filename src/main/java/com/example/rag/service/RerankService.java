package com.example.rag.service;

import com.example.rag.model.DocumentChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 重排序服务
 * 对检索到的文档块进行重新打分，提升答案相关性
 */
@Service
public class RerankService {

    @Value("${retrieval.rerank.enabled:true}")
    private boolean rerankEnabled;  // 是否启用重排序

    @Value("${retrieval.rerank.method:keyword}")
    private String rerankMethod;  // 重排序方法

    /**
     * 对文档块进行重排序
     * @param query 用户查询
     * @param candidates 候选文档块列表
     * @param finalTopK 最终返回的文档块数量
     * @return 重排序后的文档块列表
     */
    public List<DocumentChunk> rerank(String query, List<DocumentChunk> candidates, int finalTopK) {
        if (!rerankEnabled || candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        // 如果候选数量已经小于等于最终返回数量，直接返回
        if (candidates.size() <= finalTopK) {
            return candidates;
        }

        List<DocumentChunk> rerankedChunks;

        switch (rerankMethod.toLowerCase()) {
            case "keyword":
                rerankedChunks = keywordRerank(query, candidates);
                break;
            case "semantic":
                rerankedChunks = semanticRerank(query, candidates);
                break;
            default:
                rerankedChunks = keywordRerank(query, candidates);
        }

        // 返回前finalTopK个结果
        return rerankedChunks.stream()
                .limit(finalTopK)
                .collect(Collectors.toList());
    }

    /**
     * 基于关键词匹配的重排序
     * 计算查询中的关键词在文档块中的出现频率和位置
     */
    private List<DocumentChunk> keywordRerank(String query, List<DocumentChunk> candidates) {
        // 提取查询中的关键词（去除停用词）
        Set<String> queryKeywords = extractKeywords(query);

        return candidates.stream()
                .map(chunk -> {
                    double score = calculateKeywordScore(chunk.getContent(), queryKeywords);
                    chunk.setSimilarity(score);  // 更新相似度分数
                    return chunk;
                })
                .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
                .collect(Collectors.toList());
    }

    /**
     * 基于语义相似度的重排序
     * 结合原始向量相似度和关键词匹配分数
     */
    private List<DocumentChunk> semanticRerank(String query, List<DocumentChunk> candidates) {
        Set<String> queryKeywords = extractKeywords(query);

        return candidates.stream()
                .map(chunk -> {
                    double originalScore = chunk.getSimilarity();
                    double keywordScore = calculateKeywordScore(chunk.getContent(), queryKeywords);
                    // 加权组合：70%原始相似度 + 30%关键词分数
                    double combinedScore = 0.7 * originalScore + 0.3 * keywordScore;
                    chunk.setSimilarity(combinedScore);
                    return chunk;
                })
                .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
                .collect(Collectors.toList());
    }

    /**
     * 提取查询中的关键词
     * 去除常见的停用词和标点符号
     */
    private Set<String> extractKeywords(String query) {
        // 简单的分词和停用词过滤
        String[] stopWords = {"的", "了", "是", "在", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这"};
        
        // 分词（按空格和常见标点分割）
        String[] words = query.split("[\\s,，.。!！?？;；:：]");
        
        Set<String> keywords = new HashSet<>();
        for (String word : words) {
            word = word.trim();
            if (word.length() > 1 && !Arrays.asList(stopWords).contains(word)) {
                keywords.add(word.toLowerCase());
            }
        }
        
        return keywords;
    }

    /**
     * 计算关键词匹配分数
     * 考虑关键词出现频率、位置权重
     */
    private double calculateKeywordScore(String content, Set<String> keywords) {
        if (keywords.isEmpty()) {
            return 0.0;
        }

        String lowerContent = content.toLowerCase();
        double score = 0.0;
        int totalMatches = 0;

        for (String keyword : keywords) {
            if (lowerContent.contains(keyword)) {
                totalMatches++;
                // 计算关键词出现次数
                int count = countOccurrences(lowerContent, keyword);
                
                // 计算首次出现位置（位置越靠前，权重越高）
                int firstIndex = lowerContent.indexOf(keyword);
                double positionWeight = 1.0 - (double) firstIndex / lowerContent.length();
                
                // 综合分数：出现次数 * 位置权重
                score += count * (1 + positionWeight);
            }
        }

        // 归一化分数
        if (totalMatches > 0) {
            score = score / keywords.size();
        }

        return score;
    }

    /**
     * 计算字符串中子串的出现次数
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        
        return count;
    }
}
