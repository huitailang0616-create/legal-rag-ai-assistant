package com.ailegal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 评测批次（模块6）。
 * 一次管理员发起的评测 = 一行；status: PENDING/RUNNING/COMPLETED/FAILED。
 */
@TableName("evaluation_run")
public class EvaluationRun {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String status;
    /** 命中口径版本：LEGACY_KEYWORD_V1=旧关键词口径（历史run）/ REFERENCE_ARTICLE_TOP5_V2=参考法条Hit@5 */
    private String metricVersion;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    /** sys_user.id，不建外键（遵循项目惯例） */
    private Long createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMetricVersion() { return metricVersion; }
    public void setMetricVersion(String metricVersion) { this.metricVersion = metricVersion; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
