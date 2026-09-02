# RAG PDF 问答系统

基于 Spring Boot 和火山引擎（豆包）的检索增强生成（RAG）系统，支持上传文档并进行智能问答。

## 项目概述

本项目实现了一个完整的 RAG（Retrieval-Augmented Generation）系统，通过以下流程实现文档问答：

1. **文档上传**：用户上传 PDF 等文档，系统为每个文档生成唯一ID
2. **文本提取**：使用 Apache Tika 提取文档文本
3. **文档分块**：将文本按段落/句子边界动态分割成多个小块（带重叠）
4. **向量生成**：调用火山引擎多模态 Embedding API 生成文本嵌入向量
5. **向量存储**：将文档块和向量存储在 Milvus 向量数据库（单集合架构，通过 document_id 字段实现文档隔离）
6. **相似度检索**：根据用户问题检索相关文档块（支持文档级隔离）
7. **混合检索（可选）**：向量检索 + BM25 关键词检索加权融合
8. **重排序**：对检索结果进行关键词或语义重排序，提升相关性
9. **答案生成**：基于检索到的上下文生成答案（支持引用溯源）

**核心特性**：
- **文档隔离**：支持多文档管理，每个文档独立存储和检索
- **Web前端界面**：提供完整的Web界面，支持文档上传、管理、问答
- **单集合架构**：使用单个 Milvus 集合，通过 document_id 字段实现逻辑隔离
- **Redis缓存**：嵌入向量缓存，减少API调用，提升性能
- **混合检索**：向量语义检索 + BM25 关键词检索加权融合（可配置开关与权重）
- **重排序机制**：支持关键词匹配和语义相似度重排序，提升检索质量
- **指数退避重试**：API调用失败时自动重试，提高系统可靠性
- **指标监控**：集成 Micrometer + Prometheus，记录检索延迟、缓存命中率等指标
- **结构化日志**：生产环境支持 JSON 格式日志输出，便于日志分析
- **Swagger API 文档**：内置 OpenAPI 规范文档，支持导入 Postman

## 技术栈

- **Java**: 21
- **Spring Boot**: 3.2.5
- **火山引擎 SDK**: volcengine-java-sdk-ark-runtime 2.0.0
- **Spring AI**: spring-ai-openai-spring-boot-starter 1.0.0-M4
- **Apache Tika**: 2.9.1（文档解析）
- **Apache Commons Math**: 3.6.1（向量相似度计算）
- **Milvus**: 2.4+（向量数据库，SDK 2.6.18）
- **Redis**: 6.0+（缓存系统）
- **Apache Lucene**: 8.11.3（BM25 关键词检索）
- **SpringDoc OpenAPI**: 2.5.0（Swagger API 文档）
- **Gson**: JSON 序列化（Milvus 客户端依赖）
- **Micrometer / Prometheus**: 指标收集
- **Logback**: 日志框架

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         Web 前端界面                              │
│                    (文档上传、管理、问答)                          │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP API
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      RagController                                │
│                    (REST API 控制器)                              │
└────────────────────────────┬────────────────────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│DocumentService│    │ RagService   │    │VectorStore   │
│              │    │              │    │Service       │
│- 文本提取     │    │- 检索协调    │    │- Milvus操作  │
│- 文档分块     │    │- 答案生成    │    │- 向量存储    │
└──────┬───────┘    └──────┬───────┘    └──────┬───────┘
       │                   │                   │
       ▼                   ▼                   │
┌──────────────┐    ┌──────────────┐           │
│Embedding     │    │Retrieval     │           │
│Service       │    │Service       │           │
│              │    │              │           │
│- 向量生成    │    │- 混合检索    │           │
│- Redis缓存   │    │- 向量/BM25   │           │
└──────┬───────┘    └──────┬───────┘           │
       │                   │                   │
       ▼                   ▼                   │
