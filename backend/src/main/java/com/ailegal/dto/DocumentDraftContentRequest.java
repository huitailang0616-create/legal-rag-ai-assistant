package com.ailegal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 5B 文书正文编辑请求（PUT /api/document-drafts/{id}/content）。
 * 仅含 content 一个字段：只能修改 content / updated_at 两列，
 * documentType / domain / caseAnalysisId / generationStatus / userId / deleted
 * 物理上不可被本请求修改（防篡改由 service 的显式 SET 子句范围保证）。
 */
public class DocumentDraftContentRequest {

    @NotBlank(message = "正文内容不能为空")
    @Size(max = 100000, message = "正文内容不能超过100000字符")
    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
