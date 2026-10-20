package com.ailegal.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gaps-F DOCX 模板占位符替换引擎（Apache POI 处理 DOCX 模板及占位符）。
 *
 * 读取预设 .docx 模板（classpath resources/templates/docx/），段落级替换占位符后输出 DOCX。
 *
 * 【split-run 安全】Word 可能把 {{PLACEHOLDER}} 拆进多个 XWPFRun：
 * 本实现绝不做 run 级字符串查找，而是对每个段落先取段落级完整文本
 * p.getText()（POI 拼接该段全部 run），含占位符才处理：清空全部 run →
 * 写回单个 run（保留段落首 run 的格式 RPr 副本；多行值用 addBreak 换行）。
 * 表格单元格内的段落与普通段落同一处理路径（模板实际未用表格，防御覆盖）。
 *
 * 【不输出半成品】替换完成后全文档扫描占位符形残留（\{\{[^}]*\}\}）：
 * 存在任何未替换占位符 → IllegalStateException，绝不导出残片。
 * 注意：正文/标题本身若含 "{{...}}" 字样也会触发该防御（刻意为之）。
 */
@Component
public class DocumentDraftDocxTemplateEngine {

    private static final String CHINESE_FONT = "宋体";

    /** 占位符形残留检测：{{...}}（不允许中途含 }） */
    private static final Pattern LEFTOVER = Pattern.compile("\\{\\{[^}]*\\}\\}");

    /** 从 classpath 加载模板并替换占位符。placeholders 的 key 需含完整花括号（如 {{TITLE}}） */
    public byte[] render(String classpathResource, Map<String, String> placeholders) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("DOCX 模板不存在: " + classpathResource);
            }
            return render(in, placeholders);
        } catch (IOException e) {
            throw new UncheckedIOException("DOCX 模板读取失败: " + classpathResource, e);
        }
    }

    /** 从 InputStream 读取模板并替换占位符（测试可直接喂字节流构造的流） */
    public byte[] render(InputStream template, Map<String, String> placeholders) {
        try (XWPFDocument doc = new XWPFDocument(template)) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                replaceInParagraph(p, placeholders);
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph p : cell.getParagraphs()) {
                            replaceInParagraph(p, placeholders);
                        }
                    }
                }
            }
            assertNoLeftover(doc);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("DOCX 模板渲染失败", e);
        }
    }

    /**
     * 段落级替换：整段文本含占位符才处理。
     * 保留首 run 的 RPr（格式）副本；清空 run 后写回单 run；多行值 addBreak 换行。
     */
    private static void replaceInParagraph(XWPFParagraph p, Map<String, String> placeholders) {
        String full = p.getText();
        if (full == null || !full.contains("{{") || p.getRuns().isEmpty()) {
            return;
        }
        String replaced = full;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            replaced = replaced.replace(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        // 记录首 run 格式（copy 深拷贝，避免 XMLBeans 父子归属冲突）
        CTRPr format = p.getRuns().get(0).getCTR().isSetRPr()
                ? (CTRPr) p.getRuns().get(0).getCTR().getRPr().copy() : null;
        while (!p.getRuns().isEmpty()) {
            p.removeRun(0);
        }
        XWPFRun r = p.createRun();
        if (format != null) {
            r.getCTR().setRPr(format);
        }
        ensureChineseFont(r);
        // 多行值：setText 默认追加位置 → 首行 setText 后逐行 addBreak + setText
        String[] lines = replaced.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        r.setText(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            r.addBreak();
            r.setText(lines[i]);
        }
    }

    /** 防御：全文档（普通段落 + 表格单元格段落）无占位符残留，残留即抛错不输出半成品 */
    private static void assertNoLeftover(XWPFDocument doc) {
        StringBuilder leftover = new StringBuilder();
        for (XWPFParagraph p : doc.getParagraphs()) {
            collectLeftover(p.getText(), leftover);
        }
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : cell.getParagraphs()) {
                        collectLeftover(p.getText(), leftover);
                    }
                }
            }
        }
        if (leftover.length() > 0) {
            throw new IllegalStateException("DOCX 模板存在未替换的占位符: " + leftover);
        }
    }

    private static void collectLeftover(String text, StringBuilder sb) {
        if (text == null) {
            return;
        }
        Matcher m = LEFTOVER.matcher(text);
        while (m.find()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(m.group());
        }
    }

    /** 无显式格式时兜底宋体（ascii + eastAsia 双设置） */
    private static void ensureChineseFont(XWPFRun run) {
        if (!run.getCTR().isSetRPr() || run.getCTR().getRPr().getRFontsArray().length == 0) {
            run.setFontFamily(CHINESE_FONT);
        }
        CTRPr rpr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts[] fonts = rpr.getRFontsArray();
        CTFonts f = fonts.length > 0 ? fonts[0] : rpr.addNewRFonts();
        if (f.isSetEastAsia()) {
            return;
        }
        f.setEastAsia(CHINESE_FONT);
    }
}
