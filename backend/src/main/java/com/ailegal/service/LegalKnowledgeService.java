package com.ailegal.service;

import com.ailegal.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface LegalKnowledgeService {

    DocumentResponse createDocument(DocumentCreateRequest request, Long userId, String username, String ip);

    DocumentResponse getDocument(Long id);

    List<DocumentResponse> listDocuments(String domain, String documentType, Integer validStatus);

    DocumentResponse updateDocument(Long id, DocumentUpdateRequest request, Long userId, String username, String ip);

    void updateDocumentStatus(Long id, Integer validStatus, Long userId, String username, String ip);

    void deleteDocument(Long id, Long userId, String username, String ip);

    ArticleResponse addArticle(Long documentId, ArticleCreateRequest request, Long userId, String username, String ip);

    ArticleResponse updateArticle(Long id, ArticleUpdateRequest request, Long userId, String username, String ip);

    void updateArticleStatus(Long id, Integer validStatus, Long userId, String username, String ip);

    void deleteArticle(Long id, Long userId, String username, String ip);

    List<ArticleResponse> getArticlesByDocumentId(Long documentId);

    /**
     * Fetch authoritative article + document data (both valid_status = 1) by article ids.
     * Used by RAG retrieval: Qdrant returns only articleId + vectorScore, all
     * keyword/domain scoring is based on this MySQL data.
     */
    List<LegalArticleDocument> selectValidArticlesWithDocuments(List<Long> articleIds);

    /**
     * 参考法条解析：按 (document_name, article_number) 稳定标识解析为当前有效 legal_article.id
     * （两侧 valid_status=1）。供 REFERENCE_ARTICLE_TOP5_V2 命中判定使用。
     */
    List<LegalArticleDocument> selectValidReferenceIds(List<String> documentNames, List<String> articleNumbers);

    TxtImportResult importTxt(MultipartFile file, String documentName, String domain,
                              String documentType, String effectiveDate, String source,
                              Long userId, String username, String ip);
}
