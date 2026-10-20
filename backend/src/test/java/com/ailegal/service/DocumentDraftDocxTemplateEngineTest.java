package com.ailegal.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Final-Requirement-Gaps-F 自动化测试：DOCX 模板及占位符（Module 5）。
 *
 * 1) 三种 documentType 均能：加载对应 classpath 预设模板 → 段落级替换占位符 → 生成非空 DOCX，
 *    无占位符残留；且输出含模板特有附注（证明内容来自预设模板而非动态拼装）。
 * 2) Builder 三类型映射 + 未知类型防御。
 * 3) placeholder 被 Word 拆成多个 XWPFRun（split-run）仍能成功替换（含表格单元格段落）。
 * 4) 未知占位符 → IllegalStateException（不输出半成品）。
 * 测试专用 split-run DOCX 在测试内构造，不污染正式模板。
 */
class DocumentDraftDocxTemplateEngineTest {

    private static final Map<String, String> TEMPLATE_RESOURCES = Map.of(
            "LABOR_ARBITRATION_APPLICATION", "templates/docx/labor_arbitration_application.docx",
            "CIVIL_COMPLAINT", "templates/docx/civil_complaint.docx",
            "DEMAND_LETTER", "templates/docx/demand_letter.docx");

    /** 模板特有附注（只存在于模板、不存在于替换值），证明输出来自预设模板 */
    private static final String NOTE_MARK = "本系统根据用户确认的案件信息及人工智能生成内容辅助整理";

    private final DocumentDraftDocxTemplateEngine engine = new DocumentDraftDocxTemplateEngine();

    private static Map<String, String> values(String title, String content, String date) {
        Map<String, String> v = new HashMap<>();
        v.put("{{TITLE}}", title);
        v.put("{{CONTENT}}", content);
        v.put("{{GENERATED_AT}}", date);
        return v;
    }

    @Test
    @DisplayName("1: 三种 documentType 均加载预设模板替换占位符，输出非空 DOCX、无残留、内容来自模板")
    void threeTemplatesRenderWithNoLeftover() throws Exception {
        for (Map.Entry<String, String> e : TEMPLATE_RESOURCES.entrySet()) {
            byte[] out = engine.render(e.getValue(),
                    values("劳动仲裁申请书", "事实与理由正文内容\n第二行内容", "2026年8月15日"));
            assertTrue(out.length > 0, e.getKey() + " 输出非空");
            assertEquals('P', out[0], e.getKey() + " OOXML zip 魔数");
            assertEquals('K', out[1], e.getKey() + " OOXML zip 魔数");
            String text = allText(new XWPFDocument(new ByteArrayInputStream(out)));
            assertTrue(text.contains("劳动仲裁申请书"), e.getKey() + " {{TITLE}} 已替换");
            assertTrue(text.contains("事实与理由正文内容\n第二行内容"), e.getKey() + " {{CONTENT}} 多行已替换");
            assertTrue(text.contains("2026年8月15日"), e.getKey() + " {{GENERATED_AT}} 已替换");
            assertTrue(text.contains(NOTE_MARK), e.getKey() + " 输出含模板特有附注（内容来自预设模板）");
            assertFalse(text.contains("{{"), e.getKey() + " 无占位符残留");
        }
    }

    @Test
    @DisplayName("2: Builder 三种类型映射 + 未知类型防御")
    void builderMapsThreeTypesAndRejectsUnknown() throws Exception {
        DocumentDraftDocxBuilder builder = new DocumentDraftDocxBuilder(engine);
        for (String type : TEMPLATE_RESOURCES.keySet()) {
            byte[] out = builder.build(type, "民事起诉状", "正文内容行一\n正文内容行二");
            assertTrue(out.length > 0, type + " 输出非空");
            assertEquals('P', out[0]);
            assertEquals('K', out[1]);
            String text = allText(new XWPFDocument(new ByteArrayInputStream(out)));
            assertTrue(text.contains("民事起诉状"));
            assertTrue(text.contains("正文内容行一\n正文内容行二"));
            assertTrue(text.contains("导出时间：")); // {{GENERATED_AT}} 为真实当前日期
            assertFalse(text.contains("{{"), type + " 无占位符残留");
        }
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> builder.build("UNKNOWN_TYPE", "t", "c"));
        assertTrue(ex.getMessage().contains("UNKNOWN_TYPE"));
    }

    @Test
    @DisplayName("3: placeholder 被拆成多个 run（split-run）仍能成功替换，含表格单元格段落")
    void splitRunPlaceholderIsReplaced() throws Exception {
        // ---- 测试专用小 DOCX：{{TITLE}} 拆 2 个 run，{{CONTENT}} 拆 2 个 run，表格单元格 1 个 run ----
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r1 = p.createRun();
        r1.setText("标题：{{TI");
        XWPFRun r2 = p.createRun();
        r2.setText("TLE}}");
        XWPFRun r3 = p.createRun();
        r3.setText("，正文：{{CON");
        XWPFRun r4 = p.createRun();
        r4.setText("TENT}}结束");
        doc.createTable(1, 1);
        doc.getTables().get(0).getRows().get(0).getTableCells().get(0)
                .getParagraphs().get(0).createRun().setText("表格内：{{TITLE}}");
        byte[] templateBytes = toBytes(doc);

        byte[] out = engine.render(new ByteArrayInputStream(templateBytes),
                values("测试标题", "正文X", "2026年1月1日"));
        XWPFDocument result = new XWPFDocument(new ByteArrayInputStream(out));
        // 段落级合并替换后的完整文本
        assertEquals("标题：测试标题，正文：正文X结束", result.getParagraphs().get(0).getText().trim());
        // 该段已被合并为单个 run（段落级回写，而非 run 级查找）
        assertEquals(1, result.getParagraphs().get(0).getRuns().size());
        // 表格单元格内占位符同样被替换
        assertEquals("表格内：测试标题",
                result.getTables().get(0).getRows().get(0).getTableCells().get(0).getText().trim());
    }

    @Test
    @DisplayName("4: 未知占位符 → IllegalStateException（不输出半成品）")
    void leftoverPlaceholderThrows() throws Exception {
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph p = doc.createParagraph();
        p.createRun().setText("内容 {{UNKNOWN}} 结尾");
        byte[] templateBytes = toBytes(doc);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> engine.render(new ByteArrayInputStream(templateBytes),
                        values("t", "c", "d")));
        assertTrue(ex.getMessage().contains("{{UNKNOWN}}"), ex.getMessage());
    }

    // ---- helpers ----

    private static byte[] toBytes(XWPFDocument doc) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        return out.toByteArray();
    }

    /** 收集普通段落 + 表格单元格段落全部文本 */
    private static String allText(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph p : doc.getParagraphs()) {
            sb.append(p.getText()).append('\n');
        }
        for (var table : doc.getTables()) {
            for (var row : table.getRows()) {
                for (var cell : row.getTableCells()) {
                    for (XWPFParagraph p : cell.getParagraphs()) {
                        sb.append(p.getText()).append('\n');
                    }
                }
            }
        }
        return sb.toString();
    }
}
