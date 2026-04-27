# RAG PDF 问答系统

基于 Spring Boot 和火山引擎（豆包）的检索增强生成（RAG）系统，支持上传 PDF 文档并进行智能问答。

## 项目概述

本项目实现了一个完整的 RAG（Retrieval-Augmented Generation）系统，通过以下流程实现文档问答：

1. **文档上传**：用户上传 PDF 文档
2. **文本提取**：使用 Apache Tika 提取文档文本
3. **文档分块**：将文本分割成多个小块
4. **向量生成**：调用火山引擎 API 生成文本嵌入向量
5. **向量存储**：将文档块和向量存储在内存中
6. **相似度检索**：根据用户问题检索相关文档块
7. **答案生成**：基于检索到的上下文生成答案

## 技术栈

- **Java**: 21
- **Spring Boot**: 3.2.5
- **火山引擎 SDK**: volcengine-java-sdk-ark-runtime 2.0.0
- **Spring AI**: spring-ai-openai-spring-boot-starter 1.0.0-M4
- **Apache Tika**: 2.9.1（文档解析）
- **Apache Commons Math**: 3.6.1（向量相似度计算）

## 项目结构

```
RAG/
├── src/main/java/com/example/rag/
│   ├── RagApplication.java              # 主应用类
│   ├── config/
│   │   ├── EmbeddingConfig.java        # 火山引擎配置
│   │   └── MultiModalEmbeddingsExample.java  # 多模态嵌入示例
│   ├── controller/
│   │   └── RagController.java           # REST API 控制器
│   ├── model/
│   │   └── DocumentChunk.java           # 文档块模型
│   └── service/
│       ├── DocumentService.java         # 文档处理服务
│       ├── EmbeddingService.java       # 嵌入向量服务
│       ├── RagService.java              # RAG 问答服务
│       ├── RetrievalService.java        # 检索服务
│       └── VectorStoreService.java      # 向量存储服务
├── src/main/resources/
│   ├── application.yml                 # 主配置文件
│   └── application.properties          # 应用属性
└── pom.xml                              # Maven 依赖配置
```

## 配置文件说明

### pom.xml

Maven 项目配置文件，定义了项目依赖：

- **spring-boot-starter-web**: Spring Boot Web 基础依赖
- **volcengine-java-sdk-ark-runtime**: 火山引擎 Ark 运行时 SDK
- **spring-ai-openai-spring-boot-starter**: Spring AI OpenAI 兼容接口
- **tika-core & tika-parsers-standard-package**: Apache Tika 文档解析
- **commons-math3**: 向量相似度计算

### application.yml

主配置文件，包含以下配置：

```yaml
spring:
  application:
    name: RAG
  ai:
    openai:
      base-url: https://ark.cn-beijing.volces.com/api/v3  # 火山引擎网关地址
      api-key: ${VOLCENGINE_API_KEY}  # API Key
      embedding:
        enabled: false  # 禁用自动配置，使用自定义实现
      chat:
        options:
          model: ${CHAT_MODEL}  # 聊天模型端点 ID
  servlet:
    multipart:
      max-file-size: 10MB  # 最大文件上传大小
      max-request-size: 10MB

volcengine:
  api-key: ${ARK_API_KEY}  # 火山引擎 API Key
  embedding:
    model: ep-20260420014217-l6bqr  # 嵌入模型端点 ID
  chat:
    model: ep-20260419235315-sv4kp  # 聊天模型端点 ID

server:
  port: 8081  # 服务端口
```

### application.properties

简单的应用属性文件，仅包含应用名称。

## 类和函数说明

### 1. RagApplication.java

**作用**: Spring Boot 主应用类，负责启动应用程序。

**主要类和方法**:
- `RagApplication`: 主应用类
  - `main(String[] args)`: 应用程序入口，启动 Spring Boot 应用
- `StartupListener`: 启动监听器
  - `onApplicationEvent(ContextRefreshedEvent)`: 应用启动完成后打印访问地址和 API 信息

---

### 2. EmbeddingConfig.java

**作用**: 配置火山引擎 Ark 服务和模型参数。

**主要类和方法**:
- `EmbeddingConfig`: 配置类
  - `arkService()`: 配置 ArkService Bean，设置连接池和 API Key
  - `embeddingModel()`: 配置嵌入模型 ID Bean
  - `chatModel()`: 配置聊天模型 ID Bean

**配置参数**:
- `volcengine.api-key`: 火山引擎 API Key
- `volcengine.embedding.model`: 嵌入模型端点 ID
- `volcengine.chat.model`: 聊天模型端点 ID

---

### 3. MultiModalEmbeddingsExample.java

**作用**: 多模态嵌入向量生成的示例代码（独立运行）。

**主要类和方法**:
- `MultiModalEmbeddingsExample`: 示例类
  - `main(String[] args)`: 演示如何调用多模态嵌入 API 生成文本向量