┌──────────────┐    ┌──────────────┐           │
│火山引擎 API   │    │BM25Service   │           │
│              │    │RerankService │           │
│- 嵌入向量    │    │- 关键词重排  │◄──────────┘
│- 聊天生成    │    │- 语义重排    │
└──────────────┘    └──────────────┘
```

## 项目结构

```
RAG/
├── src/main/java/com/example/rag/
│   ├── RagApplication.java              # 主应用类（含 Swagger/OpenAPI 配置）
│   ├── config/
│   │   ├── EmbeddingConfig.java        # 火山引擎 Ark 服务配置
│   │   ├── MilvusConfig.java           # Milvus 向量数据库配置
│   │   ├── RedisConfig.java            # Redis 缓存配置
│   │   ├── MetricsConfig.java          # Prometheus 指标配置
│   │   └── MultiModalEmbeddingsExample.java  # 多模态嵌入示例
│   ├── controller/
│   │   └── RagController.java           # REST API 控制器
│   ├── model/
│   │   └── DocumentChunk.java           # 文档块模型
│   ├── service/
│   │   ├── DocumentService.java         # 文档处理服务（动态分块）
│   │   ├── EmbeddingService.java       # 嵌入向量服务（Redis缓存）
│   │   ├── VectorStoreService.java      # 向量存储服务（Milvus集成）
│   │   ├── RetrievalService.java        # 检索服务（纯向量/混合检索）
│   │   ├── BM25Service.java            # BM25 关键词检索服务（Lucene）
│   │   ├── RerankService.java           # 重排序服务
│   │   ├── RagService.java              # RAG 问答服务
│   │   └── MetricsService.java          # 指标记录服务
│   └── util/
│       └── RetryUtil.java               # 指数退避重试工具
├── src/main/resources/
│   ├── application.yml                 # 主配置文件
│   ├── application.properties          # 应用属性
│   ├── logback-spring.xml              # 日志配置（prod 环境 JSON 格式）
│   └── static/
│       └── index.html                   # Web前端界面
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
- **milvus-sdk-java**: Milvus 向量数据库客户端
- **spring-boot-starter-data-redis**: Spring Data Redis 集成
- **lucene-core / queryparser / analyzers-common**: Lucene BM25 检索
- **springdoc-openapi-starter-webmvc-ui**: Swagger API 文档
- **spring-boot-starter-actuator**: 监控端点
- **micrometer-registry-prometheus**: Prometheus 指标收集
- **logstash-logback-encoder**: JSON 结构化日志
- **gson**: JSON 序列化（Milvus 客户端依赖）

### application.yml

主配置文件，包含以下配置：

