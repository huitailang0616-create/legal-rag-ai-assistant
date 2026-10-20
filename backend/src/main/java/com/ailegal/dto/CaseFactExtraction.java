package com.ailegal.dto;

import java.util.List;

/**
 * 案件事实固定 JSON 提取结果（4B）。
 *
 * 结构冻结，即 4C 风险分析的输入契约，未经任务书评审不得增删字段：
 *   caseSummary       案件摘要（凝练，非原文复制）
 *   disputeFocus      争议焦点
 *   claims            用户明确表达的诉求（无则空数组）
 *   keyDates          事件 + 用户原始时间表达（禁止归一化："三个月前"保持原文）
 *   evidenceItems     用户明确提到的证据（name 识别 + description 内容/形态）
 *   counterparty      对方主体（用户没写就空串，禁止补全）
 *   location          地点（同上）
 *   missingInformation 事实信息缺口（如"未提供具体入职日期"），禁止建议/评价
 *
 * 一型两用：既是 LLM 输出严格反序列化目标（CaseFactExtractionValidator），
 * 也是 CaseAnalysisResponse.extraction 的载荷（不新增第二份 Response DTO）。
 */
public class CaseFactExtraction {

    private String caseSummary;
    private String disputeFocus;
    private List<String> claims;
    private List<KeyDateItem> keyDates;
    private List<EvidenceItem> evidenceItems;
    private DescriptionItem counterparty;
    private DescriptionItem location;
    private List<String> missingInformation;

    public String getCaseSummary() { return caseSummary; }
    public void setCaseSummary(String caseSummary) { this.caseSummary = caseSummary; }
    public String getDisputeFocus() { return disputeFocus; }
    public void setDisputeFocus(String disputeFocus) { this.disputeFocus = disputeFocus; }
    public List<String> getClaims() { return claims; }
    public void setClaims(List<String> claims) { this.claims = claims; }
    public List<KeyDateItem> getKeyDates() { return keyDates; }
    public void setKeyDates(List<KeyDateItem> keyDates) { this.keyDates = keyDates; }
    public List<EvidenceItem> getEvidenceItems() { return evidenceItems; }
    public void setEvidenceItems(List<EvidenceItem> evidenceItems) { this.evidenceItems = evidenceItems; }
    public DescriptionItem getCounterparty() { return counterparty; }
    public void setCounterparty(DescriptionItem counterparty) { this.counterparty = counterparty; }
    public DescriptionItem getLocation() { return location; }
    public void setLocation(DescriptionItem location) { this.location = location; }
    public List<String> getMissingInformation() { return missingInformation; }
    public void setMissingInformation(List<String> missingInformation) { this.missingInformation = missingInformation; }

    /** 关键时间项：event = 发生了什么；dateText = 用户原文时间表达，不得改写 */
    public static class KeyDateItem {
        private String event;
        private String dateText;

        public String getEvent() { return event; }
        public void setEvent(String event) { this.event = event; }
        public String getDateText() { return dateText; }
        public void setDateText(String dateText) { this.dateText = dateText; }
    }

    /** 证据项：name 识别；description 内容/形态，可空 */
    public static class EvidenceItem {
        private String name;
        private String description;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /** 对方主体 / 地点：仅 description，用户没写就是空串 */
    public static class DescriptionItem {
        private String description;

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
