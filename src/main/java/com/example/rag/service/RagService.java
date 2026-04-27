package com.example.rag.service;

import com.example.rag.model.DocumentChunk;
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

    @Value("${volcengine.chat.model}")
    private String chatModel;  // 聊天模型 ID

    /**
 * 基于检索增强生成回答用户问题
 * @param query 用户问题
 * @return 生成的答案
 */
    public String chat(String query) {
        // 检索相关文档块
        List<DocumentChunk> relevantChunks = retrievalService.retrieve(query, 3);
        // 格式化检索到的上下文
        String context = retrievalService.formatContext(relevantChunks);

        // 构建提示词模板
        String promptTemplate = """
            你是一个专业的知识库助手。请根据以下参考信息回答用户的问题。
            
            参考信息：
            %s
            
            用户问题：
            %s
            
            如果参考信息中没有相关内容，请明确告知用户，不要编造答案。
            """.formatted(context, query);

        // 构建聊天消息
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder()
                .role(ChatMessageRole.USER)
                .content(promptTemplate)
                .build());

        // 构建聊天完成请求
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(chatModel)
                .messages(messages)
                .build();

        // 调用火山引擎聊天 API 生成答案
        try {
            Object content = arkService.createChatCompletion(request).getChoices().get(0).getMessage().getContent();
            return content != null ? content.toString() : "";
        } catch (Exception e) {
            System.err.println("聊天API调用失败: " + e.getMessage());
            e.printStackTrace();
            return "抱歉，生成答案时出错: " + e.getMessage();
        }
    }
}