```yaml
spring:
  application:
    name: RAG
  # 配置火山引擎（豆包）的API
  ai:
    openai:
      base-url: https://ark.cn-beijing.volces.com/api/v3  # 火山引擎网关地址
      api-key: ${ARK_API_KEY}  # API Key
      embedding:
        enabled: false  # 禁用自动配置，使用自定义实现
      chat:
        options:
          model: ${CHAT_MODEL:ep-20260419235315-sv4kp}  # 聊天模型端点 ID
  # 文件上传配置
  servlet:
    multipart:
      max-file-size: 100MB  # 最大文件上传大小
      max-request-size: 100MB
  # Redis 配置
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 3000ms
      jedis:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms

# Milvus 向量数据库配置
milvus:
  host: ${MILVUS_HOST:localhost}
  port: ${MILVUS_PORT:19530}
  collection-name: rag_documents
  dimension: 2048  # 嵌入向量维度

# 自定义配置
volcengine:
  api-key: ${ARK_API_KEY}
  embedding:
    model: ep-20260420014217-l6bqr  # 嵌入模型端点 ID
  chat:
    model: ep-20260419235315-sv4kp  # 聊天模型端点 ID

# 嵌入向量缓存配置
embedding:
  cache:
    enabled: true  # 是否启用缓存
    ttl: 86400  # 缓存过期时间（秒），默认 24 小时

# 文档分块配置
document:
  chunk:
    min-size: 200  # 最小块大小
    max-size: 800  # 最大块大小
    target-size: 500  # 目标块大小
    overlap: 100  # 块重叠大小（保持上下文连续性）

# 向量存储字段配置
vector-store:
  fields:
    id: id  # 文档块ID字段名
    document-id: document_id  # 文档ID字段名
    vector: vector  # 向量字段名
    content: content  # 内容字段名
    source: source  # 来源字段名
    chunk-index: chunk_index  # 块索引字段名
  index:
    nlist: 128  # IVF_FLAT索引参数

# 检索配置
retrieval:
  top-k: 3  # 检索返回的最相关文档块数量
  rerank:
    enabled: true  # 是否启用重排序
    top-k: 5  # 重排序前检索的候选块数量
    final-top-k: 3  # 重排序后返回的最终块数量
    method: keyword  # 重排序方法：keyword（关键词匹配）、semantic（语义相似度）
  bm25:
    enabled: false  # 是否启用BM25关键词检索（混合检索）
    weight: 0.3  # BM25权重（0-1之间，向量检索权重为 1-weight）
    top-k: 5  # BM25检索的候选块数量

# API重试配置
retry:
  enabled: true  # 是否启用重试
  max-attempts: 3  # 最大重试次数
  initial-delay: 1000  # 初始延迟（毫秒）
  max-delay: 10000  # 最大延迟（毫秒）
  multiplier: 2.0  # 延迟倍数（指数退避）

# Prometheus指标配置
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,metrics
  metrics:
    tags:
      application: rag-system

# Swagger/OpenAPI 配置
springdoc:
  api-docs:
    path: /v3/api-docs  # OpenAPI 规范地址（Postman 可通过 Import -> Link 导入）
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method  # 接口按请求方法排序
    tags-sorter: alpha

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
  - `ragOpenAPI()`: 配置 Swagger/OpenAPI 文档入口
- `StartupListener`: 启动监听器
  - `onApplicationEvent(ApplicationReadyEvent)`: 应用启动完成后打印访问地址和 API 信息

**Swagger 入口**:
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI 规范 JSON: `http://localhost:8081/v3/api-docs`（Postman 可通过 Import -> Link 直接导入）

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

### 3. MilvusConfig.java

**作用**: Milvus 向量数据库配置类，配置 MilvusClientV2 用于向量存储和检索。

**主要类和方法**:
- `MilvusConfig`: 配置类
  - `milvusClient()`: 配置 MilvusClientV2 Bean，自动补充 `http://` 协议头，可选注入 token

**配置参数**:
- `milvus.host`: Milvus 服务器地址
- `milvus.port`: Milvus 服务器端口
- `milvus.token`: Milvus 访问 token（可选）

---

### 4. RedisConfig.java

**作用**: Redis 配置类，配置 RedisTemplate 用于缓存嵌入向量。

**主要类和方法**:
- `RedisConfig`: 配置类
  - `redisTemplate(RedisConnectionFactory)`: 配置 RedisTemplate Bean
    - 使用 String 序列化 key
    - 使用 JSON 序列化 value（GenericJackson2JsonRedisSerializer）
    - 自动检测 Redis 依赖并连接

---

### 5. MetricsConfig.java

**作用**: Prometheus 指标配置类，注册 Micrometer MeterRegistry。

**主要类和方法**:
- `MetricsConfig`: 配置类
  - `meterRegistry()`: 配置 PrometheusMeterRegistry Bean

---

### 6. MultiModalEmbeddingsExample.java

**作用**: 多模态嵌入向量生成的示例代码（独立运行）。

**主要类和方法**:
- `MultiModalEmbeddingsExample`: 示例类
  - `main(String[] args)`: 演示如何调用多模态嵌入 API 生成文本向量

**功能**: 展示如何使用火山引擎 SDK 生成文本的嵌入向量，可作为独立测试程序运行。

---

### 7. RagController.java

**作用**: REST API 控制器，提供文档上传、问答、向量库管理、文档管理等接口。

**API 端点**:
- `POST /api/rag/upload`: 上传文档（MultipartFile）
- `GET /api/rag/chat?message=xxx&documentId=xxx`: 问答（支持文档隔离）
- `POST /api/rag/clear`: 清空向量库
- `GET /api/rag/status`: 获取状态（总块数、文档列表）
- `GET /api/rag/document/{documentId}/status`: 获取指定文档的状态
- `DELETE /api/rag/document/{documentId}`: 删除指定文档
- `GET /api/rag/documents`: 获取所有文档列表（含文件名映射）