**功能**: 展示如何使用火山引擎 SDK 生成文本的嵌入向量，可作为独立测试程序运行。

---

### 4. RagController.java

**作用**: REST API 控制器，提供文档上传、问答、向量库管理等接口。

**主要类和方法**:
- `RagController`: 控制器类
  - `uploadDocument(MultipartFile file)`: 上传文档并生成嵌入向量
    - 输入: MultipartFile 文件
    - 输出: 包含成功状态、消息、块数量的 Map
    - 流程: 提取文本 → 分块 → 生成向量 → 存储到向量库
  - `chat(String message)`: 基于检索增强生成回答用户问题
    - 输入: 用户问题 message
    - 输出: 包含成功状态和答案的 Map
  - `clearVectorStore()`: 清空向量存储
    - 输出: 包含成功状态的 Map
  - `getStatus()`: 获取向量库状态
    - 输出: 包含总块数量的 Map

**API 端点**:
- `POST /api/rag/upload`: 上传文档
- `GET /api/rag/chat?message=xxx`: 问答
- `POST /api/rag/clear`: 清空向量库
- `GET /api/rag/status`: 获取状态

---

### 5. DocumentChunk.java

**作用**: 文档块模型类，表示文档的一个分块。

**字段**:
- `id`: 文档块唯一 ID（UUID）
- `content`: 文档块内容
- `embedding`: 嵌入向量（List<Float>）
- `source`: 文档来源（文件名）
- `chunkIndex`: 文档块索引
- `totalChunks`: 总文档块数
- `similarity`: 相似度分数

**方法**:
- 标准的 getter/setter 方法

---

### 6. DocumentService.java

**作用**: 文档处理服务，负责文本提取、分块和文档块创建。

**主要类和方法**:
- `DocumentService`: 文档服务类
  - `extractText(MultipartFile file)`: 从上传的文件中提取文本
    - 输入: MultipartFile 文件
    - 输出: 提取的文本内容
    - 实现: 使用 Apache Tika 解析文档
  - `splitText(String text)`: 将文本分割成多个块
    - 输入: 输入文本
    - 输出: 文本块列表
    - 实现: 按句子分割，每块最大 500 字符
  - `createDocumentChunks(String text, String source, List<List<Float>> embeddings)`: 创建文档块对象列表
    - 输入: 文本内容、来源、嵌入向量列表
    - 输出: DocumentChunk 对象列表
    - 实现: 为每个文本块生成唯一 ID 并创建 DocumentChunk 对象

**常量**:
- `CHUNK_SIZE = 500`: 文档块大小
- `CHUNK_OVERLAP = 50`: 文档块重叠大小（当前未使用）

---

### 7. EmbeddingService.java

**作用**: 嵌入向量服务，调用火山引擎 API 生成文本的嵌入向量。

**主要类和方法**:
- `EmbeddingService`: 嵌入服务类
  - `generateEmbedding(String text)`: 生成单个文本的嵌入向量
    - 输入: 输入文本
    - 输出: 嵌入向量（List<Float>）
    - 实现: 调用火山引擎多模态嵌入 API
  - `generateEmbeddings(List<String> texts)`: 批量生成文本的嵌入向量
    - 输入: 文本列表
    - 输出: 二维浮点数列表
    - 实现: 遍历文本列表，逐个调用 generateEmbedding

**依赖**:
- `ArkService`: 火山引擎 Ark 服务
- `volcengine.embedding.model`: 嵌入模型 ID

---

### 8. RagService.java

**作用**: RAG 问答服务，整合检索和生成，基于检索到的文档内容回答用户问题。

**主要类和方法**:
- `RagService`: RAG 服务类
  - `chat(String query)`: 基于检索增强生成回答用户问题
    - 输入: 用户问题
    - 输出: 生成的答案
    - 流程: 检索相关文档块 → 格式化上下文 → 构建提示词 → 调用聊天 API

**提示词模板**:
```
你是一个专业的知识库助手。请根据以下参考信息回答用户的问题。

参考信息：
%s

用户问题：
%s

如果参考信息中没有相关内容，请明确告知用户，不要编造答案。
```

**依赖**:
- `RetrievalService`: 检索服务
- `ArkService`: 火山引擎 Ark 服务
- `volcengine.chat.model`: 聊天模型 ID

---

### 9. RetrievalService.java

**作用**: 检索服务，根据用户查询检索相关文档块。

**主要类和方法**:
- `RetrievalService`: 检索服务类
  - `retrieve(String query, int topK)`: 根据查询检索相关文档块
    - 输入: 用户查询、返回数量
    - 输出: 相关文档块列表
    - 流程: 生成查询向量 → 相似度搜索
  - `formatContext(List<DocumentChunk> chunks)`: 格式化检索到的文档块为上下文字符串
    - 输入: 文档块列表
    - 输出: 格式化的上下文字符串
    - 格式: `[来源: xxx]\n内容`

**依赖**:
- `EmbeddingService`: 嵌入服务
- `VectorStoreService`: 向量存储服务

