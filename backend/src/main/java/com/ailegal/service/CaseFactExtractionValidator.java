package com.ailegal.service;

import com.ailegal.dto.CaseFactExtraction;
import com.ailegal.exception.ExtractionException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 4B 固定 JSON 严格校验器（强类型 DTO + 专用 STRICT ObjectMapper，不做复杂 JSON Schema 引擎）。
 *
 * STRICT mapper 规则：
 *   - FAIL_ON_UNKNOWN_PROPERTIES：拒绝模板外字段（注入内容无法通过新增字段生效）
 *   - FAIL_ON_TRAILING_TOKENS：拒绝 JSON 尾部多余内容
 *   - ACCEPT_SINGLE_VALUE_AS_ARRAY=false：标量冒充数组直接抛异常
 *   - coercion 全关（Jackson 2.15 现代 API，CoercionAction.Fail）：数字/布尔/字符串互相冒充一律失败
 *   - markdown code fence / 前后缀文字天然解析失败，统一映射 INVALID_JSON
 *
 * 每个字段做 null/blank/长度/数量校验，任何失败抛 ExtractionException(INVALID_JSON)，
 * 无任何默认值兜底（与 QU 硬失败契约一致）。失败原子性由外层保证：校验失败绝无 UPDATE。
 */
@Component
public class CaseFactExtractionValidator {

    private static final Logger log = LoggerFactory.getLogger(CaseFactExtractionValidator.class);

    private static final int MAX_CONTENT_LENGTH = 20000;   // 粗粒度拒绝异常巨型响应
    private static final int MAX_CASE_SUMMARY = 1000;
    private static final int MAX_DISPUTE_FOCUS = 500;
    private static final int MAX_CLAIMS = 10;
    private static final int MAX_CLAIM_LENGTH = 200;
    private static final int MAX_KEY_DATES = 20;
    private static final int MAX_EVENT_LENGTH = 100;
    private static final int MAX_DATE_TEXT_LENGTH = 100;
    private static final int MAX_EVIDENCE_ITEMS = 20;
    private static final int MAX_EVIDENCE_NAME_LENGTH = 200;
    private static final int MAX_DESC_LENGTH = 500;
    private static final int MAX_MISSING_ITEMS = 10;
    private static final int MAX_MISSING_LENGTH = 200;

    private static final ObjectMapper STRICT = new ObjectMapper();

    static {
        STRICT.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        STRICT.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        STRICT.disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        STRICT.coercionConfigDefaults()
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
    }

    /**
     * 严格解析 + 校验。全部通过才返回 DTO；任何失败抛 ExtractionException(INVALID_JSON)。
     */
    public CaseFactExtraction parseAndValidate(String content) {
        if (content == null || content.length() > MAX_CONTENT_LENGTH) {
            throw invalid("智能提取失败:提取结果不是合法 JSON");
        }
        CaseFactExtraction dto;
        try {
            dto = STRICT.readValue(content, CaseFactExtraction.class);
        } catch (Exception e) {
            log.warn("EXTRACT_PARSE_FAILED notJson");
            throw invalid("智能提取失败:提取结果不是合法 JSON");
        }

        requireText(dto.getCaseSummary(), 1, MAX_CASE_SUMMARY, "caseSummary");
        requireText(dto.getDisputeFocus(), 1, MAX_DISPUTE_FOCUS, "disputeFocus");
        requireStringList(dto.getClaims(), MAX_CLAIMS, MAX_CLAIM_LENGTH, "claims");
        requireKeyDates(dto.getKeyDates());
        requireEvidenceItems(dto.getEvidenceItems());
        requireDescription(dto.getCounterparty(), "counterparty");
        requireDescription(dto.getLocation(), "location");
        requireStringList(dto.getMissingInformation(), MAX_MISSING_ITEMS, MAX_MISSING_LENGTH, "missingInformation");
        return dto;
    }

    /**
     * 将已校验 DTO 序列化为规范 JSON 字符串（by construction 合法），供写入 MySQL JSON 列。
     */
    public String toCanonicalJson(CaseFactExtraction dto) {
        try {
            return STRICT.writeValueAsString(dto);
        } catch (Exception e) {
            // DTO 由本校验器构造，序列化失败仅说明内部错误；仍按原子性失败处理
            log.warn("EXTRACT_CANONICAL_FAILED");
            throw invalid("智能提取失败:提取结果不符合要求");
        }
    }

    // ---- 校验辅助 ----

    private static void requireText(String value, int minLen, int maxLen, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field);
        }
        if (value.length() > maxLen) {
            throw invalid(field);
        }
    }

    /** 字符串数组：可为空数组；每项非空且限长。null 数组视为不符合要求（字段必须出现）。 */
    private static void requireStringList(List<String> items, int maxCount, int maxLen, String field) {
        if (items == null) {
            throw invalid(field);
        }
        if (items.size() > maxCount) {
            throw invalid(field);
        }
        for (String item : items) {
            if (item == null || item.isBlank() || item.length() > maxLen) {
                throw invalid(field);
            }
        }
    }

    private static void requireKeyDates(List<CaseFactExtraction.KeyDateItem> items) {
        if (items == null) {
            throw invalid("keyDates");
        }
        if (items.size() > MAX_KEY_DATES) {
            throw invalid("keyDates");
        }
        for (CaseFactExtraction.KeyDateItem item : items) {
            if (item == null) {
                throw invalid("keyDates");
            }
            requireText(item.getEvent(), 1, MAX_EVENT_LENGTH, "keyDates");
            requireText(item.getDateText(), 1, MAX_DATE_TEXT_LENGTH, "keyDates");
        }
    }

    private static void requireEvidenceItems(List<CaseFactExtraction.EvidenceItem> items) {
        if (items == null) {
            throw invalid("evidenceItems");
        }
        if (items.size() > MAX_EVIDENCE_ITEMS) {
            throw invalid("evidenceItems");
        }
        for (CaseFactExtraction.EvidenceItem item : items) {
            if (item == null) {
                throw invalid("evidenceItems");
            }
            requireText(item.getName(), 1, MAX_EVIDENCE_NAME_LENGTH, "evidenceItems");
            // description 可空：null 归一化为空串（可选描述字段），不补全任何内容
            if (item.getDescription() == null) {
                item.setDescription("");
            } else if (item.getDescription().length() > MAX_DESC_LENGTH) {
                throw invalid("evidenceItems");
            }
        }
    }

    /** counterparty / location：结构必出现；description 可空（归一化空串），禁止补全 */
    private static void requireDescription(CaseFactExtraction.DescriptionItem item, String field) {
        if (item == null) {
            throw invalid(field);
        }
        if (item.getDescription() == null) {
            item.setDescription("");
        } else if (item.getDescription().length() > MAX_DESC_LENGTH) {
            throw invalid(field);
        }
    }

    private static ExtractionException invalid(String field) {
        // 只记字段名（定位问题），绝不记字段内容/prompt/raw 响应
        log.warn("EXTRACT_VALIDATE_FAILED field={}", field);
        return new ExtractionException(ExtractionException.INVALID_JSON, "智能提取失败:提取结果不符合要求");
    }
}