---

### 8. DocumentChunk.java

**作用**: 文档块模型类，表示文档的一个分块。

**字段**:
- `id`: 文档块唯一 ID（UUID）
- `documentId`: 文档唯一 ID（用于文档隔离）
- `content`: 文档块内容
- `embedding`: 嵌入向量（List<Float>）
- `source`: 文档来源（文件名）
- `chunkIndex`: 文档块索引
- `totalChunks`: 总文档块数
- `similarity`: 向量相似度分数
- `score`: BM25 分数
- `hybridScore`: 混合检索分数

---

### 9. DocumentService.java

**作用**: 文档处理服务，负责文本提取、动态分块和文档块创建。

**主要类和方法**:
- `DocumentService`: 文档服务类
  - `extractText(MultipartFile file)`: 使用 Apache Tika 从上传的文件中提取文本
  - `splitText(String text)`: 智能文本分块（动态分块策略）
  - `createDocumentChunks(...)`: 创建文档块对象列表（自动生成 documentId）

**分块策略**:
- 按 `\n\n` 段落边界分割，保持段落完整性
- 段落在 200-800 字符之间直接作为块
- 过长段落按句子边界分割（`。！？` 等）
- 过短段落与相邻段落合并至目标大小（500 字符）
- 为块添加 100 字符重叠，保持上下文连续性

**常量/配置**（可在 application.yml 中调整）:
- `document.chunk.min-size = 200`: 最小块大小
- `document.chunk.max-size = 800`: 最大块大小
- `document.chunk.target-size = 500`: 目标块大小
- `document.chunk.overlap = 100`: 块重叠大小

---

### 10. EmbeddingService.java

**作用**: 嵌入向量服务，调用火山引擎 API 生成文本的嵌入向量，支持 Redis 缓存。

**主要类和方法**:
- `EmbeddingService`: 嵌入服务类
  - `generateEmbedding(String text)`: 生成单个文本的嵌入向量（带缓存）
  - `generateEmbeddings(List<String> texts)`: 批量生成文本的嵌入向量（每个带缓存）
  - `extractEmbedding(MultimodalEmbeddingResult)`: 从 API 响应中提取嵌入向量

**缓存机制**:
- 缓存键: `embedding:` + MD5(model:text)
- 缓存配置: `embedding.cache.enabled`、`embedding.cache.ttl`（默认 86400 秒）
- Redis 不可用时自动降级到直接 API 调用（`@Autowired(required = false)`）

---

### 11. VectorStoreService.java

**作用**: 向量存储服务，使用 Milvus 向量数据库存储文档块及其嵌入向量，提供高性能相似度搜索功能（单集合架构，支持文档隔离）。

**主要类和方法**:
- `VectorStoreService`: 向量存储服务类
  - `initCollection()`: 初始化 Milvus 集合（不存在则自动创建）
  - `addChunks(List<DocumentChunk>)`: 添加文档块到向量存储（插入后自动 flush）
  - `similaritySearch(List<Float>, int topK, String documentId)`: 余弦相似度搜索（支持 document_id 过滤）
  - `deleteDocument(String documentId)`: 使用 filter 表达式删除指定文档所有块
  - `size()` / `size(String documentId)`: 获取总块数 / 指定文档块数
  - `getAllDocumentIds()`: 获取所有文档ID列表
  - `getDocumentIdToFilenameMap()`: 获取文档ID到文件名的映射
  - `getAllChunks()`: 获取所有文档块（用于 BM25 索引构建）
  - `clearAll()`: 清空整个集合

**Milvus 集合设计（单集合架构）**:
- **字段定义**:
  - `id`: VarChar(256)，主键，文档块唯一标识
  - `document_id`: VarChar(128)，文档ID（用于文档隔离）
  - `vector`: FloatVector(2048)，嵌入向量
  - `content`: VarChar(65535)，文档块内容
  - `source`: VarChar(512)，文档来源（文件名）
  - `chunk_index`: Int64，文档块索引
