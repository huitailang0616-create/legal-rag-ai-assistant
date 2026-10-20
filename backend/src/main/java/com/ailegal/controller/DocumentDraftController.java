package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.DocumentDraftContentRequest;
import com.ailegal.dto.DocumentDraftCreateRequest;
import com.ailegal.dto.DocumentDraftResponse;
import com.ailegal.dto.DocumentDraftUpdateRequest;
import com.ailegal.security.UserContext;
import com.ailegal.service.DocumentDraftService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 法律文书草稿 USER 业务 API（模块5）。
 * 数据隔离：所有单条操作由 service 按 id + 当前 userId 联合定位（防 IDOR）；
 * 关联案件 case_analysis_id 由 service 校验必须属于当前用户。
 * 鉴权：默认 authenticated 规则（/api/**），未登录 401。
 * 5B 新增：POST /{id}/generate（智能生成）、PUT /{id}/content（编辑正文）、
 * GET /{id}/export（DOCX 导出，真实附件下载）。
 */
@RestController
@RequestMapping("/api/document-drafts")
public class DocumentDraftController {

    private final DocumentDraftService documentDraftService;

    public DocumentDraftController(DocumentDraftService documentDraftService) {
        this.documentDraftService = documentDraftService;
    }

    @PostMapping
    public ApiResponse<DocumentDraftResponse> create(@Valid @RequestBody DocumentDraftCreateRequest request,
                                                     HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(documentDraftService.create(request, userId, username, ip));
    }

    @GetMapping
    public ApiResponse<List<DocumentDraftResponse>> list() {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(documentDraftService.listByUser(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentDraftResponse> get(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(documentDraftService.get(id, userId));
    }

    @PutMapping("/{id}")
    public ApiResponse<DocumentDraftResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody DocumentDraftUpdateRequest request,
                                                     HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(documentDraftService.update(id, request, userId, username, ip));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        documentDraftService.delete(id, userId, username, ip);
        return ApiResponse.success("删除成功", null);
    }

    // ---- 5B：智能生成 / 正文编辑 / DOCX 导出 ----

    /** 智能生成文书正文（无请求体）；仅本人文书，关联案件 + 类型兼容由 service 校验 */
    @PostMapping("/{id}/generate")
    public ApiResponse<DocumentDraftResponse> generate(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(documentDraftService.generate(id, userId, username, ip));
    }

    /** 保存用户编辑后的正文；仅 GENERATED 文书可保存（service 双重保障） */
    @PutMapping("/{id}/content")
    public ApiResponse<DocumentDraftResponse> updateContent(@PathVariable Long id,
                                                            @Valid @RequestBody DocumentDraftContentRequest request,
                                                            HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(documentDraftService.updateContent(id, request.getContent(), userId, username, ip));
    }

    /**
     * DOCX 导出：真实 .docx 附件下载（Apache POI 字节流，不写服务器磁盘）。
     * 文件名 = 安全处理后的文书 title + ".docx"，Content-Disposition 使用 RFC 5987
     * filename* 编码支持中文（title 永不作服务器文件路径，仅作响应头展示名）。
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        String title = documentDraftService.get(id, userId).getTitle();
        byte[] bytes = documentDraftService.exportDocx(id, userId, username, ip);
        String fileName = sanitizeFileName(title) + ".docx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    /** 文件名安全处理：剔除路径/保留字非法字符，防止 title 成为服务器文件路径 */
    private static String sanitizeFileName(String title) {
        String t = title == null ? "文书" : title.trim();
        if (t.isEmpty()) {
            t = "文书";
        }
        return t.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
