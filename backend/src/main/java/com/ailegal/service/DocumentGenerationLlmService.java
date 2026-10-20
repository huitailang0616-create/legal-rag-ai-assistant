package com.ailegal.service;

/**
 * 5B 文书生成 LLM 调用服务（独立于模块3/4：不接触 RagService / Qdrant / Embedding /
 * QueryUnderstanding / ConsultService / CaseExtractionLlmService / ZhipuLlmService）。
 * 独立 document-generation.* 配置块、自有 RestClient。
 *
 * LLM 唯一职责：根据关联案件原始字段草拟 factsDraft / reasoningDraft 两段文字，
 * 绝不生成整份文书；整份文书由 Java 固定模板组装。
 */
public interface DocumentGenerationLlmService {

    /**
     * 草拟文书"事实与理由"两字段（response_format=json_object）。
     * 返回原始 content 字符串（尚未校验，校验由 DocumentGenerationValidator 承担）。
     * 失败一律抛 DocumentGenerationException（RATE_LIMIT / PROVIDER_ERROR / TIMEOUT /
     * EMPTY_RESPONSE / INVALID_JSON）。
     */
    String generate(String userPrompt);
}
