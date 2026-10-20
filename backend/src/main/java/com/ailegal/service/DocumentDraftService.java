package com.ailegal.service;

import com.ailegal.dto.DocumentDraftCreateRequest;
import com.ailegal.dto.DocumentDraftResponse;
import com.ailegal.dto.DocumentDraftUpdateRequest;

import java.util.List;

public interface DocumentDraftService {

    DocumentDraftResponse create(DocumentDraftCreateRequest request, Long userId, String username, String ip);

    List<DocumentDraftResponse> listByUser(Long userId);

    DocumentDraftResponse get(Long id, Long userId);

    DocumentDraftResponse update(Long id, DocumentDraftUpdateRequest request, Long userId, String username, String ip);

    void delete(Long id, Long userId, String username, String ip);

    /**
     * 5B 智能生成文书正文（POST /generate，无请求体）。
     * getOwn → 类型/领域兼容校验(400) → caseAnalysisId 非空(400) → 关联案件归属(400)
     * → LLM 草拟两字段 → 严格校验 → Java 固定模板渲染 → 全部成功后单条 UPDATE
     * content + GENERATED + updated_at。任何失败发生在 UPDATE 之前：旧 content/status 原值不变。
     */
    DocumentDraftResponse generate(Long id, Long userId, String username, String ip);

    /**
     * 5B 保存用户编辑后的正文（PUT /content）。仅 GENERATED 可保存（双重保障：
     * Java 前置校验 + UPDATE 的 eq(status) 并发防御）；只显式 SET content/updated_at。
     */
    DocumentDraftResponse updateContent(Long id, String content, Long userId, String username, String ip);

    /**
     * 5B + Gaps-F 导出真实 DOCX（GET /export）。仅本人文书且 GENERATED + content 非空（否则 400）；
     * 读取当前 content，按 documentType 加载预设 .docx 模板（resources/templates/docx/）做段落级
     * 占位符替换（{{TITLE}}/{{CONTENT}}/{{GENERATED_AT}}，split-run 安全；不重新调 LLM、不写磁盘）。
     */
    byte[] exportDocx(Long id, Long userId, String username, String ip);
}
