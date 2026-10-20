package com.ailegal.dto;

/**
 * 5B 文书生成 LLM 最小输出：仅 factsDraft / reasoningDraft 两个字段。
 * LLM 职责极度收敛——只草拟"事实描述 + 理由描述"两段文字；
 * 完整文书由 Java 固定模板（DocumentTemplateService）组装，
 * LLM 永不直接输出整份文书，也不得输出第三个自由字段。
 * 本 DTO 不落库：生成流程解析校验后立即渲染进模板，仅组装后的 content 存入 document_draft。
 */
public class DocumentGenerationDraft {

    private String factsDraft;
    private String reasoningDraft;

    public String getFactsDraft() { return factsDraft; }
    public void setFactsDraft(String factsDraft) { this.factsDraft = factsDraft; }
    public String getReasoningDraft() { return reasoningDraft; }
    public void setReasoningDraft(String reasoningDraft) { this.reasoningDraft = reasoningDraft; }
}
