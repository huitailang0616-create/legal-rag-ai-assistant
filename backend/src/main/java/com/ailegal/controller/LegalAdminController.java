package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.*;
import com.ailegal.security.UserContext;
import com.ailegal.service.LegalKnowledgeService;
import com.ailegal.service.QdrantService;
import com.ailegal.service.VectorSyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/legal")
@PreAuthorize("hasRole('ADMIN')")
public class LegalAdminController {

    private final LegalKnowledgeService legalKnowledgeService;
    private final VectorSyncService vectorSyncService;
    private final QdrantService qdrantService;

    public LegalAdminController(LegalKnowledgeService legalKnowledgeService,
                                 VectorSyncService vectorSyncService,
                                 QdrantService qdrantService) {
        this.legalKnowledgeService = legalKnowledgeService;
        this.vectorSyncService = vectorSyncService;
        this.qdrantService = qdrantService;
    }

    // ---- Document endpoints ----

    @PostMapping("/documents")
    public ApiResponse<DocumentResponse> createDocument(@Valid @RequestBody DocumentCreateRequest request,
                                                         HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(legalKnowledgeService.createDocument(request, userId, username, ip));
    }

    @GetMapping("/documents")
    public ApiResponse<List<DocumentResponse>> listDocuments(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) Integer validStatus) {
        return ApiResponse.success(legalKnowledgeService.listDocuments(domain, documentType, validStatus));
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<DocumentResponse> getDocument(@PathVariable Long id) {
        return ApiResponse.success(legalKnowledgeService.getDocument(id));
    }

    @PutMapping("/documents/{id}")
    public ApiResponse<DocumentResponse> updateDocument(@PathVariable Long id,
                                                         @Valid @RequestBody DocumentUpdateRequest request,
                                                         HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        DocumentResponse resp = legalKnowledgeService.updateDocument(id, request, userId, username, ip);
        // documentName change → rebuild all article vectors
        if (request.getDocumentName() != null) {
            for (ArticleResponse a : legalKnowledgeService.getArticlesByDocumentId(id)) {
                if (a.getValidStatus() == 1) {
                    vectorSyncService.rebuildArticleVector(a.getId(), userId, username, ip);
                }
            }
        }
        // domain/source change → update payload
        if (request.getDomain() != null || request.getSource() != null) {
            for (ArticleResponse a : legalKnowledgeService.getArticlesByDocumentId(id)) {
                if ("COMPLETED".equals(a.getVectorStatus())) {
                    vectorSyncService.updatePayload(a.getId(), null);
                }
            }
        }
        return ApiResponse.success(resp);
    }

    @PutMapping("/documents/{id}/status")
    public ApiResponse<Void> updateDocumentStatus(@PathVariable Long id,
                                                   @RequestBody UpdateStatusRequest request,
                                                   HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        legalKnowledgeService.updateDocumentStatus(id, request.getStatus(), userId, username, ip);
        List<Long> articleIds = legalKnowledgeService.getArticlesByDocumentId(id).stream()
                .map(ArticleResponse::getId).toList();
        if (request.getStatus() == 0) {
            vectorSyncService.deleteArticleVectors(articleIds);
            for (Long aid : articleIds) vectorSyncService.deleteArticleVector(aid);
        } else {
            for (ArticleResponse a : legalKnowledgeService.getArticlesByDocumentId(id)) {
                if (a.getValidStatus() == 1) {
                    vectorSyncService.syncArticleVector(a.getId(), userId, username, ip);
                }
            }
        }
        return ApiResponse.success("状态更新成功", null);
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long id,
                                             HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        List<Long> articleIds = legalKnowledgeService.getArticlesByDocumentId(id).stream()
                .map(ArticleResponse::getId).toList();
        vectorSyncService.deleteArticleVectors(articleIds);
        legalKnowledgeService.deleteDocument(id, userId, username, ip);
        return ApiResponse.success("删除成功", null);
    }

    // ---- Article endpoints ----

    @PostMapping("/documents/{docId}/articles")
    public ApiResponse<ArticleResponse> addArticle(@PathVariable Long docId,
                                                    @Valid @RequestBody ArticleCreateRequest request,
                                                    HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        ArticleResponse resp = legalKnowledgeService.addArticle(docId, request, userId, username, ip);
        vectorSyncService.syncArticleVector(resp.getId(), userId, username, ip);
        return ApiResponse.success(resp);
    }

    @PutMapping("/articles/{id}")
    public ApiResponse<ArticleResponse> updateArticle(@PathVariable Long id,
                                                       @Valid @RequestBody ArticleUpdateRequest request,
                                                       HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        ArticleResponse resp = legalKnowledgeService.updateArticle(id, request, userId, username, ip);
        if (request.getContent() != null || request.getArticleNumber() != null) {
            vectorSyncService.rebuildArticleVector(id, userId, username, ip);
        }
        return ApiResponse.success(resp);
    }

    @PutMapping("/articles/{id}/status")
    public ApiResponse<Void> updateArticleStatus(@PathVariable Long id,
                                                  @RequestBody UpdateStatusRequest request,
                                                  HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        legalKnowledgeService.updateArticleStatus(id, request.getStatus(), userId, username, ip);
        if (request.getStatus() == 0) {
            vectorSyncService.deleteArticleVector(id);
        } else {
            vectorSyncService.syncArticleVector(id, userId, username, ip);
        }
        return ApiResponse.success("状态更新成功", null);
    }

    @DeleteMapping("/articles/{id}")
    public ApiResponse<Void> deleteArticle(@PathVariable Long id,
                                            HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        vectorSyncService.deleteArticleVector(id);
        legalKnowledgeService.deleteArticle(id, userId, username, ip);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/documents/{docId}/articles")
    public ApiResponse<List<ArticleResponse>> getArticles(@PathVariable Long docId) {
        return ApiResponse.success(legalKnowledgeService.getArticlesByDocumentId(docId));
    }

    // ---- TXT Import ----

    @PostMapping("/documents/import")
    public ApiResponse<TxtImportResult> importTxt(@RequestParam("file") MultipartFile file,
                                                   @RequestParam("documentName") String documentName,
                                                   @RequestParam("domain") String domain,
                                                   @RequestParam("documentType") String documentType,
                                                   @RequestParam(value = "effectiveDate", required = false) String effectiveDate,
                                                   @RequestParam(value = "source", required = false) String source,
                                                   HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        TxtImportResult result = legalKnowledgeService.importTxt(
                file, documentName, domain, documentType, effectiveDate, source, userId, username, ip);
        if (result.isSuccess()) {
            // Vectorize newly imported articles
            List<ArticleResponse> articles = legalKnowledgeService.getArticlesByDocumentId(result.getDocumentId());
            for (ArticleResponse a : articles) {
                vectorSyncService.syncArticleVector(a.getId(), userId, username, ip);
            }
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error(400, result.getMessage());
        }
    }

    // ---- Vector Management ----

    @PostMapping("/vectors/batch-build")
    public ApiResponse<Map<String, Integer>> batchBuildVectors(HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(vectorSyncService.batchBuildVectors(userId, username, ip));
    }

    @PostMapping("/vectors/articles/{id}/rebuild")
    public ApiResponse<String> rebuildArticleVector(@PathVariable Long id,
                                                     HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        String status = vectorSyncService.rebuildArticleVector(id, userId, username, ip);
        return ApiResponse.success(status);
    }

    @GetMapping("/vectors/status")
    public ApiResponse<Map<String, Object>> getVectorStatus() {
        return ApiResponse.success(Map.of(
                "collectionExists", qdrantService.collectionExists(),
                "pointCount", qdrantService.countPoints()
        ));
    }

    @PostMapping("/vectors/refresh-documents")
    public ApiResponse<Map<String, Object>> refreshDocumentVectorStatus() {
        int updated = vectorSyncService.refreshAllDocumentVectorStatus();
        return ApiResponse.success(Map.of(
                "updated", updated,
                "message", "已刷新所有法规的向量状态，共更新 " + updated + " 条"
        ));
    }
}
