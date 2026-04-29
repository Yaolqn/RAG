package com.example.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(RagApplication.class);
        app.addListeners(new StartupListener());
        app.run(args);
    }

    @Component
    static class StartupListener implements ApplicationListener<ContextRefreshedEvent> {
        @Override
        public void onApplicationEvent(ContextRefreshedEvent event) {
            Environment env = event.getApplicationContext().getEnvironment();
            String port = env.getProperty("server.port", "8080");
            String contextPath = env.getProperty("server.servlet.context-path", "");
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🚀 RAG PDF问答系统启动成功！");
            System.out.println("=".repeat(60));
            System.out.println("📱 前端访问地址: http://localhost:" + port + contextPath + "/");
            System.out.println("📄 上传API: POST http://localhost:" + port + contextPath + "/api/rag/upload");
            System.out.println("💬 问答API: GET http://localhost:" + port + contextPath + "/api/rag/chat?message=xxx");
            System.out.println("🗑️ 向量数据库地址: http://localhost:8000");
            System.out.println("=".repeat(60) + "\n");
        }
    }
}
