package com.ailegal.service;

import com.ailegal.dto.CaseFactExtraction;
import com.ailegal.dto.CaseRiskResult;
import com.ailegal.dto.CaseRiskResult.RiskItem;
import com.ailegal.dto.CaseRiskResult.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 4C 案件风险确定性分析器（纯 Java 规则，禁止 LLM / RAG / 随机 / 时间参与）。
 *
 * 输入契约：4B 已校验的 CaseFactExtraction（canonical 写入，字段保证非 null——
 * keyDates/evidenceItems 非 null（可为空数组），counterparty/location 非 null（description 可为空串））。
 * 输出：CaseRiskResult（四类信息完整度风险 + overallLevel）与可读 analysis_result 文本。
 *
 * 风险语义 = "根据当前提交的案件资料评估信息完整度与后续处理信息风险"，是信息缺口提示，
 * 不是胜诉概率、不是法律意见、不引用法条。措辞表冻结（reason/informationNeeded 固定）。
 * 确定性：同一 extraction 任意次执行，risk JSON 与 analysis 文本字节级一致（词表只做 contains
 * 判定，禁止遍历 Set 输出——Set.of 迭代顺序跨运行不稳定）。
 */
@Component
public class CaseRiskAnalyzer {

    /** risk_result_json 序列化用（输出方向，普通 mapper 即可） */
    private static final ObjectMapper JSON_WRITER = new ObjectMapper();

    /** 显式时间 = 含 4 位年份（"2026年5月"、"2026-08-01"）；仅"5月"等无年份表达归 MEDIUM。中文数字年份（如"二〇二六年"）不识别 → MEDIUM，已知局限。 */
    private static final Pattern EXPLICIT_DATE = Pattern.compile("\\d{4}");

    /** 对方主体泛称词表：剥掉量词前缀后做整串 contains 判定；新增泛称词 = 集合加一项，无分支膨胀 */
    private static final Set<String> GENERIC_ROLE_TOKENS = Set.of(
            "公司", "单位", "企业", "商家", "卖家", "买家", "店铺", "店家",
            "房东", "租客", "老板", "中介", "平台", "开发商", "物业", "商户",
            "用人单位", "用工单位", "用工方", "对方");

    /** 量词前缀（非"某"开头）：先剥前缀再查词表，"一家公司"→"公司" 命中泛称。 "某房东"由 startsWith("某") 分支覆盖 */
    private static final String[] QUANTIFIER_PREFIXES = {"一个", "一家", "一位", "一名", "一间"};

    /** "未提供"类表述：仅全串相等才判 MISSING（否则"不知道"会被误判 PRESENT） */
    private static final Set<String> UNKNOWN_TOKENS = Set.of(
            "不知道", "不清楚", "不详", "未提供", "忘了", "记不清", "无", "暂未确定");

    /** 地点具体化标记：命中任一即判 SPECIFIC（如"杭州市西湖区""文三路123号"）。已知边缘误判："市区"含"区"→ SPECIFIC，低概率，接受 */
    private static final Set<String> SPECIFIC_MARKERS = Set.of(
            "区", "县", "镇", "乡", "街道", "路", "街", "巷", "号", "村",
            "小区", "大厦", "广场", "商场", "园区", "桥", "弄", "栋", "座", "单元", "室");

    /**
     * 计算四类风险 + overallLevel（= 四类取最高，无加权）。
     * 纯函数：无随机、无时间、无外部调用。
     */
    public CaseRiskResult analyze(CaseFactExtraction ext) {
        RiskItem time = analyzeTime(ext.getKeyDates());
        RiskItem evidence = analyzeEvidence(ext.getEvidenceItems());
        RiskItem counterparty = analyzeCounterparty(ext.getCounterparty());
        RiskItem location = analyzeLocation(ext.getLocation());

        CaseRiskResult r = new CaseRiskResult();
        r.setOverallLevel(maxLevel(time.getLevel(), evidence.getLevel(), counterparty.getLevel(), location.getLevel()));
        r.setTimeRisk(time);
        r.setEvidenceRisk(evidence);
        r.setCounterpartyRisk(counterparty);
        r.setLocationRisk(location);
        return r;
    }