- **索引配置**:
  - 索引类型: IVF_FLAT
  - 距离度量: COSINE（余弦相似度）
  - 参数: nlist=128

**单集合架构优势**:
- **简化管理**: 只需维护一个集合，降低运维复杂度
- **高效隔离**: 通过 document_id 字段实现逻辑隔离
- **灵活查询**: 支持跨文档检索和单文档检索
- **易于扩展**: 适合中小规模应用，支持多文档管理

---

### 12. RetrievalService.java

**作用**: 检索服务，负责根据用户查询检索相关文档块，支持纯向量检索和混合检索（向量 + BM25）。

**主要类和方法**:
- `RetrievalService`: 检索服务类
  - `retrieve(String query, int topK, String documentId)`: 检索相关文档块（自动分派）
  - `vectorRetrieve(...)`: 纯向量检索（可选重排序）
  - `hybridRetrieve(...)`: 混合检索（向量 + BM25 加权融合）
  - `formatContext(List<DocumentChunk>)`: 格式化检索结果为上下文，格式 `[来源: xxx]\n内容`

**检索流程**:
1. 若启用 BM25（`retrieval.bm25.enabled=true`）→ 混合检索
2. 否则 → 纯向量检索
3. 若启用重排序且候选数 > topK → 对候选块重排序后取前 topK

**混合检索策略**:
- 向量相似度归一化 × (1 - bm25Weight) + BM25 分数归一化 × bm25Weight
- 按混合分数降序返回 topK

---

### 13. BM25Service.java

**作用**: BM25 关键词检索服务，基于 Lucene 全文索引实现 BM25 算法。

**主要类和方法**:
- `BM25Service`: BM25 检索服务类
  - `buildIndex(List<DocumentChunk>)`: 构建/重建 BM25 索引（内存 RAMDirectory）
  - `search(String query, int topK, String documentId)`: BM25 检索（支持文档过滤）
  - `addDocument(DocumentChunk)`: 添加单个文档块到索引
  - `clearIndex()`: 清空索引

**特性**:
- 使用 StandardAnalyzer（支持中文分词）
- 索引存在内存目录（RAMDirectory），首次调用时自动从 Milvus 加载全量构建
- 文档过滤通过结果后置过滤实现

---

### 14. RerankService.java

**作用**: 重排序服务，对检索到的文档块进行重新打分，提升答案相关性。

**主要类和方法**:
- `RerankService`: 重排序服务类
  - `rerank(String query, List<DocumentChunk> candidates, int finalTopK)`: 对候选块重排序后取前 finalTopK
  - `keywordRerank(...)`: 基于关键词匹配频率和位置的重排序
  - `semanticRerank(...)`: 结合原始向量相似度和关键词分数的语义重排序

**配置**:
- `retrieval.rerank.method`: `keyword`（默认）/ `semantic`

---

### 15. RagService.java

**作用**: RAG 问答服务，整合检索和生成，基于检索到的文档内容回答用户问题。

**主要类和方法**:
- `RagService`: RAG 服务类
  - `chat(String query)`: 基于检索增强生成回答用户问题
  - `chat(String query, String documentId)`: 支持文档隔离的问答

**提示词模板**（系统提示单独设置，含分段与溯源规则）:
```
你是基于内部文档的问答助手。你的回答必须完全基于给定文档内容，并严格遵循以下格式规则：

1. 每表达完一个完整意思后，必须空一行（即按两次回车键）
2. 严禁出现超过3行连续的文本，必须分段
3. 优先使用列表形式，用数字或符号标记
4. 在回答中穿插引用来源，格式为【来源：文档X第Y段】或【参考：<原文摘录>】。
5. 如果文档中没有相关信息，回复："抱歉，当前文档中未包含相关信息。"
6. 保持回答简洁，避免冗长
```

---

### 16. MetricsService.java

**作用**: 指标服务，统一管理系统各项指标（缓存命中率、查询耗时等）。

