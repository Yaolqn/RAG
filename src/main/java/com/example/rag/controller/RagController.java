package com.example.rag.controller;

import com.example.rag.model.DocumentChunk;
import com.example.rag.service.DocumentService;
import com.example.rag.service.EmbeddingService;
import com.example.rag.service.RagService;
import com.example.rag.service.VectorStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG REST API 控制器
 * 提供文档上传、问答、向量库管理等 API 接口
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    @Autowired
    private DocumentService documentService;  // 文档服务

    @Autowired
    private EmbeddingService embeddingService;  // 嵌入服务

    @Autowired
    private VectorStoreService vectorStoreService;  // 向量存储服务

    @Autowired
    private RagService ragService;  // RAG 问答服务

    /**
 * 上传文档并生成嵌入向量
 * @param file 上传的文件
 * @return 上传结果
 */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            System.out.println("开始上传文档: " + file.getOriginalFilename());
            // 提取文档文本
            String text = documentService.extractText(file);
            System.out.println("文档提取成功，文本长度: " + text.length());
            
            // 分割文档为块
            List<String> chunks = documentService.splitText(text);
            System.out.println("文档分块完成，共 " + chunks.size() + " 个块");
            
            // 生成嵌入向量
            List<List<Float>> embeddings = embeddingService.generateEmbeddings(chunks);
            System.out.println("向量生成完成，共 " + embeddings.size() + " 个向量");
            
            // 创建文档块并存储到向量库
            List<DocumentChunk> documentChunks = documentService.createDocumentChunks(
                text, file.getOriginalFilename(), embeddings);
            vectorStoreService.addChunks(documentChunks);

            // 获取文档ID
            String documentId = documentChunks.isEmpty() ? null : documentChunks.get(0).getDocumentId();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "文档上传成功");
            response.put("documentId", documentId);
            response.put("filename", file.getOriginalFilename());
            response.put("chunks", chunks.size());
            response.put("totalChunks", vectorStoreService.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "文档上传失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
 * 基于检索增强生成回答用户问题
 * @param message 用户问题
 * @param documentId 文档ID（可选，如果指定则只在该文档内搜索）
 * @return 生成的答案
 */
    @GetMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestParam("message") String message,
            @RequestParam(value = "documentId", required = false) String documentId) {
        try {
            String response = ragService.chat(message, documentId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("response", response);
            result.put("documentId", documentId);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "问答失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
 * 清空向量存储
 * @return 清空结果
 */
    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearVectorStore() {
        vectorStoreService.clearAll();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "向量库已清空");

        return ResponseEntity.ok(response);
    }

    /**
 * 获取向量库状态
 * @return 状态信息
 */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("totalChunks", vectorStoreService.size());
        status.put("documents", vectorStoreService.getAllDocumentIds());
        status.put("success", true);

        return ResponseEntity.ok(status);
    }

    /**
 * 获取指定文档的状态
 * @param documentId 文档ID
 * @return 文档状态信息
 */
    @GetMapping("/document/{documentId}/status")
    public ResponseEntity<Map<String, Object>> getDocumentStatus(@PathVariable String documentId) {
        Map<String, Object> status = new HashMap<>();
        status.put("documentId", documentId);
        status.put("chunks", vectorStoreService.size(documentId));
        status.put("success", true);

        return ResponseEntity.ok(status);
    }

    /**
 * 删除指定文档
 * @param documentId 文档ID
 * @return 删除结果
 */
    @DeleteMapping("/document/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String documentId) {
        try {
            vectorStoreService.deleteDocument(documentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "文档删除成功");
            response.put("documentId", documentId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "文档删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
 * 获取所有文档列表
 * @return 文档列表
 */
    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> getAllDocuments() {
        try {
            List<String> documentIds = vectorStoreService.getAllDocumentIds();
            Map<String, String> filenameMap = vectorStoreService.getDocumentIdToFilenameMap();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("documents", documentIds);
            response.put("filenameMap", filenameMap);
            response.put("count", documentIds.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取文档列表失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
