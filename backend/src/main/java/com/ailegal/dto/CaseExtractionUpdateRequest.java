package com.ailegal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 4B 人工确认保存请求：携带 extraction（用户可编辑的提取字段），可携带 domain（案件类型，
 * 用户确认时对案件类型的修改，仅允许三个正式领域；不传则不修改）。
 * 客户端不可直接写 user_id / deleted / extraction_confirmed / 其他案件列——
 * 确认写库由服务端显式 SET 指定列完成（SET 子句范围保证防篡改）。
 */
public class CaseExtractionUpdateRequest {

    @NotNull(message = "提取结果不能为空")
    private CaseFactExtraction extraction;

    @Pattern(regexp = "^(LABOR_DISPUTE|HOUSE_RENTAL|CONSUMER_RIGHTS)$",
            message = "案件领域不合法")
    private String domain;

    public CaseFactExtraction getExtraction() { return extraction; }
    public void setExtraction(CaseFactExtraction extraction) { this.extraction = extraction; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
}
