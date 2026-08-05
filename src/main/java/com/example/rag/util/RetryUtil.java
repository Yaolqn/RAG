package com.example.rag.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 重试工具类
 * 实现指数退避重试机制，用于处理API调用失败的情况
 */
@Component
public class RetryUtil {

    @Value("${retry.enabled:true}")
    private boolean retryEnabled;

    @Value("${retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${retry.initial-delay:1000}")
    private long initialDelay;

    @Value("${retry.max-delay:10000}")
    private long maxDelay;

    @Value("${retry.multiplier:2.0}")
    private double multiplier;

    /**
     * 执行带重试的操作
     * @param operation 要执行的操作
     * @param operationName 操作名称（用于日志）
     * @param <T> 返回类型
     * @return 操作结果
     * @throws Exception 重试失败后抛出异常
     */
    public <T> T executeWithRetry(Supplier<T> operation, String operationName) throws Exception {
        if (!retryEnabled) {
            return operation.get();
        }

        Exception lastException = null;
        long currentDelay = initialDelay;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                
                // 检查是否应该重试（某些异常可能不适合重试）
                if (!shouldRetry(e)) {
                    throw e;
                }

                if (attempt < maxAttempts) {
                    System.out.printf("%s 失败（第%d次尝试），%dms后重试... 错误: %s%n",
                            operationName, attempt, currentDelay, e.getMessage());
                    
                    try {
                        TimeUnit.MILLISECONDS.sleep(currentDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }

                    // 计算下一次延迟（指数退避）
                    currentDelay = Math.min((long) (currentDelay * multiplier), maxDelay);
                }
            }
        }

        System.out.printf("%s 重试%d次后仍然失败%n", operationName, maxAttempts);
        throw lastException;
    }

    /**
     * 执行带重试的操作（带降级处理）
     * @param operation 要执行的操作
     * @param fallback 降级操作
     * @param operationName 操作名称
     * @param <T> 返回类型
     * @return 操作结果或降级结果
     */
    public <T> T executeWithRetryAndFallback(Supplier<T> operation, Supplier<T> fallback, String operationName) {
        try {
            return executeWithRetry(operation, operationName);
        } catch (Exception e) {
            System.out.printf("%s 失败，执行降级处理%n", operationName);
            return fallback.get();
        }
    }

    /**
     * 判断异常是否应该重试
     * @param exception 异常
     * @return 是否应该重试
     */
    private boolean shouldRetry(Exception exception) {
        // 网络相关异常通常可以重试
        String message = exception.getMessage();
        if (message == null) {
            return true;
        }

        String lowerMessage = message.toLowerCase();
        
        // 以下情况不应该重试
        if (lowerMessage.contains("invalid") || 
            lowerMessage.contains("unauthorized") ||
            lowerMessage.contains("forbidden") ||
            lowerMessage.contains("not found") ||
            lowerMessage.contains("authentication")) {
            return false;
        }

        // 以下情况应该重试
        if (lowerMessage.contains("timeout") ||
            lowerMessage.contains("connection") ||
            lowerMessage.contains("network") ||
            lowerMessage.contains("rate limit") ||
            lowerMessage.contains("too many requests") ||
            lowerMessage.contains("503") ||
            lowerMessage.contains("502") ||
            lowerMessage.contains("504")) {
            return true;
        }

        // 默认情况下重试
        return true;
    }

    /**
     * 获取友好的错误消息
     * @param exception 异常
     * @return 友好的错误消息
     */
    public String getFriendlyErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return "服务暂时不可用，请稍后重试";
        }

        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("timeout")) {
            return "服务响应超时，请稍后重试";
        }
        if (lowerMessage.contains("connection")) {
            return "网络连接失败，请检查网络后重试";
        }
        if (lowerMessage.contains("rate limit") || lowerMessage.contains("too many requests")) {
            return "服务繁忙，请稍后重试";
        }
        if (lowerMessage.contains("unauthorized") || lowerMessage.contains("authentication")) {
            return "认证失败，请检查API密钥配置";
        }
        if (lowerMessage.contains("invalid")) {
            return "请求参数错误，请检查输入";
        }

        return "服务暂时不可用，请稍后重试";
    }
}