**主要类和方法**:
- `MetricsService`: 指标服务类
  - `recordEmbeddingCacheHit()` / `recordEmbeddingCacheMiss()`: 缓存命中/未命中
  - `getCacheHitRate()`: 获取缓存命中率
  - `recordEmbeddingApiCall(long, boolean)`: 记录嵌入 API 调用
  - `recordMilvusSearch(long, boolean)`: 记录 Milvus 搜索
  - `recordRagRequest(long, boolean)`: 记录 RAG 请求
  - `recordRerank(long)`: 记录重排序

---

### 17. RetryUtil.java

**作用**: 重试工具类，实现指数退避重试机制。

**主要类和方法**:
- `RetryUtil`: 工具类
  - `executeWithRetry(Supplier<T>, String)`: 执行带重试的操作
  - `executeWithRetryAndFallback(Supplier<T>, Supplier<T>, String)`: 带降级处理的重试
  - `getFriendlyErrorMessage(Exception)`: 获取友好的错误消息

**配置**（application.yml 中 `retry.*`）:
- `retry.max-attempts = 3`: 最大重试次数
- `retry.initial-delay = 1000`: 初始延迟（毫秒）
- `retry.max-delay = 10000`: 最大延迟（毫秒）
- `retry.multiplier = 2.0`: 延迟倍数（指数退避，1s → 2s → 4s）

**重试策略**:
- 网络类异常（timeout/connection/network/rate limit/5xx）自动重试
- 认证类异常（invalid/unauthorized/forbidden/not found）不重试

## 指标监控

系统集成了 Micrometer + Prometheus，可通过 `/actuator/prometheus` 端点获取指标数据。

### 关键指标

- **嵌入向量缓存指标**:
  - `embedding.cache.hit`: 缓存命中次数
  - `embedding.cache.miss`: 缓存未命中次数
  - `embedding.api.call`: API调用次数
  - `embedding.api.error`: API调用失败次数
  - `embedding.api.duration`: API调用耗时

- **Milvus搜索指标**:
  - `milvus.search.call`: 搜索调用次数
  - `milvus.search.error`: 搜索失败次数
  - `milvus.search.duration`: 搜索耗时

- **RAG请求指标**:
  - `rag.request.call`: RAG请求次数
  - `rag.request.error`: RAG请求失败次数
  - `rag.request.duration`: RAG请求总耗时

- **重排序指标**:
  - `rerank.call`: 重排序调用次数
  - `rerank.duration`: 重排序耗时

### 日志配置

系统支持结构化日志输出（`logback-spring.xml`）:

- **开发环境**: 普通文本格式，便于调试
- **生产环境（profiles=prod）**: JSON 格式，便于日志收集和分析

通过设置 `spring.profiles.active=prod` 启用 JSON 格式日志。

## 数据传输流程

### 1. 文档上传流程

```
用户上传 PDF
    ↓
RagController.uploadDocument()
    ↓
DocumentService.extractText() → 提取文本
    ↓
DocumentService.splitText() → 动态分块（段落/句子边界 + 重叠）
    ↓
EmbeddingService.generateEmbeddings() → 生成向量（Redis 缓存）
    ↓
DocumentService.createDocumentChunks() → 创建文档块对象（生成 documentId）
    ↓
VectorStoreService.addChunks() → 存储到 Milvus 向量数据库（记录 document_id）
    ↓
返回上传结果（包含 documentId）
```

### 2. 问答流程

```
用户提问（可选择文档范围）
    ↓
RagController.chat()
    ↓
RagService.chat()
    ↓
RetrievalService.retrieve()
    ↓
EmbeddingService.generateEmbedding() → 生成问题向量（Redis 缓存）
    ↓
VectorStoreService.similaritySearch() → Milvus 向量相似度搜索（支持 document_id 过滤）
    ↓
(可选) BM25Service.search() → BM25 关键词检索
    ↓
(可选) RerankService.rerank() → 重排序
    ↓
RetrievalService.formatContext() → 格式化上下文
    ↓
构建提示词（含分段/溯源规则）
    ↓
调用火山引擎聊天 API（带重试）
    ↓
返回答案
```

### 3. 数据流向

**上传阶段**:
- PDF 文件 → 文本 → 智能文本块 → 嵌入向量（缓存） → DocumentChunk 对象（含 documentId） → Milvus 持久化存储（单集合）

