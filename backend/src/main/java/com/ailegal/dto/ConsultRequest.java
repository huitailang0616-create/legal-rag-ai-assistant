package com.ailegal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request of POST /api/consult (3B-1 single-round user consult).
 * Minimal: only a question. No conversation/history fields.
 *
 * caseAnalysisId（可选，Final-Requirement-Gaps-G）：关联已确认案件分析。
 * 非 null 时服务端按当前 userId + caseAnalysisId 重新从 MySQL 读取本人案件，
 * 客户端只允许传 id，禁止直接上传案件事实文本作为可信上下文。
 */
public class ConsultRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过500字符")
    private String question;

    /** 关联案件分析 ID（可选；null = 普通咨询，行为完全不变） */
    private Long caseAnalysisId;

    public ConsultRequest() {
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Long getCaseAnalysisId() {
        return caseAnalysisId;
    }

    public void setCaseAnalysisId(Long caseAnalysisId) {
        this.caseAnalysisId = caseAnalysisId;
    }
}
