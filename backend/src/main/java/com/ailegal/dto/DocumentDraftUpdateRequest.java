package com.ailegal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 编辑文书草稿请求。PUT 为全量覆盖语义：请求中出现的字段整体覆盖；
 * content / generationStatus 由服务端控制，不接受客户端传入。
 */
public class DocumentDraftUpdateRequest {

    @NotBlank(message = "文书标题不能为空")
    @Size(max = 128, message = "文书标题长度不能超过128字符")
    private String title;

    @NotBlank(message = "文书类型不能为空")
    @Pattern(regexp = "^(LABOR_ARBITRATION_APPLICATION|CIVIL_COMPLAINT|DEMAND_LETTER)$",
            message = "文书类型只能是劳动仲裁申请书、民事起诉状或催告函")
    private String documentType;

    @NotBlank(message = "文书领域不能为空")
    @Pattern(regexp = "^(LABOR_DISPUTE|HOUSE_RENTAL|CONSUMER_RIGHTS)$",
            message = "文书领域只能是劳动争议、房屋租赁或消费者权益")
    private String domain;

    /** 关联案件 ID（case_analysis.id），可空；传 null 表示解除关联 */
    private Long caseAnalysisId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public Long getCaseAnalysisId() { return caseAnalysisId; }
    public void setCaseAnalysisId(Long caseAnalysisId) { this.caseAnalysisId = caseAnalysisId; }
}
