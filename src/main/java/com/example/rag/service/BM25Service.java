package com.example.rag.service;

import com.example.rag.model.DocumentChunk;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * BM25关键词检索服务
 * Lucene全文索引检索服务
 * 使用Lucene实现BM25算法进行关键词匹配检索
 */
@Service
public class BM25Service {

    private Directory directory;
    private Analyzer analyzer;
    private volatile boolean indexBuilt = false;

    @Autowired
    private VectorStoreService vectorStoreService;

    public BM25Service() {
        // 使用内存目录存储索引
        this.directory = new RAMDirectory();
        // 使用标准分析器（支持中文分词）
        this.analyzer = new StandardAnalyzer();
    }

    /**
     * 构建或重建BM25索引
     * @param chunks 文档块列表
     */
    public synchronized void buildIndex(List<DocumentChunk> chunks) {
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE); // 清空重建

            IndexWriter writer = new IndexWriter(directory, config);

            for (DocumentChunk chunk : chunks) {
                Document doc = new Document();
                doc.add(new TextField("id", chunk.getId(), Field.Store.YES));
                doc.add(new TextField("documentId", chunk.getDocumentId(), Field.Store.YES));
                doc.add(new TextField("content", chunk.getContent(), Field.Store.YES));
                doc.add(new TextField("source", chunk.getSource(), Field.Store.YES));
                doc.add(new TextField("chunkIndex", String.valueOf(chunk.getChunkIndex()), Field.Store.YES));
                writer.addDocument(doc);
            }

            writer.commit();
            writer.close();
            indexBuilt = true;
            System.out.println("BM25索引构建完成，文档块数量: " + chunks.size());

        } catch (IOException e) {
            System.err.println("BM25索引构建失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 基于BM25检索相关文档块
     * @param query 查询文本
     * @param topK 返回的文档块数量
     * @param documentId 文档ID（可选，如果指定则只在该文档内搜索）
     * @return 相关文档块列表
     */
    public List<DocumentChunk> search(String query, int topK, String documentId) {
        if (!indexBuilt) {
            System.out.println("BM25索引未构建，先从向量存储加载文档块构建索引");
            List<DocumentChunk> allChunks = vectorStoreService.getAllChunks();
            if (allChunks.isEmpty()) {
                System.out.println("向量存储中没有文档块");
                return new ArrayList<>();
            }
            buildIndex(allChunks);
        }

        List<DocumentChunk> results = new ArrayList<>();

        try {
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);

            // 构建查询
            QueryParser parser = new QueryParser("content", analyzer);
            Query luceneQuery = parser.parse(query);

            // 如果指定了文档ID，添加文档过滤条件
            if (documentId != null && !documentId.isEmpty()) {
                Query docIdQuery = parser.parse("documentId:" + documentId);
                // 使用BooleanQuery组合查询（这里简化处理，实际应该使用BooleanQuery）
                // 为简化，我们直接在结果后过滤
            }

            // 执行搜索
            TopDocs topDocs = searcher.search(luceneQuery, topK * 2); // 多检索一些用于过滤

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);

                // 如果指定了文档ID，进行过滤
                if (documentId != null && !documentId.isEmpty()) {
                    String docDocumentId = doc.get("documentId");
                    if (!documentId.equals(docDocumentId)) {
                        continue;
                    }
                }

                DocumentChunk chunk = new DocumentChunk();
                chunk.setId(doc.get("id"));
                chunk.setDocumentId(doc.get("documentId"));
                chunk.setContent(doc.get("content"));
                chunk.setSource(doc.get("source"));
                chunk.setChunkIndex(Integer.parseInt(doc.get("chunkIndex")));
                chunk.setScore(scoreDoc.score); // BM25分数

                results.add(chunk);

                if (results.size() >= topK) {
                    break;
                }
            }

            reader.close();
            System.out.println("BM25检索完成，返回文档块数量: " + results.size());

        } catch (Exception e) {
            System.err.println("BM25检索失败: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    /**
     * 添加单个文档块到索引
     * @param chunk 文档块
     */
    public synchronized void addDocument(DocumentChunk chunk) {
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            IndexWriter writer = new IndexWriter(directory, config);

            Document doc = new Document();
            doc.add(new TextField("id", chunk.getId(), Field.Store.YES));
            doc.add(new TextField("documentId", chunk.getDocumentId(), Field.Store.YES));
            doc.add(new TextField("content", chunk.getContent(), Field.Store.YES));
            doc.add(new TextField("source", chunk.getSource(), Field.Store.YES));
            doc.add(new TextField("chunkIndex", String.valueOf(chunk.getChunkIndex()), Field.Store.YES));
            writer.addDocument(doc);

            writer.commit();
            writer.close();
            indexBuilt = true;

        } catch (IOException e) {
            System.err.println("BM25添加文档失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 清空索引
     */
    public synchronized void clearIndex() {
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

            IndexWriter writer = new IndexWriter(directory, config);
            writer.commit();
            writer.close();
            indexBuilt = false;
            System.out.println("BM25索引已清空");

        } catch (IOException e) {
            System.err.println("BM25清空索引失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
