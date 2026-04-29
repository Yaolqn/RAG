package com.example.rag.service;

import com.example.rag.model.DocumentChunk;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档服务
 * 负责文档文本提取、分块和文档块创建
 */
@Service
public class DocumentService {

    private final Tika tika = new Tika();  // Apache Tika 文本提取工具
    
    private static final int MIN_CHUNK_SIZE = 200;  // 最小块大小
    private static final int MAX_CHUNK_SIZE = 800;  // 最大块大小
    private static final int TARGET_CHUNK_SIZE = 500;  // 目标块大小
    private static final int CHUNK_OVERLAP = 100;  // 块重叠大小（保持上下文连续性）

    /**
 * 从上传的文件中提取文本
 * @param file 上传的文件
 * @return 提取的文本内容
 * @throws IOException 文件读取异常
 */
    public String extractText(MultipartFile file) throws IOException {
        //Metadata 的作用：
        //
        //存储文档元数据：包含文档的各种属性信息，如文件名、作者、创建日期、内容类型等
        //辅助文本提取：Tika 在解析文档时会使用这些元数据来更好地理解文档结构
        //标准化字段：Tika 定义了许多标准的元数据字段名称
        Metadata metadata = new Metadata();
        metadata.set("resourceName", file.getOriginalFilename());
        
        try {
            return tika.parseToString(file.getInputStream(), metadata);
        } catch (Exception e) {
            throw new IOException("Failed to extract text from file", e);
        }
    }

    /**
 * 将文本分割成多个块（改进的语义分块策略）
 * 优先在段落边界分割，动态调整块大小，添加重叠保持上下文
 * @param text 输入文本
 * @return 文本块列表
 */
    public List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        
        // 先按段落分割（保持段落完整性）
        String[] paragraphs = text.split("\\n\\s*\\n");
        
        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                continue;
            }
            
            // 如果段落本身在目标大小范围内，直接作为一个块
            if (paragraph.length() >= MIN_CHUNK_SIZE && paragraph.length() <= MAX_CHUNK_SIZE) {
                chunks.add(paragraph.trim());
                continue;
            }
            
            // 如果段落太长，按句子分割
            if (paragraph.length() > MAX_CHUNK_SIZE) {
                List<String> paragraphChunks = splitLongParagraph(paragraph);
                chunks.addAll(paragraphChunks);
            }
            
            // 如果段落太短，尝试与下一段合并
            if (paragraph.length() < MIN_CHUNK_SIZE) {
                // 暂时添加，后续会合并
                chunks.add(paragraph.trim());
            }
        }
        
        // 合并过短的块
        chunks = mergeSmallChunks(chunks);
        
        // 添加重叠（保持上下文连续性）
        chunks = addOverlap(chunks);
        
        return chunks;
    }
    
    /**
 * 分割过长的段落
 * @param paragraph 过长的段落
 * @return 分割后的块列表
 */
    private List<String> splitLongParagraph(String paragraph) {
        List<String> chunks = new ArrayList<>();
        
        // 按句子分割
        String[] sentences = paragraph.split("(?<=[。！？.!?])\\s*");
        StringBuilder currentChunk = new StringBuilder();
        
        for (String sentence : sentences) {
            String trimmedSentence = sentence.trim();
            if (trimmedSentence.isEmpty()) {
                continue;
            }
            
            // 如果添加这句会超过最大限制，保存当前块
            if (currentChunk.length() + trimmedSentence.length() > MAX_CHUNK_SIZE && currentChunk.length() >= MIN_CHUNK_SIZE) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder(trimmedSentence);
            } else {
                currentChunk.append(trimmedSentence);
            }
        }
        
        // 添加最后一个块
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }
    
    /**
 * 合并过短的块
 * @param chunks 原始块列表
 * @return 合并后的块列表
 */
    private List<String> mergeSmallChunks(List<String> chunks) {
        List<String> mergedChunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        
        for (String chunk : chunks) {
            if (currentChunk.length() + chunk.length() <= TARGET_CHUNK_SIZE) {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(chunk);
            } else {
                if (currentChunk.length() > 0) {
                    mergedChunks.add(currentChunk.toString());
                }
                currentChunk = new StringBuilder(chunk);
            }
        }
        
        if (currentChunk.length() > 0) {
            mergedChunks.add(currentChunk.toString());
        }
        
        return mergedChunks;
    }
    
    /**
 * 为块添加重叠内容，保持上下文连续性
 * @param chunks 原始块列表
 * @return 添加重叠后的块列表
 */
    private List<String> addOverlap(List<String> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }
        
        List<String> overlappedChunks = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            
            // 如果不是第一个块，添加前一个块的尾部作为重叠
            if (i > 0) {
                String previousChunk = chunks.get(i - 1);
                String overlap = getOverlapText(previousChunk, CHUNK_OVERLAP);
                chunk = overlap + "\n\n" + chunk;
            }
            
            overlappedChunks.add(chunk);
        }
        
        return overlappedChunks;
    }
    
    /**
 * 获取文本的尾部重叠部分
 * @param text 原始文本
 * @param overlapSize 重叠大小
 * @return 重叠文本
 */
    private String getOverlapText(String text, int overlapSize) {
        if (text.length() <= overlapSize) {
            return text;
        }
        
        // 从尾部开始查找句子边界
        String tail = text.substring(text.length() - overlapSize);
        int firstSentenceEnd = tail.indexOf('。');
        
        if (firstSentenceEnd > 0) {
            return tail.substring(firstSentenceEnd + 1).trim();
        }
        
        return tail.trim();
    }

    /**
     * 创建文档块对象列表
     * 将文本分割成多个块，并为每个块创建包含唯一ID、嵌入向量、来源等信息的DocumentChunk对象
     *
     * @param text 要分割的文本内容
     * @param source 文档来源标识（如文件名或URL）
     * @param embeddings 与文本块对应的嵌入向量列表，需与分割后的文本块数量一致
     * @return 文档块对象列表，每个对象包含块ID、内容、嵌入向量、来源和位置信息
     */
    public List<DocumentChunk> createDocumentChunks(String text, String source, List<List<Float>> embeddings) {
        String documentId = UUID.randomUUID().toString(); // 生成文档唯一ID
        return createDocumentChunks(text, source, embeddings, documentId);
    }

    /**
     * 创建文档块对象列表（支持指定文档ID）
     * 将文本分割成多个块，并为每个块创建包含唯一ID、嵌入向量、来源等信息的DocumentChunk对象
     *
     * @param text 要分割的文本内容
     * @param source 文档来源标识（如文件名或URL）
     * @param embeddings 与文本块对应的嵌入向量列表，需与分割后的文本块数量一致
     * @param documentId 文档唯一ID
     * @return 文档块对象列表，每个对象包含块ID、内容、嵌入向量、来源、位置信息和文档ID
     */
    public List<DocumentChunk> createDocumentChunks(String text, String source, List<List<Float>> embeddings, String documentId) {
        List<String> chunks = splitText(text);
        List<DocumentChunk> documentChunks = new ArrayList<>();
        
        /*
         * 遍历所有文本块，为每个块生成唯一ID并创建DocumentChunk对象
         * 每个DocumentChunk包含：唯一标识、文档ID、文本内容、嵌入向量、来源、索引位置和总块数
         */
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = UUID.randomUUID().toString();
            DocumentChunk chunk = new DocumentChunk(
                chunkId,
                documentId,
                chunks.get(i),
                embeddings.get(i),
                source,
                i,
                chunks.size()
            );
            documentChunks.add(chunk);
        }
        
        return documentChunks;
    }
}
