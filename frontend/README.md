# RAG Vue 前端

这是 RAG 问答系统的 Vue 3 + Vite 前端。后端 API 路径保持不变，仍然是 `/api/rag/*`。

## 开发

```bash
npm install
npm run dev
```

开发服务器默认运行在 `http://localhost:5173`，Vite 会把 `/api` 请求代理到 `http://localhost:8081`。

## 构建

```bash
npm run build
```

构建结果会直接写入 `src/main/resources/static`，因此 Spring Boot 启动后访问 `http://localhost:8081/` 就能打开页面。`src/App.vue` 中的注释说明了状态管理和各接口调用的位置。
