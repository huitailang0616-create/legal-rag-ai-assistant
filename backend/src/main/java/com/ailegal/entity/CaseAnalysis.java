package com.ailegal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 案件诉求分析主表（模块4 第一阶段）。
 * 逻辑删除字段 deleted 由业务代码显式过滤，本表禁止物理删除。
 */
@TableName("case_analysis")
public class CaseAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String domain;
    private String caseFacts;
    private String claims;
    private String keyDates;
    private String evidenceDescription;
    private String counterpartyDescription;
    private String locationDescription;
    private BigDecimal amount;
    private String contractStatus;
    private String analysisStatus;
    private String extractionJson;
    private Integer extractionConfirmed;
    private String riskResultJson;
    private String analysisResult;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getCaseFacts() { return caseFacts; }
    public void setCaseFacts(String caseFacts) { this.caseFacts = caseFacts; }
    public String getClaims() { return claims; }
    public void setClaims(String claims) { this.claims = claims; }
    public String getKeyDates() { return keyDates; }
    public void setKeyDates(String keyDates) { this.keyDates = keyDates; }
    public String getEvidenceDescription() { return evidenceDescription; }
    public void setEvidenceDescription(String evidenceDescription) { this.evidenceDescription = evidenceDescription; }
    public String getCounterpartyDescription() { return counterpartyDescription; }
    public void setCounterpartyDescription(String counterpartyDescription) { this.counterpartyDescription = counterpartyDescription; }
    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String locationDescription) { this.locationDescription = locationDescription; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getContractStatus() { return contractStatus; }
    public void setContractStatus(String contractStatus) { this.contractStatus = contractStatus; }
    public String getAnalysisStatus() { return analysisStatus; }
    public void setAnalysisStatus(String analysisStatus) { this.analysisStatus = analysisStatus; }
    public String getExtractionJson() { return extractionJson; }
    public void setExtractionJson(String extractionJson) { this.extractionJson = extractionJson; }
    public Integer getExtractionConfirmed() { return extractionConfirmed; }
    public void setExtractionConfirmed(Integer extractionConfirmed) { this.extractionConfirmed = extractionConfirmed; }
    public String getRiskResultJson() { return riskResultJson; }
    public void setRiskResultJson(String riskResultJson) { this.riskResultJson = riskResultJson; }
    public String getAnalysisResult() { return analysisResult; }
    public void setAnalysisResult(String analysisResult) { this.analysisResult = analysisResult; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
