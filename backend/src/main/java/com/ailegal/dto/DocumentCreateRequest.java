package com.ailegal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DocumentCreateRequest {

    @NotBlank(message = "法规名称不能为空")
    @Size(max = 256)
    private String documentName;

    @NotBlank(message = "领域不能为空")
    private String domain;

    @NotBlank(message = "类型不能为空")
    private String documentType;

    private String effectiveDate;

    @Size(max = 256)
    private String source;

    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(String effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
