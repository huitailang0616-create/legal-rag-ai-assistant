package com.ailegal.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 5B + Gaps-F DOCX 导出构建器：读取预设 .docx 模板（resources/templates/docx/），
 * 由 DocumentDraftDocxTemplateEngine 做段落级占位符替换后输出字节流，全程不写服务器磁盘。
 *
 * documentType → 模板文件映射（三份正式模板，模板内容 = 固定结构 + 占位符，非 Java 拼装）：
 *   LABOR_ARBITRATION_APPLICATION → labor_arbitration_application.docx
 *   CIVIL_COMPLAINT              → civil_complaint.docx
 *   DEMAND_LETTER                 → demand_letter.docx
 *
 * 占位符只使用系统真实拥有的数据（任务书禁止编造）：
 *   {{TITLE}}        = 文书标题（document_draft.title）
 *   {{CONTENT}}      = 已保存正文（document_draft.content，唯一事实来源，不重新调 LLM）
 *   {{GENERATED_AT}} = 导出日期（当前系统日期，真实数据）
 *
 * 不新增任何模板 DSL / 页眉动态规则 / 电子签章 / 模板上传。
 */
@Component
public class DocumentDraftDocxBuilder {

    private static final String TEMPLATE_DIR = "templates/docx/";

    private static final Map<String, String> TEMPLATE_BY_TYPE = Map.of(
            "LABOR_ARBITRATION_APPLICATION", "labor_arbitration_application.docx",
            "CIVIL_COMPLAINT", "civil_complaint.docx",
            "DEMAND_LETTER", "demand_letter.docx");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private final DocumentDraftDocxTemplateEngine engine;

    public DocumentDraftDocxBuilder(DocumentDraftDocxTemplateEngine engine) {
        this.engine = engine;
    }

    /**
     * 按文书类型加载预设模板并替换占位符（{{TITLE}} / {{CONTENT}} / {{GENERATED_AT}}）。
     * 未知类型抛 IllegalStateException；模板缺失/占位符残留由引擎抛错，绝不输出半成品。
     */
    public byte[] build(String documentType, String title, String content) {
        String template = TEMPLATE_BY_TYPE.get(documentType);
        if (template == null) {
            throw new IllegalStateException("未知文书类型: " + documentType);
        }
        Map<String, String> values = new HashMap<>();
        values.put("{{TITLE}}", title == null ? "" : title);
        values.put("{{CONTENT}}", content == null ? "" : content);
        values.put("{{GENERATED_AT}}", LocalDate.now().format(DATE_FMT));
        return engine.render(TEMPLATE_DIR + template, values);
    }
}
