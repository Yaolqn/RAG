package com.example.rag.service;

import com.example.rag.model.DocumentChunk;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 向量存储服务 (单集合架构)
 * 所有文档存储在同一个集合中，通过 document_id 字段实现逻辑隔离
 */
@Service
public class VectorStoreService {

    @Autowired
    private MilvusClientV2 milvusClient;

    @Autowired
    private MetricsService metricsService;

    @Value("${milvus.collection-name:rag_documents}")
    private String collectionName;

    @Value("${milvus.dimension:2048}")
    private int dimension;

    // 字段定义（从配置文件读取）
    @Value("${vector-store.fields.id:id}")
    private String idField;
    
    @Value("${vector-store.fields.document-id:document_id}")
    private String documentIdField;
    
    @Value("${vector-store.fields.vector:vector}")
    private String vectorField;
    
    @Value("${vector-store.fields.content:content}")
    private String contentField;
    
    @Value("${vector-store.fields.source:source}")
    private String sourceField;
    
    @Value("${vector-store.fields.chunk-index:chunk_index}")
    private String chunkIndexField;
    
    @Value("${vector-store.index.nlist:128}")
    private int indexNlist;

    /**
     * 初始化共享的 Milvus 集合
     */
    public void initCollection() {
        try {
            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            if (milvusClient.hasCollection(hasReq)) {
                return;
            }

            // 1. 定义 Schema
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();

            schema.addField(AddFieldReq.builder().fieldName(idField).dataType(DataType.VarChar).maxLength(256).isPrimaryKey(true).autoID(false).build());

            // 【核心】加入 document_id 作为过滤字段
            schema.addField(AddFieldReq.builder().fieldName(documentIdField).dataType(DataType.VarChar).maxLength(128).build());

            schema.addField(AddFieldReq.builder().fieldName(vectorField).dataType(DataType.FloatVector).dimension(dimension).build());
            schema.addField(AddFieldReq.builder().fieldName(contentField).dataType(DataType.VarChar).maxLength(65535).build());
            schema.addField(AddFieldReq.builder().fieldName(sourceField).dataType(DataType.VarChar).maxLength(512).build());
            schema.addField(AddFieldReq.builder().fieldName(chunkIndexField).dataType(DataType.Int64).build());

            // 2. 创建集合
            CreateCollectionReq createReq = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .build();
            milvusClient.createCollection(createReq);
            System.out.println("统一向量集合创建成功: " + collectionName);

            // 3. 创建向量索引 (由于数据量会累加，可以使用 IVF_FLAT 或 HNSW)
            IndexParam indexParam = IndexParam.builder()
                    .indexName("vector_index")
                    .fieldName(vectorField)
                    .indexType(IndexParam.IndexType.IVF_FLAT)
                    .metricType(IndexParam.MetricType.COSINE)
                    .extraParams(Collections.singletonMap("nlist", indexNlist))
                    .build();

            CreateIndexReq createIndexReq = CreateIndexReq.builder()
                    .collectionName(collectionName)
                    .indexParams(Collections.singletonList(indexParam))
                    .build();
            milvusClient.createIndex(createIndexReq);

            // 4. 加载到内存
            LoadCollectionReq loadReq = LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            milvusClient.loadCollection(loadReq);
            System.out.println("集合加载完成，准备就绪。");

        } catch (Exception e) {
            System.err.println("初始化集合失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 添加文档块到统一存储
     */
    public void addChunks(List<DocumentChunk> documentChunks) {
        if (documentChunks == null || documentChunks.isEmpty()) {
            return;
        }

        try {
            initCollection(); // 确保集合存在

            List<JsonObject> rows = new ArrayList<>();
            for (DocumentChunk chunk : documentChunks) {
                JsonObject row = new JsonObject();
                row.addProperty(idField, chunk.getId());

                // 【核心】记录所属的 documentId
                row.addProperty(documentIdField, chunk.getDocumentId() != null ? chunk.getDocumentId() : "default");

                JsonArray vectorArray = new JsonArray();
                chunk.getEmbedding().forEach(vectorArray::add);
                row.add(vectorField, vectorArray);

                row.addProperty(contentField, chunk.getContent());
                row.addProperty(sourceField, chunk.getSource());
                row.addProperty(chunkIndexField, chunk.getChunkIndex());
                rows.add(row);
            }

            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(rows)
                    .build();
            milvusClient.insert(insertReq);

            // 刷盘
            milvusClient.flush(FlushReq.builder().collectionNames(Collections.singletonList(collectionName)).build());
            System.out.println("成功插入 " + documentChunks.size() + " 个块。");

        } catch (Exception e) {
            System.err.println("插入失败: " + e.getMessage());
        }
    }

    /**
     * 相似度搜索（支持文档级隔离）
     * @param queryEmbedding 查询向量
     * @param topK 召回数量
     * @param documentId 可选。如果传入，则只在该文档范围内检索；如果为null，则进行全局知识检索
     */
    public List<DocumentChunk> similaritySearch(List<Float> queryEmbedding, int topK, String documentId) {
        long startTime = System.currentTimeMillis();
        boolean searchSuccess = false;
        
        try {
            FloatVec floatVec = new FloatVec(queryEmbedding);

            SearchReq.SearchReqBuilder searchBuilder = SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField(vectorField)
                    .data(Collections.singletonList(floatVec))
                    .topK(topK)
                    .outputFields(List.of(contentField, sourceField, chunkIndexField, documentIdField));

            // 【核心过滤】如果指定了 documentId，就增加标量过滤条件
            if (documentId != null && !documentId.trim().isEmpty()) {
                String filterExpr = documentIdField + " == '" + documentId + "'";
                searchBuilder.filter(filterExpr);
                System.out.println("启用文档隔离检索，Filter: " + filterExpr);
            }

            SearchResp searchResp = milvusClient.search(searchBuilder.build());
            searchSuccess = true;
            List<DocumentChunk> results = new ArrayList<>();

            if (searchResp.getSearchResults() != null && !searchResp.getSearchResults().isEmpty()) {
                for (SearchResp.SearchResult result : searchResp.getSearchResults().get(0)) {
                    DocumentChunk chunk = new DocumentChunk();
                    chunk.setId(String.valueOf(result.getId()));
                    chunk.setDocumentId((String) result.getEntity().get(documentIdField));
                    chunk.setContent((String) result.getEntity().get(contentField));
                    chunk.setSource((String) result.getEntity().get(sourceField));

                    Object indexObj = result.getEntity().get(chunkIndexField);
                    if (indexObj != null) chunk.setChunkIndex(((Number) indexObj).intValue());

                    chunk.setSimilarity(result.getScore());
                    results.add(chunk);
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordMilvusSearch(duration, true);
            System.out.println("Milvus搜索耗时: " + duration + "ms");
            return results;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordMilvusSearch(duration, false);
            System.err.println("检索失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 删除指定文档的所有 Chunk (利用过滤表达式)
     */
    public void deleteDocument(String documentId) {
        if (documentId == null || documentId.trim().isEmpty()) return;

        try {
            // 【核心】使用 DeleteReq 和过滤表达式删除特定文档的数据，无需删除集合
            String filterExpr = documentIdField + " == '" + documentId + "'";
            DeleteReq deleteReq = DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(filterExpr)
                    .build();

            milvusClient.delete(deleteReq);
            System.out.println("成功删除文档的所有分块，DocumentID: " + documentId);

        } catch (Exception e) {
            System.err.println("删除文档失败: " + e.getMessage());
        }
    }

    /**
     * 获取库中所有文档块的总数量
     */
    public int size() {
        try {
            // 先检查集合是否存在
            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            if (!milvusClient.hasCollection(hasReq)) {
                return 0;
            }

            GetCollectionStatsReq req = GetCollectionStatsReq.builder().collectionName(collectionName).build();
            GetCollectionStatsResp resp = milvusClient.getCollectionStats(req);
            return resp.getNumOfEntities().intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取某个文档的块数量
     */
    public int size(String documentId) {
        if (documentId == null || documentId.trim().isEmpty()) return 0;

        try {
            // 先检查集合是否存在
            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            if (!milvusClient.hasCollection(hasReq)) {
                return 0;
            }

            // 使用查询获取该文档的所有数据，然后统计数量
            String filterExpr = documentIdField + " == '" + documentId + "'";
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter(filterExpr)
                    .outputFields(Collections.singletonList(idField))
                    .limit(10000L)  // 设置较大的limit以获取所有数据
                    .build();

            QueryResp resp = milvusClient.query(queryReq);
            if (resp.getQueryResults() != null) {
                return resp.getQueryResults().size();
            }
            return 0;
        } catch (Exception e) {
            System.err.println("获取文档块数量失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 获取数据库中已有的所有 Document ID
     * 注意：对于生产环境，强烈建议这些元数据维护在关系型数据库（如 MySQL）中。
     * 此方法仅供测试或轻量级使用，通过遍历获取去重后的 ID。
     */
    public List<String> getAllDocumentIds() {
        try {
            // 先检查集合是否存在
            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            if (!milvusClient.hasCollection(hasReq)) {
                return new ArrayList<>();
            }

            // 查出全量数据中的 document_id 字段
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter(idField + " != ''") // 匹配所有数据
                    .outputFields(Collections.singletonList(documentIdField))
                    .limit(10000L) // 注意：如果你的 chunk 数量非常大，这里可能会受限制
                    .build();

            QueryResp resp = milvusClient.query(queryReq);
            Set<String> uniqueDocIds = new HashSet<>();

            if (resp.getQueryResults() != null) {
                for (QueryResp.QueryResult result : resp.getQueryResults()) {
                    String docId = (String) result.getEntity().get(documentIdField);
                    if (docId != null) uniqueDocIds.add(docId);
                }
            }
            return new ArrayList<>(uniqueDocIds);
        } catch (Exception e) {
            System.err.println("获取文档列表失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 获取文档ID到文件名的映射
     * @return Map<documentId, filename>
     */
    public Map<String, String> getDocumentIdToFilenameMap() {
        Map<String, String> resultMap = new HashMap<>();
        try {
            // 先检查集合是否存在
            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            if (!milvusClient.hasCollection(hasReq)) {
                return resultMap;
            }

            // 查出全量数据中的 document_id 和 source 字段
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter(idField + " != ''")
                    .outputFields(List.of(documentIdField, sourceField))
                    .limit(10000L)
                    .build();

            QueryResp resp = milvusClient.query(queryReq);
            if (resp.getQueryResults() != null) {
                for (QueryResp.QueryResult result : resp.getQueryResults()) {
                    String docId = (String) result.getEntity().get(documentIdField);
                    String source = (String) result.getEntity().get(sourceField);
                    if (docId != null && source != null && !resultMap.containsKey(docId)) {
                        resultMap.put(docId, source);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("获取文档文件名映射失败: " + e.getMessage());
        }
        return resultMap;
    }

    /**
     * 危险操作：清空整个集合（用于系统重置）
     */
    public void clearAll() {
        try {
            milvusClient.dropCollection(DropCollectionReq.builder().collectionName(collectionName).build());
            System.out.println("集合已整体删除，请重启服务或调用 initCollection() 重新建立。");
        } catch (Exception e) {
            System.err.println("清空集合失败: " + e.getMessage());
        }
    }
}