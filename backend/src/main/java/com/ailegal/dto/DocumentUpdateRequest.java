package com.ailegal.dto;

import jakarta.validation.constraints.Size;

public class DocumentUpdateRequest {

    @Size(max = 256)
    private String documentName;

    private String domain;

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
