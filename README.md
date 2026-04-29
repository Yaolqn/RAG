# RAG PDF 问答系统

基于 Spring Boot 和火山引擎（豆包）的检索增强生成（RAG）系统，支持上传 PDF 文档并进行智能问答。

## 项目概述

本项目实现了一个完整的 RAG（Retrieval-Augmented Generation）系统，通过以下流程实现文档问答：

1. **文档上传**：用户上传 PDF 文档，系统为每个文档生成唯一ID
2. **文本提取**：使用 Apache Tika 提取文档文本
3. **文档分块**：将文本分割成多个小块
4. **向量生成**：调用火山引擎 API 生成文本嵌入向量
5. **向量存储**：将文档块和向量存储在 Milvus 向量数据库（单集合架构，通过 document_id 字段实现文档隔离）
6. **相似度检索**：根据用户问题检索相关文档块（支持文档级隔离）
7. **答案生成**：基于检索到的上下文生成答案

**核心特性**：
- **文档隔离**：支持多文档管理，每个文档独立存储和检索
- **Web前端界面**：提供完整的Web界面，支持文档上传、管理、问答
- **单集合架构**：使用单个 Milvus 集合，通过 document_id 字段实现逻辑隔离
- **Redis缓存**：嵌入向量缓存，减少API调用，提升性能

## 技术栈

- **Java**: 21
- **Spring Boot**: 3.2.5
- **火山引擎 SDK**: volcengine-java-sdk-ark-runtime 2.0.0
- **Spring AI**: spring-ai-openai-spring-boot-starter 1.0.0-M4
- **Apache Tika**: 2.9.1（文档解析）
- **Apache Commons Math**: 3.6.1（向量相似度计算）
- **Milvus**: 2.4+（向量数据库）
- **Redis**: 6.0+（缓存系统）
- **Spring Data Redis**: Redis 集成
- **Gson**: JSON 序列化（Milvus 客户端依赖）

## 项目结构

