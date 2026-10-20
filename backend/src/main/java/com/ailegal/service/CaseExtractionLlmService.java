package com.ailegal.service;

/**
 * 4B 案件事实提取 LLM 调用服务。
 * 与运行期 LLM / 查询理解互不接触：独立 case-extraction.* 配置块、自有 RestClient。
 */
public interface CaseExtractionLlmService {

    /**
     * 对案件事实资料做结构化提取（response_format=json_object）。
     * 返回原始 content 字符串（尚未校验，校验由 CaseFactExtractionValidator 承担）。
     * 失败一律抛 ExtractionException（RATE_LIMIT / PROVIDER_ERROR / TIMEOUT / EMPTY_RESPONSE）。
     */
    String extract(String userPrompt);
}
