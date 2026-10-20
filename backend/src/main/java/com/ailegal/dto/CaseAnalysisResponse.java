package com.ailegal.dto;

import com.ailegal.entity.CaseAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 案件分析响应。不暴露 deleted / userId / 预留 JSON 字段的原始串。
 * extraction（4B）：结构化案情提取结果，只读展示用；null 表示尚未提取。
 * risk（4C）：结构化风险分析结果，只读展示用；null 表示尚未风险分析。
 * analysisResult（4C）：可读分析说明文本（用户可编辑保存）。
 */
public class CaseAnalysisResponse {

    private static final Logger log = LoggerFactory.getLogger(CaseAnalysisResponse.class);
    /** 只读 lenient mapper：DB 中已校验过的 JSON 列；解析失败绝不导致既有接口回归 */
    private static final ObjectMapper EXTRACTION_READER = new ObjectMapper();

    private Long id;
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
    private CaseFactExtraction extraction;
    private Integer extractionConfirmed;
    private CaseRiskResult risk;
    private String analysisResult;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CaseAnalysisResponse fromEntity(CaseAnalysis ca) {
        CaseAnalysisResponse r = new CaseAnalysisResponse();
        r.id = ca.getId();
        r.title = ca.getTitle();
        r.domain = ca.getDomain();
        r.caseFacts = ca.getCaseFacts();
        r.claims = ca.getClaims();
        r.keyDates = ca.getKeyDates();
        r.evidenceDescription = ca.getEvidenceDescription();
        r.counterpartyDescription = ca.getCounterpartyDescription();
        r.locationDescription = ca.getLocationDescription();
        r.amount = ca.getAmount();
        r.contractStatus = ca.getContractStatus();
        r.analysisStatus = ca.getAnalysisStatus();
        r.extraction = parseExtraction(ca.getExtractionJson());
        r.extractionConfirmed = ca.getExtractionConfirmed();
        r.risk = parseRisk(ca.getRiskResultJson());
        r.analysisResult = ca.getAnalysisResult();
        r.createdAt = ca.getCreatedAt();
        r.updatedAt = ca.getUpdatedAt();
        return r;
    }

    /**
     * 防御性解析：extraction_json 为 null/空白/不可解析时一律返回 null，
     * 绝不因历史脏数据让既有接口回归。失败只记错误类别，不记内容。
     */
    private static CaseFactExtraction parseExtraction(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return EXTRACTION_READER.readValue(json, CaseFactExtraction.class);
        } catch (Exception e) {
            log.warn("extraction_json 解析失败，按 null 返回（不影响既有接口）");
            return null;
        }
    }

    /**
     * 4C：risk_result_json 防御性解析（同上模式）。解析失败按 null 返回：
     * 前端据此不渲染风险卡，不展示可能不一致的旧风险。
     */
    private static CaseRiskResult parseRisk(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return EXTRACTION_READER.readValue(json, CaseRiskResult.class);
        } catch (Exception e) {
            log.warn("risk_result_json 解析失败，按 null 返回（不影响既有接口）");
            return null;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public CaseFactExtraction getExtraction() { return extraction; }
    public Integer getExtractionConfirmed() { return extractionConfirmed; }
    public void setExtractionConfirmed(Integer extractionConfirmed) { this.extractionConfirmed = extractionConfirmed; }
    public void setExtraction(CaseFactExtraction extraction) { this.extraction = extraction; }
    public CaseRiskResult getRisk() { return risk; }
    public void setRisk(CaseRiskResult risk) { this.risk = risk; }
    public String getAnalysisResult() { return analysisResult; }
    public void setAnalysisResult(String analysisResult) { this.analysisResult = analysisResult; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