**问答阶段**:
- 用户问题（可选文档范围）→ 问题向量（缓存） → Milvus 高性能相似度搜索（支持 document_id 过滤）→ (可选 BM25 + 重排序) → 相关文档块 → 上下文 → 提示词 → 答案

## 运行指南

### 前置要求

- Java 21
- Maven 3.x
- Node.js 18+ 和 npm（用于启动 Vue 开发服务器）
- 火山引擎 API Key
- 火山引擎推理端点 ID（嵌入模型和聊天模型）
- Redis 6.0+（可选，用于缓存）
- Milvus 2.4+（向量数据库）

### 配置步骤

1. 获取火山引擎 API Key 和推理端点 ID
2. 安装和启动 Redis（可选，用于缓存优化）：
   ```bash
   # Docker 方式
   docker run -d -p 6379:6379 redis:6-alpine

   # 或使用本地安装的 Redis
   redis-server
   ```
3. 安装和启动 Milvus 向量数据库：
   ```bash
   # Docker Compose 方式（推荐）
   docker-compose up -d

   # 或参考 Milvus 官方文档安装
   ```
4. 修改 `application.yml` 中的配置（或设置环境变量）：
   - `ARK_API_KEY`: 设置你的火山引擎 API Key
   - `volcengine.embedding.model`: 设置嵌入模型端点 ID（默认 `ep-20260420014217-l6bqr`）
   - `volcengine.chat.model`: 设置聊天模型端点 ID（默认 `ep-20260419235315-sv4kp`）
   - `spring.data.redis.*`: Redis 连接配置（如果启用缓存）
   - `milvus.*`: Milvus 连接配置
   - `embedding.cache.*`: 缓存配置（可开关）
   - `retrieval.*`: 检索配置（top-k、重排序、BM25 开关）

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
🟢 Vue开发地址: http://localhost:5173/
📚 Swagger API文档: http://localhost:8081/swagger-ui.html
📄 OpenAPI规范(JSON): http://localhost:8081/v3/api-docs
📄 上传API: POST http://localhost:8081/api/rag/upload
💬 问答API: GET http://localhost:8081/api/rag/chat?message=xxx
📊 Prometheus指标: http://localhost:8081/actuator/prometheus
```

`RagApplication` 在 Spring Boot 启动完成后会自动执行 `frontend/npm run dev`，Vue 开发页面通过 Vite 代理访问后端 API。点击控制台中的 `http://localhost:5173/` 即可打开 Vue 页面；`http://localhost:8081/` 是 `npm run build` 生成的静态页面入口。

如果部署环境不需要启动 Node.js 开发服务器，将 `application.yml` 中的 `frontend.dev.enabled` 设置为 `false` 即可。

### API 使用示例

**上传文档**:
```bash
curl -X POST http://localhost:8081/api/rag/upload \
  -F "file=@your_document.pdf"
```

**问答（所有文档）**:
```bash
curl "http://localhost:8081/api/rag/chat?message=你的问题"
```

**问答（指定文档）**:
```bash
curl "http://localhost:8081/api/rag/chat?message=你的问题&documentId=xxx"
```

**清空向量库**:
```bash
curl -X POST http://localhost:8081/api/rag/clear
```

**获取状态**:
```bash
curl http://localhost:8081/api/rag/status
```

**获取所有文档列表**:
```bash
curl http://localhost:8081/api/rag/documents
```

**获取文档状态**:
```bash
curl http://localhost:8081/api/rag/document/{documentId}/status
```

**删除文档**:
```bash
curl -X DELETE http://localhost:8081/api/rag/document/{documentId}
```

**Swagger 导入 Postman**:
在 Postman 中 `Import -> Link` 填入 `http://localhost:8081/v3/api-docs` 即可导入全部接口。

## 注意事项