```
RAG/
├── src/main/java/com/example/rag/
│   ├── RagApplication.java              # 主应用类
│   ├── config/
│   │   ├── EmbeddingConfig.java        # 火山引擎配置
│   │   ├── MilvusConfig.java           # Milvus 向量数据库配置
│   │   ├── RedisConfig.java            # Redis 缓存配置
│   │   └── MultiModalEmbeddingsExample.java  # 多模态嵌入示例
│   ├── controller/
│   │   └── RagController.java           # REST API 控制器
│   ├── model/
│   │   └── DocumentChunk.java           # 文档块模型
│   └── service/
│       ├── DocumentService.java         # 文档处理服务（动态分块）
│       ├── EmbeddingService.java       # 嵌入向量服务（Redis缓存）
│       ├── RagService.java              # RAG 问答服务
│       ├── RetrievalService.java        # 检索服务
│       └── VectorStoreService.java      # 向量存储服务（Milvus集成）
├── src/main/resources/
│   ├── application.yml                 # 主配置文件
│   ├── application.properties          # 应用属性
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
      database: 2
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

### 3. MilvusConfig.java

**作用**: Milvus 向量数据库配置类，配置 MilvusClient 用于向量存储和检索。

**主要类和方法**:
- `MilvusConfig`: 配置类
  - `milvusClient()`: 配置 MilvusClientV2 Bean，设置连接参数
  - `milvusCollectionName()`: 配置集合名称 Bean
  - `milvusDimension()`: 配置向量维度 Bean

**配置参数**:
- `milvus.host`: Milvus 服务器地址
- `milvus.port`: Milvus 服务器端口
- `milvus.collection-name`: 集合名称
- `milvus.dimension`: 向量维度

---

### 4. RedisConfig.java

**作用**: Redis 配置类，配置 RedisTemplate 用于缓存嵌入向量。

**主要类和方法**:
- `RedisConfig`: 配置类
  - `redisTemplate(RedisConnectionFactory)`: 配置 RedisTemplate Bean
    - 使用 String 序列化 key
    - 使用 JSON 序列化 value
    - 自动检测 Redis 依赖并连接

**特性**:
- 自动创建 RedisConnectionFactory Bean
- 支持缓存嵌入向量以提高性能
- 可配置缓存过期时间

---

### 5. MultiModalEmbeddingsExample.java

**作用**: 多模态嵌入向量生成的示例代码（独立运行）。

**主要类和方法**:
- `MultiModalEmbeddingsExample`: 示例类
  - `main(String[] args)`: 演示如何调用多模态嵌入 API 生成文本向量

**功能**: 展示如何使用火山引擎 SDK 生成文本的嵌入向量，可作为独立测试程序运行。

---

### 6. RagController.java

**作用**: REST API 控制器，提供文档上传、问答、向量库管理、文档管理等接口。

**主要类和方法**:
- `RagController`: 控制器类
  - `uploadDocument(MultipartFile file)`: 上传文档并生成嵌入向量
    - 输入: MultipartFile 文件
    - 输出: 包含成功状态、消息、块数量、documentId 的 Map
    - 流程: 提取文本 → 动态分块 → 生成向量（带缓存）→ 存储到 Milvus（记录 document_id）
  - `chat(String message, String documentId)`: 基于检索增强生成回答用户问题
    - 输入: 用户问题 message、文档ID（可选）
    - 输出: 包含成功状态和答案的 Map
    - 流程: 生成问题向量（带缓存）→ Milvus 相似度搜索（支持文档隔离）→ 格式化上下文 → 生成答案
  - `clearVectorStore()`: 清空向量存储
    - 输出: 包含成功状态的 Map
    - 操作: 删除 Milvus 集合
  - `getStatus()`: 获取向量库状态
    - 输出: 包含总块数量、文档列表的 Map
    - 操作: 查询 Milvus 集合统计信息
  - `getDocumentStatus(String documentId)`: 获取指定文档的状态
    - 输入: 文档ID
    - 输出: 包含文档块数量的 Map
    - 操作: 查询特定 document_id 的数据统计
  - `deleteDocument(String documentId)`: 删除指定文档
    - 输入: 文档ID
    - 输出: 包含成功状态的 Map
    - 操作: 使用 filter 删除特定 document_id 的数据
  - `getAllDocuments()`: 获取所有文档列表
    - 输出: 包含文档ID列表、文件名映射的 Map
    - 操作: 查询所有唯一的 document_id 和对应的文件名

**API 端点**:
- `POST /api/rag/upload`: 上传文档
- `GET /api/rag/chat?message=xxx&documentId=xxx`: 问答（支持文档隔离）
- `POST /api/rag/clear`: 清空向量库
- `GET /api/rag/status`: 获取状态
- `GET /api/rag/document/{documentId}/status`: 获取文档状态
- `DELETE /api/rag/document/{documentId}`: 删除文档
- `GET /api/rag/documents`: 获取所有文档列表

---

### 7. DocumentChunk.java

**作用**: 文档块模型类，表示文档的一个分块。

**字段**:
- `id`: 文档块唯一 ID（UUID）
- `documentId`: 文档唯一 ID（用于文档隔离）
- `content`: 文档块内容
- `embedding`: 嵌入向量（List<Float>）
- `source`: 文档来源（文件名）
- `chunkIndex`: 文档块索引
- `totalChunks`: 总文档块数
- `similarity`: 相似度分数

**方法**:
- 标准的 getter/setter 方法

---

### 8. DocumentService.java

**作用**: 文档处理服务，负责文本提取、动态分块和文档块创建。

**主要类和方法**:
- `DocumentService`: 文档服务类
  - `extractText(MultipartFile file)`: 从上传的文件中提取文本
    - 输入: MultipartFile 文件
    - 输出: 提取的文本内容
    - 实现: 使用 Apache Tika 解析文档，支持多种格式
  - `splitText(String text)`: 智能文本分块（动态分块策略）
    - 输入: 输入文本
    - 输出: 文本块列表
    - 策略: 
      - 优先在段落边界分割，保持语义完整性
      - 动态调整块大小（200-800 字符）
      - 过长段落按句子分割
      - 过短段落智能合并
      - 添加重叠保持上下文连续性
  - `createDocumentChunks(String text, String source, List<List<Float>> embeddings)`: 创建文档块对象列表
    - 输入: 文本内容、来源、嵌入向量列表
    - 输出: DocumentChunk 对象列表
    - 实现: 为每个文本块生成唯一 ID 和 documentId 并创建 DocumentChunk 对象

**核心方法详解**:
- `splitLongParagraph(String paragraph)`: 分割过长的段落
  - 按句子边界分割，避免破坏语义完整性
  - 动态控制块大小，确保在 MIN_CHUNK_SIZE 和 MAX_CHUNK_SIZE 之间
- `mergeSmallChunks(List<String> chunks)`: 合并过短的块
  - 智能合并相邻的短块，达到目标块大小
  - 保持段落之间的分隔符
- `addOverlap(List<String> chunks)`: 添加重叠内容
  - 为每个块添加前一个块的尾部作为重叠
  - 在句子边界处分割重叠，保持语义连贯性

**常量**:
- `MIN_CHUNK_SIZE = 200`: 最小块大小
- `MAX_CHUNK_SIZE = 800`: 最大块大小
- `TARGET_CHUNK_SIZE = 500`: 目标块大小
- `CHUNK_OVERLAP = 100`: 块重叠大小

**动态分块优势**:
- 保持语义完整性：优先在段落和句子边界分割
- 自适应大小：根据内容动态调整块大小
- 上下文连续性：通过重叠保持块之间的关联
- 提高检索质量：更好的分块策略提升向量检索准确性

---

### 9. EmbeddingService.java

**作用**: 嵌入向量服务，调用火山引擎 API 生成文本的嵌入向量，支持 Redis 缓存。

**主要类和方法**:
- `EmbeddingService`: 嵌入服务类
  - `generateEmbedding(String text)`: 生成单个文本的嵌入向量（带缓存）
    - 输入: 输入文本
    - 输出: 嵌入向量（List<Float>）
    - 流程: 
      1. 尝试从 Redis 缓存获取
      2. 缓存未命中时调用火山引擎 API
      3. 将结果存入缓存并返回
  - `generateEmbeddings(List<String> texts)`: 批量生成文本的嵌入向量
    - 输入: 文本列表
    - 输出: 二维浮点数列表
    - 实现: 遍历文本列表，逐个调用 generateEmbedding（每个都带缓存）

**缓存机制**:
- `generateCacheKey(String text)`: 生成缓存键
  - 使用 MD5 哈希算法
  - 格式: `model + ":" + text`
  - 确保相同文本和模型组合的唯一性
- 缓存配置:
  - `embedding.cache.enabled`: 是否启用缓存
  - `embedding.cache.ttl`: 缓存过期时间（默认 86400 秒）

**性能优化**:
- Redis 缓存大幅减少重复文本的 API 调用
- 批量处理时每个文本独立缓存，提高命中率
- 可配置的缓存过期时间，平衡性能和数据新鲜度

**依赖**:
- `ArkService`: 火山引擎 Ark 服务
- `RedisTemplate<String, Object>`: Redis 缓存客户端（可选）
- `volcengine.embedding.model`: 嵌入模型 ID

---

### 10. RagService.java

**作用**: RAG 问答服务，整合检索和生成，基于检索到的文档内容回答用户问题。

**主要类和方法**:
- `RagService`: RAG 服务类
  - `chat(String query, String documentId)`: 基于检索增强生成回答用户问题
    - 输入: 用户问题、文档ID（可选，用于文档隔离）
    - 输出: 生成的答案
    - 流程: 检索相关文档块（Milvus，支持文档隔离） → 格式化上下文 → 构建提示词 → 调用聊天 API

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

### 11. RetrievalService.java

**作用**: 检索服务，根据用户查询检索相关文档块。

**主要类和方法**:
- `RetrievalService`: 检索服务类
  - `retrieve(String query, int topK, String documentId)`: 根据查询检索相关文档块
    - 输入: 用户查询、返回数量、文档ID（可选，用于文档隔离）
    - 输出: 相关文档块列表
    - 流程: 生成查询向量（带缓存）→ Milvus 相似度搜索（支持文档隔离）
  - `formatContext(List<DocumentChunk> chunks)`: 格式化检索到的文档块为上下文字符串
    - 输入: 文档块列表
    - 输出: 格式化的上下文字符串
    - 格式: `[来源: xxx]\n内容`

**依赖**:
- `EmbeddingService`: 嵌入服务（带缓存）
- `VectorStoreService`: 向量存储服务（Milvus）

---

### 12. VectorStoreService.java

**作用**: 向量存储服务，使用 Milvus 向量数据库存储文档块及其嵌入向量，提供高性能相似度搜索功能（单集合架构，支持文档隔离）。

**主要类和方法**:
- `VectorStoreService`: 向量存储服务类
  - `initCollection()`: 初始化 Milvus 集合（单集合架构）
    - 自动检查集合是否存在，不存在则创建
    - 定义字段：id（主键）、document_id（文档隔离）、vector（向量）、content、source、chunk_index
    - 创建向量索引（IVF_FLAT，余弦相似度）
    - 加载集合到内存
  - `addChunks(List<DocumentChunk> documentChunks)`: 添加文档块到向量存储
    - 输入: 文档块列表
    - 实现: 使用 Gson JsonObject 批量插入到 Milvus，记录 document_id
    - 自动刷新数据确保持久化
  - `similaritySearch(List<Float> queryEmbedding, int topK, String documentId)`: 基于余弦相似度搜索相关文档块
    - 输入: 查询嵌入向量、返回数量、文档ID（可选，用于文档隔离）
    - 输出: 按相似度排序的文档块列表
    - 实现: 使用 Milvus 向量搜索 API，支持 document_id 过滤
  - `deleteDocument(String documentId)`: 删除指定文档的所有块
    - 输入: 文档ID
    - 实现: 使用 filter 表达式删除特定 document_id 的数据
  - `size()`: 获取存储的文档块总数
    - 操作: 查询 Milvus 集合统计信息
  - `size(String documentId)`: 获取指定文档的块数量
    - 输入: 文档ID
    - 实现: 使用 filter 查询特定 document_id 的数据并统计
  - `getAllDocumentIds()`: 获取所有文档ID列表
    - 输出: 文档ID列表
    - 实现: 查询集合中所有唯一的 document_id
  - `getDocumentIdToFilenameMap()`: 获取文档ID到文件名的映射
    - 输出: Map<documentId, filename>
    - 实现: 查询集合中 document_id 和 source 字段
  - `clearAll()`: 清空向量存储
    - 操作: 删除整个 Milvus 集合

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

**依赖**:
- `MilvusClientV2`: Milvus 客户端
- 配置参数: `milvus.collection-name`、`milvus.dimension`

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
DocumentService.splitText() → 动态分块（智能段落分割）
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
- PDF 文件 → 文本 → 智能文本块 → 嵌入向量（缓存） → DocumentChunk 对象（含 documentId） → Milvus 持久化存储（单集合）

**问答阶段**:
- 用户问题（可选文档范围）→ 问题向量（缓存） → Milvus 高性能相似度搜索（支持 document_id 过滤）→ 相关文档块 → 上下文 → 提示词 → 答案

### 4. 新特性数据流

**Redis 缓存流程**:
```
文本输入 → 生成 MD5 缓存键 → 检查 Redis 缓存
    ↓
