package com.example.rag;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }

    /**
     * Swagger/OpenAPI 文档入口配置
     * Swagger UI:     http://localhost:8081/swagger-ui.html
     * API规范JSON:    http://localhost:8081/v3/api-docs (可在Postman中 Import -> Link 直接导入)
     */
    @Bean
    public OpenAPI ragOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RAG PDF 问答系统 API")
                        .description("RAG 文档上传、问答与向量库管理接口文档\n\n"
                                + "**Postman 导入方式：** Postman -> Import -> Link 填入 [/v3/api-docs](/v3/api-docs)")
                        .version("v1.0.0")
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }

    @Component
    static class StartupListener implements ApplicationListener<ApplicationReadyEvent> {
        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            Environment env = event.getApplicationContext().getEnvironment();
            String port = env.getProperty("server.port", "8080");
            String contextPath = env.getProperty("server.servlet.context-path", "");
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🚀 RAG PDF问答系统启动成功！");
            System.out.println("=".repeat(60));
            System.out.println("📱 前端访问地址: http://localhost:" + port + contextPath + "/");
            System.out.println("📚 Swagger API文档: http://localhost:" + port + contextPath + "/swagger-ui.html");
            System.out.println("📄 OpenAPI规范(JSON): http://localhost:" + port + contextPath + "/v3/api-docs (Postman导入: Import -> Link)");
            System.out.println("📄 上传API: POST http://localhost:" + port + contextPath + "/api/rag/upload");
            System.out.println("💬 问答API: GET http://localhost:" + port + contextPath + "/api/rag/chat?message=xxx");
            System.out.println("📊 Prometheus指标: http://localhost:" + port + contextPath + "/actuator/prometheus");
            System.out.println("🗑️ 向量数据库地址: http://localhost:8000");
            System.out.println("=".repeat(60) + "\n");
        }
    }
}
