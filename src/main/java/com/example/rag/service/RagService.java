package com.example.rag.service;

import com.example.rag.model.DocumentChunk;
import com.example.rag.util.RetryUtil;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 问答服务
 * 整合检索和生成，基于检索到的文档内容回答用户问题
 */
@Service
public class RagService {

    @Autowired
    private RetrievalService retrievalService;  // 检索服务

    @Autowired
    private ArkService arkService;  // 火山引擎 Ark 服务

    @Autowired
    private RetryUtil retryUtil;  // 重试工具

    @Autowired
    private MetricsService metricsService;  // 指标服务

    @Value("${volcengine.chat.model}")
    private String chatModel;  // 聊天模型 ID
    
    @Value("${retrieval.top-k:3}")
    private int defaultTopK;  // 默认检索返回的最相关文档块数量

    /**
 * 基于检索增强生成回答用户问题
 * @param query 用户问题
 * @return 生成的答案
 */
    public String chat(String query) {
        return chat(query, null);
    }

    /**
 * 基于检索增强生成回答用户问题（支持文档隔离）
 * @param query 用户问题
 * @param documentId 文档ID（可选，如果指定则只在该文档内搜索）
 * @return 生成的答案
 */
    public String chat(String query, String documentId) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        
        try {
            // 检索相关文档块
            List<DocumentChunk> relevantChunks = retrievalService.retrieve(query, defaultTopK, documentId);
            // 格式化检索到的上下文
            String context = retrievalService.formatContext(relevantChunks);

//            // 构建提示词模板
//            // 回答模板具有溯源功能
//            String promptTemplate = """
//               【系统提示】
//               你是基于内部文档的问答助手。你的回答必须完全基于给定【文档内容】，并遵循以下规则：
//               1. 在回答中穿插引用来源，格式为【来源：文档X第Y段】或【参考：<原文摘录>】。
//               2. 如果信息来自多个片段，请分别注明。
//               3. 如果【文档内容】中没有相关信息，回复：“抱歉，当前文档中未包含相关信息。”，禁止使用外部知识。
//               4. 回答应条理清晰，优先使用列表或分步骤说明，保持简洁回答。
//               5. 回答时，每表达完一个完整意思，必须强制空出一行（即按两次回车键）。严禁出现超过4行没有换行的大段文字。
//
//               【用户消息】
//               文档内容：
//               %s
//
//               用户问题：%s
//               """.formatted(context, query);

            // 构建聊天消息
            List<ChatMessage> messages = new ArrayList<>();
            
            // 系统提示（单独设置，权重更高）
            messages.add(ChatMessage.builder()
                    .role(ChatMessageRole.SYSTEM)
                    .content("""
                    你是基于内部文档的问答助手。你的回答必须完全基于给定文档内容，并严格遵循以下格式规则：
                    
                    1. 每表达完一个完整意思后，必须空一行（即按两次回车键）
                    2. 严禁出现超过3行连续的文本，必须分段
                    3. 优先使用列表形式，用数字或符号标记
                    4. 在回答中穿插引用来源，格式为【来源：文档X第Y段】或【参考：<原文摘录>】。
                    5. 如果文档中没有相关信息，回复："抱歉，当前文档中未包含相关信息。"
                    6. 保持回答简洁，避免冗长
                    
                    回答格式示例：
                    
                    根据文档内容，答案如下：
                    
                    第一点：xxx【来源：文档A】
                    
                    第二点：xxx【来源：文档B】
                    
                    第三点：xxx【来源：文档C】
                    
                    以上就是相关内容。
                    """)
                    .build());
            
            // 用户消息
            messages.add(ChatMessage.builder()
                    .role(ChatMessageRole.USER)
                    .content(String.format("""
                    文档内容：
                    %s
                    
                    用户问题：%s
                    """, context, query))
                    .build());

            // 构建聊天完成请求
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(chatModel)
                    .messages(messages)
                    .build();

            System.out.println("开始调用聊天API，模型: " + chatModel);
            System.out.println("提示词长度: " + context.length() + " 字符");

            // 调用火山引擎聊天 API 生成答案（带重试）
            var response = retryUtil.executeWithRetry(
                    () -> arkService.createChatCompletion(request),
                    "聊天API调用"
            );
            
            System.out.println("聊天API调用成功");
            Object content = response.getChoices().get(0).getMessage().getContent();
            success = true;
            
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordRagRequest(duration, true);
            System.out.println("RAG问答总耗时: " + duration + "ms");
            
            return content != null ? content.toString() : "";
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordRagRequest(duration, false);
            System.err.println("RAG问答失败: " + e.getMessage());
            e.printStackTrace();
            return "抱歉，" + retryUtil.getFriendlyErrorMessage(e);
        }
    }
}
