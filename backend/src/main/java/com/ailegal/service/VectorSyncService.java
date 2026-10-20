package com.ailegal.service;

import java.util.Map;

public interface VectorSyncService {

    /** Returns {total, completed, failed} */
    Map<String, Integer> batchBuildVectors(Long userId, String username, String ip);

    /** Sync single article: embed + upsert. Returns new vectorStatus. */
    String syncArticleVector(Long articleId, Long userId, String username, String ip);

    /** Rebuild vector for an article (delete old point, embed, upsert). */
    String rebuildArticleVector(Long articleId, Long userId, String username, String ip);

    /** Delete Qdrant point for an article, set vector_status=NONE. */
    void deleteArticleVector(Long articleId);

    /** Delete Qdrant points for multiple articles. */
    void deleteArticleVectors(java.util.List<Long> articleIds);

    /** Update only Qdrant payload (no re-embed), set COMPLETED on success. */
    void updatePayload(Long articleId, Map<String, Object> payloadDelta);

    /** Build embedding text from article + document. */
    String buildEmbeddingText(Long articleId);

    /** Recalculate document vector_status from its effective articles. */
    void recalcDocumentVectorStatus(Long documentId);

    /** Refresh vector_status for all documents. Returns count of updated documents. */
    int refreshAllDocumentVectorStatus();
}
