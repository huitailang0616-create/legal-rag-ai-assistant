package com.ailegal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 法律文书草稿主表（模块5）。
 * 逻辑删除字段 deleted 由业务代码显式过滤，本表禁止物理删除。
 * generation_status 仅 DRAFT/NOT_GENERATED（5A 统一为 NOT_GENERATED），
 * 5B 生成正文后扩展为 GENERATING/GENERATED/GENERATION_FAILED。
 */
@TableName("document_draft")
public class DocumentDraft {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 关联案件 ID（case_analysis.id），可空；为空表示尚未绑定案件 */
    private Long caseAnalysisId;
    private String title;
    /** 文书类型：LABOR_ARBITRATION_APPLICATION / CIVIL_COMPLAINT / DEMAND_LETTER */
    private String documentType;
    /** 领域：LABOR_DISPUTE / HOUSE_RENTAL / CONSUMER_RIGHTS */
    private String domain;
    /** 文书正文；NULL 表示尚未生成（5A 恒为 NULL，5B LLM 生成后写入） */
    private String content;
    /** 生成状态：5A 恒为 NOT_GENERATED */
    private String generationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCaseAnalysisId() { return caseAnalysisId; }
    public void setCaseAnalysisId(Long caseAnalysisId) { this.caseAnalysisId = caseAnalysisId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getGenerationStatus() { return generationStatus; }
    public void setGenerationStatus(String generationStatus) { this.generationStatus = generationStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
