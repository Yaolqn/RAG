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
    
    private static final int CHUNK_SIZE = 500;  // 文档块大小
    private static final int CHUNK_OVERLAP = 50;  // 文档块重叠大小

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
 * 将文本分割成多个块
 * @param text 输入文本
 * @return 文本块列表
 */
    public List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        
        // 按句子分割文本
        String[] sentences = text.split("(?<=[。！？.!?])\\s*");
        StringBuilder currentChunk = new StringBuilder();
        
        // 遍历所有句子，按CHUNK_SIZE限制进行分块
        // 当当前块加上下一句超过大小时，保存当前块并开始新块
        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() > CHUNK_SIZE) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                }
                currentChunk = new StringBuilder(sentence);
            } else {
                currentChunk.append(sentence);
            }
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        
        return chunks;
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
        List<String> chunks = splitText(text);
        List<DocumentChunk> documentChunks = new ArrayList<>();
        
        /*
         * 遍历所有文本块，为每个块生成唯一ID并创建DocumentChunk对象
         * 每个DocumentChunk包含：唯一标识、文本内容、嵌入向量、来源、索引位置和总块数
         */
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = UUID.randomUUID().toString();
            DocumentChunk chunk = new DocumentChunk(
                chunkId,
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
