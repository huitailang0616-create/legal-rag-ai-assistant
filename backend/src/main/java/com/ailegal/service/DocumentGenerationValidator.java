package com.ailegal.service;

import com.ailegal.dto.DocumentGenerationDraft;
import com.ailegal.exception.DocumentGenerationException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 5B 文书生成固定 JSON 严格校验器（强类型 DTO + 专用 STRICT ObjectMapper，不做 JSON Schema 引擎）。
 *
 * STRICT mapper 规则与 4B CaseFactExtractionValidator 同构：
 *   - FAIL_ON_UNKNOWN_PROPERTIES：拒绝模板外字段（LLM 无法通过新增字段注入内容）
 *   - FAIL_ON_TRAILING_TOKENS：拒绝 JSON 尾部多余内容
 *   - ACCEPT_SINGLE_VALUE_AS_ARRAY=false：标量冒充数组直接抛异常
 *   - coercion 全关（CoercionAction.Fail）：数字/布尔/字符串互相冒充一律失败
 *   - markdown code fence / 前后缀文字天然解析失败，统一映射 INVALID_JSON
 *
 * 根节点必须是 object，且只允许 factsDraft / reasoningDraft 两个字段，均必存在：
 * 两字段各做 null/blank/长度校验（factsDraft<=6000 / reasoningDraft<=4000 字符），
 * 无任何默认值兜底。失败原子性由外层保证：校验失败绝无 UPDATE。
 */
@Component
public class DocumentGenerationValidator {

    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationValidator.class);

    /** 粗粒度拒绝异常巨型响应（含围栏/前后缀） */
    private static final int MAX_CONTENT_LENGTH = 20000;
    private static final int MAX_FACTS_DRAFT = 6000;
    private static final int MAX_REASONING_DRAFT = 4000;

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
     * 严格解析 + 校验。全部通过才返回 DTO；任何失败抛 DocumentGenerationException(INVALID_JSON)。
     */
    public DocumentGenerationDraft parseAndValidate(String content) {
        if (content == null || content.length() > MAX_CONTENT_LENGTH) {
            throw invalid("notJson");
        }
        DocumentGenerationDraft dto;
        try {
            dto = STRICT.readValue(content, DocumentGenerationDraft.class);
        } catch (Exception e) {
            log.warn("DOC_GEN_PARSE_FAILED notJson");
            throw invalid("notJson");
        }

        requireText(dto.getFactsDraft(), MAX_FACTS_DRAFT, "factsDraft");
        requireText(dto.getReasoningDraft(), MAX_REASONING_DRAFT, "reasoningDraft");
        return dto;
    }

    private static void requireText(String value, int maxLen, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field);
        }
        if (value.length() > maxLen) {
            throw invalid(field);
        }
    }

    private static DocumentGenerationException invalid(String field) {
        // 只记字段名（定位问题），绝不记字段内容/prompt/raw 响应
        log.warn("DOC_GEN_VALIDATE_FAILED field={}", field);
        return new DocumentGenerationException(DocumentGenerationException.INVALID_JSON,
                "文书生成失败:生成结果不符合要求");
    }
}