缓存命中 → 直接返回嵌入向量
    ↓
缓存未命中 → 调用火山引擎 API → 存储到 Redis → 返回向量
```

**Milvus 向量存储流程**:
```
文档块列表 → Milvus 集合初始化 → 批量插入数据
    ↓
创建向量索引（IVF_FLAT） → 加载到内存 → 提供搜索服务
```

**动态分块流程**:
```
原始文本 → 段落分割 → 大小判断
    ↓
合适段落 → 直接作为块
    ↓
过长段落 → 句子分割 → 动态合并
    ↓
过短段落 → 智能合并 → 添加重叠 → 最终块列表
```

## 运行指南

### 前置要求

- Java 21
- Maven 3.x
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
4. 修改 `application.yml` 中的配置：
   - `spring.ai.openai.api-key`: 设置你的 API Key
   - `volcengine.api-key`: 设置你的 API Key
   - `volcengine.embedding.model`: 设置嵌入模型端点 ID
   - `volcengine.chat.model`: 设置聊天模型端点 ID
   - `spring.data.redis.*`: Redis 连接配置（如果启用缓存）
   - `milvus.*`: Milvus 连接配置
   - `embedding.cache.*`: 缓存配置（可开关）

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

## 注意事项

1. **API Key 安全**: 不要将 API Key 提交到版本控制系统，建议使用环境变量
2. **依赖服务**: 确保 Redis 和 Milvus 服务正常运行，应用启动时会自动连接
3. **文件大小限制**: 默认最大上传 100MB，可在 `application.yml` 中调整
4. **向量维度**: 确保嵌入模型和 Milvus 配置的向量维度一致（默认 2048）
5. **缓存配置**: Redis 缓存是可选的，如果未启用 Redis，系统仍可正常运行但性能较低
6. **数据持久化**: 使用 Milvus 后数据持久存储，重启应用不会丢失向量数据
7. **文档隔离**: 系统使用单集合架构，通过 document_id 字段实现逻辑隔离，支持多文档管理

## 新特性说明

### 1. 文档隔离功能
- **单集合架构**: 使用单个 Milvus 集合，通过 document_id 字段实现逻辑隔离
- **多文档管理**: 支持上传多个文档，每个文档独立管理
- **文档级检索**: 可选择在特定文档或所有文档范围内进行问答
- **文档管理**: 支持查看文档列表、删除指定文档、查看文档统计

### 2. Web 前端界面
- **完整界面**: 提供现代化的 Web 前端界面
- **文档上传**: 支持拖拽上传和文件选择
- **文档管理**: 显示文档列表、文件名、块数量统计
- **智能问答**: 支持选择文档范围进行问答
- **实时反馈**: 显示操作状态和错误信息

### 3. 动态分块策略
- **智能分割**: 优先在段落和句子边界分割，保持语义完整性
- **自适应大小**: 根据内容动态调整块大小（200-800 字符）
- **上下文重叠**: 通过重叠保持块之间的语义关联
- **质量提升**: 更好的分块策略显著提升向量检索准确性

### 4. Redis 缓存优化
- **性能提升**: 缓存嵌入向量，避免重复调用火山引擎 API
- **智能缓存**: 使用 MD5 哈希生成唯一缓存键
- **可配置**: 支持开关缓存和设置过期时间
- **降级方案**: Redis 不可用时自动降级到直接 API 调用

### 5. Milvus 向量数据库
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

## 许可证

本项目仅供学习和研究使用。