1. **API Key 安全**: 不要将 API Key 提交到版本控制系统，建议使用环境变量（`ARK_API_KEY`）
2. **依赖服务**: 确保 Redis 和 Milvus 服务正常运行，应用启动时会自动连接
3. **文件大小限制**: 默认最大上传 100MB，可在 `application.yml` 中调整
4. **向量维度**: 确保嵌入模型和 Milvus 配置的向量维度一致（默认 2048）
5. **缓存配置**: Redis 缓存是可选的，如果未启用 Redis，系统仍可正常运行但性能较低
6. **数据持久化**: 使用 Milvus 后数据持久存储，重启应用不会丢失向量数据
7. **文档隔离**: 系统使用单集合架构，通过 document_id 字段实现逻辑隔离，支持多文档管理
8. **BM25 索引内存**: BM25 索引构建在内存中（RAMDirectory），首次使用时会从 Milvus 加载全量数据，文档量大时建议在应用启动时预热

## 新特性说明

### 1. 文档隔离功能
- **单集合架构**: 使用单个 Milvus 集合，通过 document_id 字段实现逻辑隔离
- **多文档管理**: 支持上传多个文档，每个文档独立管理
- **文档级检索**: 可选择在特定文档或所有文档范围内进行问答
- **文档管理**: 支持查看文档列表、删除指定文档、查看文档统计

### 2. Web 前端界面
- **完整界面**: 提供现代化的 Web 前端界面
- **文档上传**: 支持拖拽上传和文件选择
- **文档管理**: 显示文档列表、文件名、块数量统计、删除/清空操作
- **智能问答**: 支持选择文档范围进行问答
- **实时反馈**: 显示操作状态和错误信息

### 3. 混合检索（向量 + BM25）
- **语义检索**: 向量相似度检索，理解语义相似
- **关键词检索**: Lucene BM25 精确匹配关键词
- **加权融合**: 通过 `retrieval.bm25.weight` 配置两种检索的权重
- **可配置**: 通过 `retrieval.bm25.enabled` 开关切换纯向量/混合检索

### 4. 重排序机制
- **关键词重排**: 根据关键词出现频率和位置重新打分
- **语义重排**: 结合原始向量相似度 + 关键词分数加权
- **提升相关性**: 将更相关的内容排到前面，提高答案质量

### 5. 动态分块策略
- **智能分割**: 优先在段落和句子边界分割，保持语义完整性
- **自适应大小**: 根据内容动态调整块大小（200-800 字符）
- **上下文重叠**: 通过重叠保持块之间的语义关联
- **质量提升**: 更好的分块策略显著提升向量检索准确性

### 6. Redis 缓存优化
- **性能提升**: 缓存嵌入向量，避免重复调用火山引擎 API
- **智能缓存**: 使用 MD5 哈希生成唯一缓存键
- **可配置**: 支持开关缓存和设置过期时间
- **降级方案**: Redis 不可用时自动降级到直接 API 调用

### 7. 指数退避重试
- **自动重试**: API 调用失败自动重试，最多 3 次
- **智能判断**: 根据异常类型判断是否值得重试（网络类重试，认证类不重试）
- **服务降级**: 支持降级处理，提高系统可靠性

### 8. Swagger API 文档
- **在线文档**: Swagger UI 交互式 API 文档
- **Postman 导入**: 支持通过 `/v3/api-docs` 一键导入 Postman

### 9. Milvus 向量数据库
- **高性能**: 专业的向量索引和搜索算法，支持大规模数据
- **持久化**: 数据持久存储，重启应用不丢失
- **可扩展**: 支持海量向量数据存储和检索
- **企业级**: 支持高并发、高可用部署

## 扩展建议

1. **用户认证**: 添加用户认证和权限管理
2. **批量上传**: 支持批量上传多个文档
3. **日志监控**: 集成日志系统和监控工具
4. **性能优化**: 添加异步处理、连接池等优化
5. **多模态支持**: 支持图片、音频等多模态文档处理
6. **分布式部署**: 支持多节点部署和负载均衡
7. **向量数据库集群**: Milvus 集群部署，支持更大规模数据
8. **文档导出**: 支持导出问答结果和文档统计
9. **BM25 索引持久化**: 将 Lucene 索引持久化到磁盘，避免每次重启重建
10. **元数据数据库**: 维护文档元数据到关系型数据库（如 MySQL），避免全表扫描获取文档列表

## 许可证

本项目仅供学习和研究使用。