    /** 将结果序列化为规范 JSON 字符串（by construction 合法，字段声明顺序即输出顺序）。 */
    public String toRiskJson(CaseRiskResult risk) {
        try {
            return JSON_WRITER.writeValueAsString(risk);
        } catch (Exception e) {
            // 纯 DTO 序列化失败仅说明内部错误；调用方失败原子性保证此时尚未写库
            throw new IllegalStateException("risk_result_json 序列化失败", e);
        }
    }

    // ---- 四类规则（均为 CaseFactExtraction 的纯函数） ----

    private static RiskItem analyzeTime(List<CaseFactExtraction.KeyDateItem> keyDates) {
        if (keyDates == null || keyDates.isEmpty()) {
            return item(RiskLevel.HIGH, "TIME_MISSING",
                    "未提供关键时间信息",
                    "请补充入职、签约、购买、争议发生等关键时间（如 2026年5月）");
        }
        for (CaseFactExtraction.KeyDateItem kd : keyDates) {
            if (kd.getDateText() != null && EXPLICIT_DATE.matcher(kd.getDateText()).find()) {
                return item(RiskLevel.LOW, "TIME_PRESENT",
                        "已提供明确时间信息",
                        "");
            }
        }
        return item(RiskLevel.MEDIUM, "TIME_VAGUE",
                "关键时间表达模糊，缺少明确年月日",
                "请补充明确的时间表达（如 2026年5月）");
    }

    private static RiskItem analyzeEvidence(List<CaseFactExtraction.EvidenceItem> evidenceItems) {
        int n = evidenceItems == null ? 0 : evidenceItems.size();
        if (n == 0) {
            return item(RiskLevel.HIGH, "EVIDENCE_NONE",
                    "未提及任何证据",
                    "请补充与案件相关的证据（如合同、聊天记录、转账记录等）");
        }
        if (n == 1) {
            return item(RiskLevel.MEDIUM, "EVIDENCE_SINGLE",
                    "仅提及1项证据，证据形式较为单一",
                    "请补充更多与案件相关的证据材料");
        }
        return item(RiskLevel.LOW, "EVIDENCE_MULTIPLE",
                "已提及多项证据",
                "");
    }

    private static RiskItem analyzeCounterparty(CaseFactExtraction.DescriptionItem counterparty) {
        String t = (counterparty == null || counterparty.getDescription() == null)
                ? "" : counterparty.getDescription().strip();
        if (t.isEmpty() || UNKNOWN_TOKENS.contains(t)) {
            return item(RiskLevel.HIGH, "COUNTERPARTY_MISSING",
                    "未提供对方主体信息",
                    "请补充对方主体信息（如公司名称、房东姓名等）");
        }
        // 泛称判断（禁止 if equals 死串）：“某房东”/“某公司”命中前缀；“一家公司”剥量词后命中词表。
        // 注意词表只做 contains 判定，绝不遍历输出。
        if (t.startsWith("某")) {
            return item(RiskLevel.MEDIUM, "COUNTERPARTY_GENERIC",
                    "对方主体描述较为笼统",
                    "请补充更具体的对方主体信息");
        }
        if (GENERIC_ROLE_TOKENS.contains(stripQuantifier(t))) {
            return item(RiskLevel.MEDIUM, "COUNTERPARTY_GENERIC",
                    "对方主体描述较为笼统",
                    "请补充更具体的对方主体信息");
        }
        return item(RiskLevel.LOW, "COUNTERPARTY_PRESENT",
                "已提供较为具体的对方主体信息",
                "");
    }

