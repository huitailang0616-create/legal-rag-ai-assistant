package com.ailegal.service.impl;

import com.ailegal.service.DocumentTemplateService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 5B 三类固定文书模板（Java 字符串组装，单一事实来源，无模板引擎/无 DB）。
 *
 * 占位符统一为 [请填写……]：与用户/LLM 内容视觉可区分，缺失信息永不猜测。
 * 模板确定性保证：LLM 无法控制文书标题 / 申请人原告字段 / 被申请人被告字段 /
 * 法院仲裁委占位 / 日期占位 / 固定后续处理文本——LLM 输出只能进入
 * factsDraft / reasoningDraft 两个位置。
 */
@Service
public class DocumentTemplateServiceImpl implements DocumentTemplateService {

    /** documentType → 允许的 domain 集合（5B 正式锁定，见接口注释） */
    private static final Map<String, Set<String>> COMPATIBLE_DOMAINS = Map.of(
            "LABOR_ARBITRATION_APPLICATION", Set.of("LABOR_DISPUTE"),
            "CIVIL_COMPLAINT", Set.of("HOUSE_RENTAL", "CONSUMER_RIGHTS"),
            "DEMAND_LETTER", Set.of("LABOR_DISPUTE", "HOUSE_RENTAL", "CONSUMER_RIGHTS"));

    @Override
    public boolean isCompatible(String documentType, String domain) {
        Set<String> domains = COMPATIBLE_DOMAINS.get(documentType);
        return domains != null && domains.contains(domain);
    }

    @Override
    public String render(String documentType, TemplateInput input) {
        if (input == null) {
            throw new IllegalArgumentException("模板输入不能为空");
        }
        return switch (documentType) {
            case "LABOR_ARBITRATION_APPLICATION" -> renderLaborArbitration(input);
            case "CIVIL_COMPLAINT" -> renderCivilComplaint(input);
            case "DEMAND_LETTER" -> renderDemandLetter(input);
            default -> throw new IllegalArgumentException("未知文书类型: " + documentType);
        };
    }

    // ---- 劳动仲裁申请书 ----

    private static String renderLaborArbitration(TemplateInput in) {
        return "劳动仲裁申请书\n"
                + "\n申请人：\n[请填写申请人姓名及必要身份信息]\n"
                + "\n被申请人：\n" + textOrPlaceholder(in.counterpartyDescription(), "[请填写被申请人信息]") + "\n"
                + "\n仲裁请求：\n" + textOrPlaceholder(in.claims(), "[请填写具体仲裁请求]") + "\n"
                + "\n事实与理由：\n" + in.factsDraft() + "\n\n" + in.reasoningDraft() + "\n"
                + "\n此致\n[请填写有管辖权的劳动人事争议仲裁委员会]\n"
                + "\n申请人：\n[请填写]\n"
                + "\n日期：\n[请填写]";
    }

    // ---- 民事起诉状 ----

    private static String renderCivilComplaint(TemplateInput in) {
        return "民事起诉状\n"
                + "\n原告：\n[请填写原告姓名及必要身份信息]\n"
                + "\n被告：\n" + textOrPlaceholder(in.counterpartyDescription(), "[请填写被告信息]") + "\n"
                + "\n诉讼请求：\n" + textOrPlaceholder(in.claims(), "[请填写具体诉讼请求]") + "\n"
                + "\n事实与理由：\n" + in.factsDraft() + "\n\n" + in.reasoningDraft() + "\n"
                + "\n此致\n[请填写有管辖权的人民法院]\n"
                + "\n具状人：\n[请填写]\n"
                + "\n日期：\n[请填写]";
    }

    // ---- 催告函 ----

    private static String renderDemandLetter(TemplateInput in) {
        return "催告函\n"
                + "\n致：\n" + textOrPlaceholder(in.counterpartyDescription(), "[请填写收函方信息]") + "\n"
                + "\n事项：\n关于「" + textOrPlaceholder(in.caseTitle(), "本案") + "」的催告\n"
                + "\n事实说明：\n" + in.factsDraft() + "\n"
                + "\n催告事项：\n" + textOrPlaceholder(in.claims(), "[请填写具体催告事项]") + "\n"
                + "\n理由说明：\n" + in.reasoningDraft() + "\n"
                + "\n履行期限：\n[请根据实际情况填写]\n"
                + "\n后续处理：\n如相关事项仍未妥善处理，本人将根据实际情况依法选择适当的争议解决途径。\n"
                + "\n落款：\n[请填写]\n"
                + "\n日期：\n[请填写]";
    }

    /** 有内容用原始文本，否则占位符（缺失信息永不猜测） */
    private static String textOrPlaceholder(String value, String placeholder) {
        return (value == null || value.isBlank()) ? placeholder : value;
    }
}
