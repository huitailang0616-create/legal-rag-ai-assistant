package com.ailegal.service.impl;

import com.ailegal.entity.LegalArticle;
import com.ailegal.entity.LegalDocument;
import com.ailegal.mapper.LegalArticleMapper;
import com.ailegal.mapper.LegalDocumentMapper;
import com.ailegal.service.EmbeddingService;
import com.ailegal.service.OperationLogService;
import com.ailegal.service.QdrantService;
import com.ailegal.service.VectorSyncService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class VectorSyncServiceImpl implements VectorSyncService {

    private static final Logger log = LoggerFactory.getLogger(VectorSyncServiceImpl.class);

    private final LegalArticleMapper articleMapper;
    private final LegalDocumentMapper documentMapper;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final OperationLogService operationLogService;

    public VectorSyncServiceImpl(LegalArticleMapper articleMapper,
                                  LegalDocumentMapper documentMapper,
                                  EmbeddingService embeddingService,
                                  QdrantService qdrantService,
                                  OperationLogService operationLogService) {
        this.articleMapper = articleMapper;
        this.documentMapper = documentMapper;
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.operationLogService = operationLogService;
    }

    @Override
    public Map<String, Integer> batchBuildVectors(Long userId, String username, String ip) {
        int total = 0, completed = 0, failed = 0;
        Set<Long> affectedDocIds = new HashSet<>();

        // Find effective articles needing vectors
        LambdaQueryWrapper<LegalArticle> q = new LambdaQueryWrapper<>();
        q.eq(LegalArticle::getValidStatus, 1)
                .in(LegalArticle::getVectorStatus, "NONE", "FAILED")
                .orderByAsc(LegalArticle::getId);
        List<LegalArticle> articles = articleMapper.selectList(q);

        for (LegalArticle article : articles) {
            LegalDocument doc = documentMapper.selectById(article.getDocumentId());
            if (doc == null || doc.getValidStatus() != 1) {
                continue; // Skip if document is invalid/deleted
            }
            total++;
            affectedDocIds.add(article.getDocumentId());
            // Set PENDING
            article.setVectorStatus("PENDING");
            article.setUpdatedAt(LocalDateTime.now());
            articleMapper.updateById(article);

            // Phase 1: Embedding
            float[] vector;
            try {
                String text = buildEmbeddingText(article, doc);
                vector = embeddingService.embed(text);
            } catch (Exception e) {
                log.error("Embedding failed for article id={}", article.getId(), e);
                article.setVectorStatus("FAILED");
                article.setUpdatedAt(LocalDateTime.now());
                articleMapper.updateById(article);
                operationLogService.logFailure(userId, username, "VECTOR_EMBED_FAILED",
                        "articleId=" + article.getId(), e.getMessage(), ip);
                failed++;
                continue;
            }

            // Phase 2: Qdrant upsert
            try {
                Map<String, Object> payload = buildPayload(article, doc);
                qdrantService.upsertPoint(article.getId(), vector, payload);

                article.setVectorStatus("COMPLETED");
                article.setUpdatedAt(LocalDateTime.now());
                articleMapper.updateById(article);
                completed++;
            } catch (Exception e) {
                log.error("Qdrant upsert failed for article id={}", article.getId(), e);
                article.setVectorStatus("FAILED");
                article.setUpdatedAt(LocalDateTime.now());
                articleMapper.updateById(article);
                operationLogService.logFailure(userId, username, "VECTOR_QDRANT_FAILED",
                        "articleId=" + article.getId(), e.getMessage(), ip);
                failed++;
            }
        }

        // Recalc document vector_status for all affected documents
        for (Long docId : affectedDocIds) {
            recalcDocumentVectorStatus(docId);
        }

        operationLogService.log(userId, username, "VECTOR_BATCH_BUILD",
                "legal_articles", "完成" + completed + "条，失败" + failed + "条", ip);

        return Map.of("total", total, "completed", completed, "failed", failed);
    }

    @Override
    public String syncArticleVector(Long articleId, Long userId, String username, String ip) {
        LegalArticle article = articleMapper.selectById(articleId);
        if (article == null) return "NONE";

        LegalDocument doc = documentMapper.selectById(article.getDocumentId());
        if (doc == null || doc.getValidStatus() != 1 || article.getValidStatus() != 1) {
            return "NONE";
        }

        // Phase 1: Embedding
        float[] vector;
        try {
            article.setVectorStatus("PENDING");
            article.setUpdatedAt(LocalDateTime.now());
            articleMapper.updateById(article);

            String text = buildEmbeddingText(article, doc);
            vector = embeddingService.embed(text);
        } catch (Exception e) {
            log.error("Embedding failed for article id={}", articleId, e);
            article.setVectorStatus("FAILED");
            article.setUpdatedAt(LocalDateTime.now());
            articleMapper.updateById(article);
            recalcDocumentVectorStatus(article.getDocumentId());
            operationLogService.logFailure(userId, username, "VECTOR_EMBED_FAILED",
                    "articleId=" + articleId, e.getMessage(), ip);
            return "FAILED";
        }

        // Phase 2: Qdrant upsert
        try {
            Map<String, Object> payload = buildPayload(article, doc);
            qdrantService.upsertPoint(article.getId(), vector, payload);

            article.setVectorStatus("COMPLETED");
            article.setUpdatedAt(LocalDateTime.now());
            articleMapper.updateById(article);

            recalcDocumentVectorStatus(article.getDocumentId());

            operationLogService.log(userId, username, "VECTOR_UPSERT",
                    "articleId=" + articleId, "向量化成功", ip);

            return "COMPLETED";
        } catch (Exception e) {
            log.error("Qdrant upsert failed for article id={}", articleId, e);
            article.setVectorStatus("FAILED");
            article.setUpdatedAt(LocalDateTime.now());
            articleMapper.updateById(article);
            recalcDocumentVectorStatus(article.getDocumentId());
            operationLogService.logFailure(userId, username, "VECTOR_QDRANT_FAILED",
                    "articleId=" + articleId, e.getMessage(), ip);
            return "FAILED";
        }
    }

    @Override
    public String rebuildArticleVector(Long articleId, Long userId, String username, String ip) {
        // Delete old point first, then rebuild
        try {
            qdrantService.deletePoint(articleId);
        } catch (Exception e) {
            log.warn("Failed to delete old point for article id={} (may not exist)", articleId);
        }
        return syncArticleVector(articleId, userId, username, ip);
    }

    @Override
    public void deleteArticleVector(Long articleId) {
        try {
            qdrantService.deletePoint(articleId);
        } catch (Exception e) {
            log.warn("Failed to delete point for article id={} (may not exist)", articleId);
        }
        LegalArticle article = articleMapper.selectById(articleId);
        if (article != null) {
            article.setVectorStatus("NONE");
            article.setUpdatedAt(LocalDateTime.now());
            articleMapper.updateById(article);
            recalcDocumentVectorStatus(article.getDocumentId());
        }
    }

    @Override
    public void deleteArticleVectors(List<Long> articleIds) {
        if (articleIds.isEmpty()) return;
        try {
            qdrantService.deletePoints(articleIds);
        } catch (Exception e) {
            log.warn("Failed to delete points for article ids (may not exist)", e);
        }
        // Recalc affected document vector_status
        Set<Long> affectedDocIds = new HashSet<>();
        for (Long articleId : articleIds) {
            LegalArticle article = articleMapper.selectById(articleId);
            if (article != null) {
                affectedDocIds.add(article.getDocumentId());
            }
        }
        for (Long docId : affectedDocIds) {
            recalcDocumentVectorStatus(docId);
        }
    }

    @Override
    public void updatePayload(Long articleId, Map<String, Object> payloadDelta) {
        LegalArticle article = articleMapper.selectById(articleId);
        if (article == null) return;
        LegalDocument doc = documentMapper.selectById(article.getDocumentId());
        if (doc == null) return;

        try {
            article.setVectorStatus("PENDING");
            article.setUpdatedAt(LocalDateTime.now());
            articleMapper.updateById(article);

            Map<String, Object> fullPayload = buildPayload(article, doc);
            // We need to re-upsert to update payload; get current vector first, or re-embed
            // Since domain/source don't change embedding text, we re-embed (simplest)
            String text = buildEmbeddingText(article, doc);
            float[] vector = embeddingService.embed(text);
            qdrantService.upsertPoint(article.getId(), vector, fullPayload);

            article.setVectorStatus("COMPLETED");
            article.setUpdatedAt(LocalDateTime.now());
            articleMapper.updateById(article);
            recalcDocumentVectorStatus(article.getDocumentId());
        } catch (Exception e) {
            log.error("Failed to update payload for article id={}, deleting point", articleId, e);
            try { qdrantService.deletePoint(articleId); } catch (Exception ignored) {}
            article.setVectorStatus("FAILED");
            article.setUpdatedAt(LocalDateTime.now());
            articleMapper.updateById(article);
            recalcDocumentVectorStatus(article.getDocumentId());
        }
    }

    @Override
    public String buildEmbeddingText(Long articleId) {
        LegalArticle article = articleMapper.selectById(articleId);
        if (article == null) throw new IllegalArgumentException("Article not found: " + articleId);
        LegalDocument doc = documentMapper.selectById(article.getDocumentId());
        if (doc == null) throw new IllegalArgumentException("Document not found for article: " + articleId);
        return buildEmbeddingText(article, doc);
    }

    @Override
    public void recalcDocumentVectorStatus(Long documentId) {
        LegalDocument doc = documentMapper.selectById(documentId);
        if (doc == null) return;

        // Rule 1: invalid document → NONE
        if (doc.getValidStatus() == null || doc.getValidStatus() != 1) {
            if (!"NONE".equals(doc.getVectorStatus())) {
                doc.setVectorStatus("NONE");
                doc.setUpdatedAt(LocalDateTime.now());
                documentMapper.updateById(doc);
            }
            return;
        }

        // Query effective articles under this document
        LambdaQueryWrapper<LegalArticle> q = new LambdaQueryWrapper<>();
        q.eq(LegalArticle::getDocumentId, documentId)
         .eq(LegalArticle::getValidStatus, 1);
        List<LegalArticle> articles = articleMapper.selectList(q);

        // Rule 2: no effective articles → NONE
        String newStatus;
        if (articles.isEmpty()) {
            newStatus = "NONE";
        } else {
            boolean hasFailed = articles.stream().anyMatch(a -> "FAILED".equals(a.getVectorStatus()));
            boolean hasPending = articles.stream().anyMatch(a -> "PENDING".equals(a.getVectorStatus()));
            boolean allCompleted = articles.stream().allMatch(a -> "COMPLETED".equals(a.getVectorStatus()));

            // Priority: FAILED > PENDING > COMPLETED > NONE
            if (hasFailed) {
                newStatus = "FAILED";
            } else if (hasPending) {
                newStatus = "PENDING";
            } else if (allCompleted) {
                newStatus = "COMPLETED";
            } else {
                newStatus = "NONE";
            }
        }

        if (!newStatus.equals(doc.getVectorStatus())) {
            doc.setVectorStatus(newStatus);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
        }
    }

    @Override
    public int refreshAllDocumentVectorStatus() {
        List<LegalDocument> allDocs = documentMapper.selectList(null);
        int updated = 0;
        for (LegalDocument doc : allDocs) {
            String oldStatus = doc.getVectorStatus();
            recalcDocumentVectorStatus(doc.getId());
            // Check if changed by re-reading
            LegalDocument updatedDoc = documentMapper.selectById(doc.getId());
            if (updatedDoc != null && !oldStatus.equals(updatedDoc.getVectorStatus())) {
                updated++;
            }
        }
        log.info("Refreshed document vector status: {} documents updated out of {}", updated, allDocs.size());
        return updated;
    }

    private String buildEmbeddingText(LegalArticle article, LegalDocument doc) {
        return article.getArticleNumber() + " " + doc.getDocumentName() + " " + article.getContent();
    }

    private Map<String, Object> buildPayload(LegalArticle article, LegalDocument doc) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("articleId", article.getId());
        payload.put("documentId", doc.getId());
        payload.put("documentName", doc.getDocumentName());
        payload.put("articleNumber", article.getArticleNumber());
        payload.put("domain", doc.getDomain());
        payload.put("documentType", doc.getDocumentType());
        payload.put("source", doc.getSource() != null ? doc.getSource() : "");
        payload.put("effectiveDate", doc.getEffectiveDate() != null ? doc.getEffectiveDate().toString() : "");
        payload.put("documentValidStatus", doc.getValidStatus());
        payload.put("articleValidStatus", article.getValidStatus());
        return payload;
    }
}