    private static RiskItem analyzeLocation(CaseFactExtraction.DescriptionItem location) {
        String t = (location == null || location.getDescription() == null)
                ? "" : location.getDescription().strip();
        if (t.isEmpty() || UNKNOWN_TOKENS.contains(t)) {
            return item(RiskLevel.HIGH, "LOCATION_MISSING",
                    "未提供案件相关地点信息",
                    "请补充案件发生地、工作地、房屋所在地、消费地点等信息");
        }
        if (containsAnyMarker(t)) {
            return item(RiskLevel.LOW, "LOCATION_SPECIFIC",
                    "已提供较为具体的地点信息",
                    "");
        }
        return item(RiskLevel.MEDIUM, "LOCATION_GENERAL",
                "地点信息仅到省市，较为宽泛",
                "请补充区县、街道、详细地址等具体地点信息");
    }

    // ---- analysis_result 文本模板（确定性拼接，措辞冻结） ----

    /**
     * 生成可读分析说明。整体结构：overall 固定句 + 四类风险（等级标签 + 固定 reason + 可选
     * informationNeeded）+ 复用 extraction 的 missingInformation + 固定免责声明。
     * 所有文字来自本类固定措辞，无随机/时间参与。
     */
    public String buildAnalysisResult(CaseFactExtraction ext, CaseRiskResult risk) {
        StringBuilder sb = new StringBuilder();
        sb.append("案件信息风险提示（信息完整度：").append(risk.getOverallLevel()).append("）\n\n");
        sb.append(overallSentence(risk.getOverallLevel())).append("\n\n");
        appendSection(sb, "一、关键时间", risk.getTimeRisk());
        appendSection(sb, "二、现有证据", risk.getEvidenceRisk());
        appendSection(sb, "三、对方主体", risk.getCounterpartyRisk());
        appendSection(sb, "四、地点", risk.getLocationRisk());
        if (ext.getMissingInformation() != null && !ext.getMissingInformation().isEmpty()) {
            sb.append("五、其他待补充信息：").append(String.join("；", ext.getMissingInformation())).append("\n");
        }
        sb.append("\n说明：以上内容仅基于您当前填写的案件资料评估信息完整程度，用于提示补充资料，不构成任何法律意见，不预测案件结果。");
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String title, RiskItem risk) {
        sb.append(title).append("（").append(levelLabel(risk.getLevel())).append("）\n");
        sb.append("   说明：").append(risk.getReason()).append("\n");
        if (!risk.getInformationNeeded().isEmpty()) {
            sb.append("   需要补充：").append(risk.getInformationNeeded()).append("\n");
        }
    }

    private static String overallSentence(RiskLevel level) {
        switch (level) {
            case HIGH:
                return "案件资料存在较多信息缺口，建议补充完善后再继续处理。";
            case MEDIUM:
                return "案件资料基本可用，部分信息建议补充完善。";
            default:
                return "根据当前案件资料，时间、证据、对方主体、地点四类信息均较为完整，暂未发现明显信息缺口。";
        }
    }

    private static String levelLabel(RiskLevel level) {
        switch (level) {
            case HIGH:
                return "信息缺失较多";
            case MEDIUM:
                return "信息不完整";
            default:
                return "信息较完整";
        }
    }

    // ---- 辅助 ----

    private static RiskItem item(RiskLevel level, String ruleCode, String reason, String informationNeeded) {
        RiskItem it = new RiskItem();
        it.setLevel(level);
        it.setRuleCode(ruleCode);
        it.setReason(reason);
        it.setInformationNeeded(informationNeeded);
        return it;
    }

    /** overallLevel = 四类取最高（HIGH > MEDIUM > LOW），无加权。 */
    private static RiskLevel maxLevel(RiskLevel a, RiskLevel b, RiskLevel c, RiskLevel d) {
        RiskLevel m = a.severity() >= b.severity() ? a : b;
        m = m.severity() >= c.severity() ? m : c;
        return m.severity() >= d.severity() ? m : d;
    }

    private static String stripQuantifier(String t) {
        for (String q : QUANTIFIER_PREFIXES) {
            if (t.startsWith(q)) {
                return t.substring(q.length());
            }
        }
        return t;
    }

    private static boolean containsAnyMarker(String t) {
        for (String marker : SPECIFIC_MARKERS) {
            if (t.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
