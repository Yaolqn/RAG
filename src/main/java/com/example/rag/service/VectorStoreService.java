package com.example.rag.service;

import com.example.rag.model.DocumentChunk;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.request.data.FloatVec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.QueryResp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量存储服务
 * 使用 Milvus 向量数据库存储文档块及其嵌入向量，提供相似度搜索功能
 */
@Service
public class VectorStoreService {

    @Autowired
    private MilvusClientV2 milvusClient;  // Milvus 客户端

    @Value("${milvus.collection-name:rag_documents}")
    private String collectionName;  // 集合名称

    @Value("${milvus.dimension:2048}")
    private int dimension;  // 向量维度

    private static final String ID_FIELD = "id";
    private static final String VECTOR_FIELD = "vector";
    private static final String CONTENT_FIELD = "content";
    private static final String SOURCE_FIELD = "source";
    private static final String CHUNK_INDEX_FIELD = "chunk_index";

    /**
     * 初始化 Milvus 集合
     * 如果集合不存在则创建
     */
    public void initCollection() {
        try {
            // 检查集合是否存在
            HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            if (milvusClient.hasCollection(hasCollectionReq)) {
                System.out.println("Milvus 集合已存在: " + collectionName);
                return;
            }

            // 【修改1】使用 builder() 创建 Schema
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();

            schema.addField(AddFieldReq.builder()
                    .fieldName(ID_FIELD)
                    .dataType(DataType.VarChar)
                    .maxLength(256)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .description("Document chunk ID")
                    .build());

            schema.addField(AddFieldReq.builder()
                    .fieldName(VECTOR_FIELD)
                    .dataType(DataType.FloatVector)
                    .dimension(dimension)
                    .description("Document embedding vector")
                    .build());

            schema.addField(AddFieldReq.builder()
                    .fieldName(CONTENT_FIELD)
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .description("Document chunk content")
                    .build());

            schema.addField(AddFieldReq.builder()
                    .fieldName(SOURCE_FIELD)
                    .dataType(DataType.VarChar)
                    .maxLength(512)
                    .description("Document source")
                    .build());

            schema.addField(AddFieldReq.builder()
                    .fieldName(CHUNK_INDEX_FIELD)
                    .dataType(DataType.Int64)
                    .description("Chunk index in document")
                    .build());

            CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .build();

            milvusClient.createCollection(createCollectionReq);
            System.out.println("Milvus 集合创建成功: " + collectionName);

            // 【修改2】V2 SDK 中创建索引需要使用 CreateIndexReq
            Map<String, Object> extraParams = new HashMap<>();
            extraParams.put("nlist", 128);

            IndexParam indexParam = IndexParam.builder()
                    .indexName("vector_index")
                    .fieldName(VECTOR_FIELD)
                    .indexType(IndexParam.IndexType.IVF_FLAT)
                    .metricType(IndexParam.MetricType.COSINE)
                    .extraParams(extraParams)
                    .build();

            CreateIndexReq createIndexReq = CreateIndexReq.builder()
                    .collectionName(collectionName)
                    .indexParams(Collections.singletonList(indexParam))
                    .build();

            milvusClient.createIndex(createIndexReq);
            System.out.println("Milvus 索引创建成功");

            // 加载集合到内存
            LoadCollectionReq loadCollectionReq = LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            milvusClient.loadCollection(loadCollectionReq);
            System.out.println("Milvus 集合加载到内存");

        } catch (Exception e) {
            System.err.println("初始化 Milvus 集合失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 添加文档块到向量存储
     * @param documentChunks 文档块列表
     */
    public void addChunks(List<DocumentChunk> documentChunks) {
        try {
            initCollection();

            // 【修改3】V2 SDK 推荐使用 Gson 的 JsonObject 列表来插入基于行的数据
            List<JsonObject> rows = new ArrayList<>();
            for (DocumentChunk chunk : documentChunks) {
                JsonObject row = new JsonObject();
                row.addProperty(ID_FIELD, chunk.getId());

                // 处理浮点向量
                JsonArray vectorArray = new JsonArray();
                chunk.getEmbedding().forEach(vectorArray::add);
                row.add(VECTOR_FIELD, vectorArray);

                row.addProperty(CONTENT_FIELD, chunk.getContent());
                row.addProperty(SOURCE_FIELD, chunk.getSource());
                row.addProperty(CHUNK_INDEX_FIELD, chunk.getChunkIndex());

                rows.add(row);
            }

            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(rows)
                    .build();

            milvusClient.insert(insertReq);
            System.out.println("成功插入 " + documentChunks.size() + " 个文档块到 Milvus");

            // 刷新数据
            FlushReq flushReq = FlushReq.builder()
                    .collectionNames(Collections.singletonList(collectionName))
                    .build();
            milvusClient.flush(flushReq);

        } catch (Exception e) {
            System.err.println("插入文档块到 Milvus 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 基于余弦相似度搜索相关文档块
     */
    public List<DocumentChunk> similaritySearch(List<Float> queryEmbedding, int topK) {
        try {
            HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            if (!milvusClient.hasCollection(hasCollectionReq)) {
                System.out.println("集合不存在，返回空结果");
                return new ArrayList<>();
            }

            LoadCollectionReq loadCollectionReq = LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            milvusClient.loadCollection(loadCollectionReq);

            // 【修改4】FloatVec 可以直接接收 List<Float>，无需手动转换 float[]
            FloatVec floatVec = new FloatVec(queryEmbedding);

            SearchReq searchReq = SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField(VECTOR_FIELD)
                    .data(Collections.singletonList(floatVec))
                    .topK(topK)
                    .outputFields(List.of(CONTENT_FIELD, SOURCE_FIELD, CHUNK_INDEX_FIELD))
                    .build();

            SearchResp searchResp = milvusClient.search(searchReq);

            List<DocumentChunk> results = new ArrayList<>();
            if (searchResp.getSearchResults() != null && !searchResp.getSearchResults().isEmpty()) {
                List<SearchResp.SearchResult> searchResults = searchResp.getSearchResults().get(0);

                for (SearchResp.SearchResult result : searchResults) {
                    DocumentChunk chunk = new DocumentChunk();
                    // 【修改5】安全类型转换
                    chunk.setId(String.valueOf(result.getId()));
                    chunk.setContent((String) result.getEntity().get(CONTENT_FIELD));
                    chunk.setSource((String) result.getEntity().get(SOURCE_FIELD));

                    Object chunkIndexObj = result.getEntity().get(CHUNK_INDEX_FIELD);
                    if (chunkIndexObj != null) {
                        chunk.setChunkIndex(((Number) chunkIndexObj).intValue());
                    }

                    chunk.setSimilarity(result.getScore());
                    results.add(chunk);
                }
            }

            System.out.println("从 Milvus 检索到 " + results.size() + " 个文档块");
            return results;

        } catch (Exception e) {
            System.err.println("Milvus 搜索失败: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 清空向量存储
     */
    public void clear() {
        try {
            HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            if (milvusClient.hasCollection(hasCollectionReq)) {
                DropCollectionReq dropReq = DropCollectionReq.builder()
                        .collectionName(collectionName)
                        .build();
                milvusClient.dropCollection(dropReq);
                System.out.println("Milvus 集合已删除: " + collectionName);
            }
        } catch (Exception e) {
            System.err.println("删除 Milvus 集合失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取存储的文档块数量
     */
    public int size() {
        try {
            HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            if (!milvusClient.hasCollection(hasCollectionReq)) {
                return 0;
            }
            GetCollectionStatsReq getStatsReq = GetCollectionStatsReq.builder()
                    .collectionName(collectionName)
                    .build();

            // 【修改6】V2 SDK 中获取行数的方法变更
            GetCollectionStatsResp resp = milvusClient.getCollectionStats(getStatsReq);
            return resp.getNumOfEntities().intValue();
        } catch (Exception e) {
            System.err.println("获取集合统计信息失败: " + e.getMessage());
            return 0;
        }
    }

//    /**
//     * 获取存储的文档块数量
//     * @return 文档块数量
//     */
//    public int size() {
//        try {
//            // 检查集合是否存在
//            HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
//                    .collectionName(collectionName)
//                    .build();
//            if (!milvusClient.hasCollection(hasCollectionReq)) {
//                return 0;
//            }
//
//            // 使用 count(*) 进行查询统计（Milvus V2 推荐的精准统计方式）
//            QueryReq queryReq = QueryReq.builder()
//                    .collectionName(collectionName)
//                    .filter("") // 空的 filter 表示匹配全部
//                    .outputFields(List.of("count(*)"))
//                    .build();
//
//            QueryResp queryResp = milvusClient.query(queryReq);
//
//            // 解析 count(*) 的返回结果
//            if (queryResp.getQueryResults() != null && !queryResp.getQueryResults().isEmpty()) {
//                Object countObj = queryResp.getQueryResults().get(0).getEntity().get("count(*)");
//                if (countObj instanceof Number) {
//                    return ((Number) countObj).intValue();
//                }
//            }
//            return 0;
//
//        } catch (Exception e) {
//            System.err.println("获取集合统计信息失败: " + e.getMessage());
//            e.printStackTrace();
//            return 0;
//        }
//    }
}