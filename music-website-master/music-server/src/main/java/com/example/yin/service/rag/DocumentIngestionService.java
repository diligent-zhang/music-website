package com.example.yin.service.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
public class DocumentIngestionService implements ApplicationRunner {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    private static final String DOCS_DIR = "rag-docs";
    private static final int CHUNK_SIZE = 500;   // 每段最大 500 字
    private static final int OVERLAP = 50;        // 段与段重叠 50 字

    @Override
    public void run(ApplicationArguments args) {
        // 1. 检查目录是否存在
        Path docsPath = Path.of(DOCS_DIR);
        if (!docsPath.toFile().exists() ||
                !docsPath.toFile().isDirectory()) {
            log.warn("RAG 文档目录不存在: {}，跳过知识库构建",
                    docsPath.toAbsolutePath());
            return;
        }

        // 2. 加载所有 .txt 文档
        List<Document> documents =
                FileSystemDocumentLoader.loadDocuments(
                        DOCS_DIR, new TextDocumentParser());

        if (documents.isEmpty()) {
            log.warn("RAG 文档目录为空，跳过知识库构建");
            return;
        }

        log.info("加载 {} 个文档，开始切割...", documents.size());

        // 3. 递归切割文档
        DocumentSplitter splitter =
                DocumentSplitters.recursive(CHUNK_SIZE, OVERLAP);
        List<TextSegment> segments = splitter.splitAll(documents);

        log.info("切割完成，共 {} 个文本片段，开始向量化...",
                segments.size());

        // 4. 逐批向量化并存入 EmbeddingStore
        int batchSize = 50;
        for (int i = 0; i < segments.size(); i += batchSize) {
            int end = Math.min(i + batchSize, segments.size());
            List<TextSegment> batch = segments.subList(i, end);

            List<Embedding> embeddings = embeddingModel.embedAll(batch).content();

            for (int j = 0; j < batch.size(); j++) {
                embeddingStore.add(embeddings.get(j), batch.get(j));
            }

            log.info("向量化进度: {}/{}", end, segments.size());
        }

        log.info("RAG 知识库构建完成！共 {} 个文档 → {} 个向量片段",
                documents.size(), segments.size());
    }
}