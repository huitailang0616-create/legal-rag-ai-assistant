package com.ailegal.dto;

/**
 * 4C 案件风险分析结果（固定 JSON 结构，冻结契约）。
 *
 * 5 个根字段（不增不删）：overallLevel + timeRisk / evidenceRisk / counterpartyRisk / locationRisk。
 * 每项风险 4 个子字段：
 *   level            风险等级（HIGH / MEDIUM / LOW，枚举名序列化）
 *   ruleCode         命中的规则编码（如 TIME_MISSING / EVIDENCE_MULTIPLE）
 *   reason           固定中文说明（信息完整度语义，禁止法律结论）
 *   informationNeeded 需要补充的信息提示；无缺口时为空串 ""（不是 null，不是省略键）
 *
 * 一型两用：既是 risk_result_json 序列化目标（CaseRiskAnalyzer），
 * 也是 CaseAnalysisResponse.risk 的载荷（lenient 防御性解析，失败返回 null）。
 */
public class CaseRiskResult {

    private RiskLevel overallLevel;
    private RiskItem timeRisk;
    private RiskItem evidenceRisk;
    private RiskItem counterpartyRisk;
    private RiskItem locationRisk;

    public RiskLevel getOverallLevel() { return overallLevel; }
    public void setOverallLevel(RiskLevel overallLevel) { this.overallLevel = overallLevel; }
    public RiskItem getTimeRisk() { return timeRisk; }
    public void setTimeRisk(RiskItem timeRisk) { this.timeRisk = timeRisk; }
    public RiskItem getEvidenceRisk() { return evidenceRisk; }
    public void setEvidenceRisk(RiskItem evidenceRisk) { this.evidenceRisk = evidenceRisk; }
    public RiskItem getCounterpartyRisk() { return counterpartyRisk; }
    public void setCounterpartyRisk(RiskItem counterpartyRisk) { this.counterpartyRisk = counterpartyRisk; }
    public RiskItem getLocationRisk() { return locationRisk; }
    public void setLocationRisk(RiskItem locationRisk) { this.locationRisk = locationRisk; }

    /** 单项风险：level / ruleCode / reason / informationNeeded 四子字段，冻结 */
    public static class RiskItem {
        private RiskLevel level;
        private String ruleCode;
        private String reason;
        private String informationNeeded;

        public RiskLevel getLevel() { return level; }
        public void setLevel(RiskLevel level) { this.level = level; }
        public String getRuleCode() { return ruleCode; }
        public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getInformationNeeded() { return informationNeeded; }
        public void setInformationNeeded(String informationNeeded) { this.informationNeeded = informationNeeded; }
    }

    /**
     * 风险等级：LOW(1) < MEDIUM(2) < HIGH(3)。
     * 用显式 severity 而非 ordinal，与枚举声明顺序解耦；Jackson 按枚举名序列化为 "HIGH"/"MEDIUM"/"LOW"。
     */
    public enum RiskLevel {
        LOW(1), MEDIUM(2), HIGH(3);

        private final int severity;

        RiskLevel(int severity) {
            this.severity = severity;
        }

        public int severity() {
            return severity;
        }
    }
}