---

### 10. VectorStoreService.java

**作用**: 向量存储服务，使用内存存储文档块及其嵌入向量，提供相似度搜索功能。

**主要类和方法**:
- `VectorStoreService`: 向量存储服务类
  - `addChunks(List<DocumentChunk> documentChunks)`: 添加文档块到向量存储
    - 输入: 文档块列表
    - 实现: 存储到 ConcurrentHashMap 和 ArrayList
  - `similaritySearch(List<Float> queryEmbedding, int topK)`: 基于余弦相似度搜索相关文档块
    - 输入: 查询嵌入向量、返回数量
    - 输出: 按相似度排序的文档块列表
    - 实现: 计算余弦相似度 → 排序 → 返回 topK
  - `clear()`: 清空向量存储
  - `size()`: 获取存储的文档块数量
  - `cosineSimilarity(List<Float> vectorA, List<Float> vectorB)`: 计算两个向量的余弦相似度
    - 输入: 两个向量
    - 输出: 余弦相似度（0-1 之间）
    - 公式: `similarity = (A · B) / (||A|| × ||B||)`

**数据结构**:
- `ConcurrentHashMap<String, DocumentChunk> chunkStore`: 按 ID 索引的文档块存储
- `List<DocumentChunk> chunks`: 文档块列表（用于遍历搜索）

---

## 数据传输流程

### 1. 文档上传流程

```
用户上传 PDF
    ↓
RagController.uploadDocument()
    ↓
DocumentService.extractText() → 提取文本
    ↓
DocumentService.splitText() → 分割成块
    ↓
EmbeddingService.generateEmbeddings() → 生成向量
    ↓
DocumentService.createDocumentChunks() → 创建文档块对象
    ↓
VectorStoreService.addChunks() → 存储到内存
    ↓
返回上传结果
```

### 2. 问答流程

```
用户提问
    ↓
RagController.chat()
    ↓
RagService.chat()
    ↓
RetrievalService.retrieve()
    ↓
EmbeddingService.generateEmbedding() → 生成问题向量
    ↓
VectorStoreService.similaritySearch() → 相似度搜索
    ↓
RetrievalService.formatContext() → 格式化上下文
    ↓
构建提示词
    ↓
调用火山引擎聊天 API
    ↓
返回答案
```

### 3. 数据流向

**上传阶段**:
- PDF 文件 → 文本 → 文本块 → 嵌入向量 → DocumentChunk 对象 → 内存存储

**问答阶段**:
- 用户问题 → 问题向量 → 相似度计算 → 相关文档块 → 上下文 → 提示词 → 答案

## 运行指南

### 前置要求

- Java 21
- Maven 3.x
- 火山引擎 API Key
- 火山引擎推理端点 ID（嵌入模型和聊天模型）

### 配置步骤

1. 获取火山引擎 API Key 和推理端点 ID
2. 修改 `application.yml` 中的配置：
   - `spring.ai.openai.api-key`: 设置你的 API Key
   - `volcengine.api-key`: 设置你的 API Key
   - `volcengine.embedding.model`: 设置嵌入模型端点 ID
   - `volcengine.chat.model`: 设置聊天模型端点 ID

### 运行项目

```bash
# 使用 Maven 运行
mvn spring-boot:run

# 或使用 Maven Wrapper
./mvnw spring-boot:run
```

### 访问应用

启动成功后，控制台会显示访问地址：

```
📱 前端访问地址: http://localhost:8081/
📄 上传API: POST http://localhost:8081/api/rag/upload
💬 问答API: GET http://localhost:8081/api/rag/chat?message=xxx
```

### API 使用示例

**上传文档**:
```bash
curl -X POST http://localhost:8081/api/rag/upload \
  -F "file=@your_document.pdf"
```

**问答**:
```bash
curl "http://localhost:8081/api/rag/chat?message=你的问题"
```

**清空向量库**:
```bash
curl -X POST http://localhost:8081/api/rag/clear
```

**获取状态**:
```bash
curl http://localhost:8081/api/rag/status
```

## 注意事项

1. **API Key 安全**: 不要将 API Key 提交到版本控制系统，建议使用环境变量
2. **内存限制**: 当前使用内存存储向量，重启应用后数据会丢失
3. **文件大小限制**: 默认最大上传 10MB，可在 `application.yml` 中调整
4. **并发处理**: 使用 ConcurrentHashMap 保证线程安全
5. **向量维度**: 确保嵌入模型和聊天模型兼容

## 扩展建议

1. **持久化存储**: 可集成 Redis、Milvus 等向量数据库
2. **前端界面**: 开发 Web 前端或移动应用
3. **多文档支持**: 支持批量上传和文档管理
4. **用户认证**: 添加用户认证和权限管理
5. **日志监控**: 集成日志系统和监控工具
6. **性能优化**: 添加缓存、异步处理等优化

## 许可证

本项目仅供学习和研究使用。
