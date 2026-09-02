package com.example.rag;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        @Value("${frontend.dev.enabled:true}")
        private boolean frontendDevEnabled;

        @Value("${frontend.dev.directory:frontend}")
        private String frontendDirectory;

        @Value("${frontend.dev.port:5173}")
        private int frontendPort;

        private Process frontendProcess;

        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            Environment env = event.getApplicationContext().getEnvironment();
            String port = env.getProperty("server.port", "8080");
            String contextPath = env.getProperty("server.servlet.context-path", "");

            // Spring 启动完成后再启动 Vite。Vite 会将 /api 请求代理到后端，
            // 因此 Vue 页面可以在 5173 端口独立开发和热更新。
            String frontendUrl = startFrontendDevServer();
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🚀 RAG PDF问答系统启动成功！");
            System.out.println("=".repeat(60));
            System.out.println("📱 前端访问地址: http://localhost:" + port + contextPath + "/");
            if (frontendUrl != null) {
                System.out.println("🟢 Vue开发地址: " + frontendUrl);
            }
            System.out.println("📚 Swagger API文档: http://localhost:" + port + contextPath + "/swagger-ui.html");
            System.out.println("📄 OpenAPI规范(JSON): http://localhost:" + port + contextPath + "/v3/api-docs (Postman导入: Import -> Link)");
            System.out.println("📄 上传API: POST http://localhost:" + port + contextPath + "/api/rag/upload");
            System.out.println("💬 问答API: GET http://localhost:" + port + contextPath + "/api/rag/chat?message=xxx");
            System.out.println("📊 Prometheus指标: http://localhost:" + port + contextPath + "/actuator/prometheus");
            System.out.println("🗑️ 向量数据库地址: http://localhost:8000");
            System.out.println("=".repeat(60) + "\n");
        }

        /**
         * 启动 Vue/Vite 开发服务器。
         * 生产环境可以将 frontend.dev.enabled 设为 false，直接使用 Spring
         * Boot 静态目录中由 npm run build 生成的页面。
         */
        private String startFrontendDevServer() {
            if (!frontendDevEnabled) {
                System.out.println("Vue开发服务器已禁用，使用Spring Boot静态页面。");
                return null;
            }

            Path directory = Paths.get(frontendDirectory).toAbsolutePath().normalize();
            if (!Files.isDirectory(directory) || !Files.exists(directory.resolve("package.json"))) {
                System.out.println("未找到Vue项目目录: " + directory + "，跳过启动Vite。");
                return null;
            }

            String npmCommand = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "npm.cmd" : "npm";
            try {
                frontendProcess = new ProcessBuilder(
                        npmCommand, "run", "dev", "--", "--host", "127.0.0.1", "--port", String.valueOf(frontendPort))
                        .directory(directory.toFile())
                        .redirectErrorStream(true)
                        .start();

                streamFrontendLogs(frontendProcess);
                return "http://localhost:" + frontendPort + "/";
            } catch (IOException e) {
                System.out.println("启动Vue开发服务器失败: " + e.getMessage());
                System.out.println("请确认已安装Node.js/npm，或访问Spring Boot页面: http://localhost:"
                        + System.getProperty("server.port", "8081") + "/");
                return null;
            }
        }

        /** 将 Vite 输出转发到 Spring 控制台，便于看到编译和代理错误。 */
        private void streamFrontendLogs(Process process) {
            Thread logThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[Vue] " + line);
                    }
                } catch (IOException e) {
                    if (process.isAlive()) {
                        System.out.println("读取Vue开发服务器日志失败: " + e.getMessage());
                    }
                }
            }, "vue-dev-server-logs");
            logThread.setDaemon(true);
            logThread.start();
        }

        /** 应用退出时关闭 Vite 及其子进程，避免 npm/node 残留。 */
        @PreDestroy
        public void stopFrontendDevServer() {
            if (frontendProcess == null || !frontendProcess.isAlive()) {
                return;
            }
            ProcessHandle processHandle = frontendProcess.toHandle();
            processHandle.descendants().forEach(ProcessHandle::destroy);
            processHandle.destroy();
            try {
                if (!frontendProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    processHandle.descendants().forEach(ProcessHandle::destroyForcibly);
                    processHandle.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                processHandle.destroyForcibly();
            }
        }
    }
}
