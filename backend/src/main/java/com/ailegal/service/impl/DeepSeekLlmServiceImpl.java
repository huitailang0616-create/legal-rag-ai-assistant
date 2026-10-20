package com.ailegal.service.impl;

import com.ailegal.exception.BusinessException;
import com.ailegal.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek V4 Pro (deepseek-v4-pro) via the official REST API (OpenAI-compatible
 * /chat/completions). Non-streaming, locked parameters: temperature=0.2,
 * max_tokens=2048, timeout=60s, thinking explicitly disabled. No third-party SDK —
 * same RestClient pattern as EmbeddingServiceImpl.
 *
 * Every failure mode is a BusinessException -> HTTP 500. No fake answers, no canned
 * legal fallback, no silent model switch (Demo-Reliability-1: Zhipu -> DeepSeek).
 * Prompt content is never logged.
 */
@Service
public class DeepSeekLlmServiceImpl implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmServiceImpl.class);

    private final RestClient restClient;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final String thinking;

    public DeepSeekLlmServiceImpl(@Value("${llm.base-url}") String baseUrl,
                               @Value("${llm.api-key}") String apiKey,
                               @Value("${llm.model}") String model,
                               @Value("${llm.temperature}") double temperature,
                               @Value("${llm.max-tokens}") int maxTokens,
                               @Value("${llm.timeout-seconds}") int timeoutSeconds,
                               @Value("${llm.thinking}") String thinking) {
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.thinking = thinking;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        factory.setConnectTimeout((int) Duration.ofSeconds(Math.min(timeoutSeconds, 15)).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String chat(String systemPrompt, String userPrompt) {
        log.info("LLM_CALL_START model={}", model);
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", temperature,
                    "max_tokens", maxTokens,
                    "thinking", Map.of("type", thinking)
            );

            Map<String, Object> response;
            try {
                response = restClient.post()
                        .uri("/chat/completions")
                        .body(requestBody)
                        .retrieve()
                        .body(Map.class);
            } catch (RestClientResponseException e) {
                log.error("LLM_CALL_FAILED httpStatus={} model={}", e.getStatusCode(), model);
                // 429 = 模型限流（分钟级瞬时）：消息带 (429) 供上层精确重试。
                if (e.getStatusCode().value() == 429) {
                    throw new BusinessException(500, "咨询生成失败:LLM 服务繁忙(429)");
                }
                throw new BusinessException(500, "咨询生成失败:LLM 服务返回错误状态");
            } catch (ResourceAccessException e) {
                // read/connect timeout: 高峰期慢响应，瞬时错误，可重试。
                log.error("LLM_CALL_TIMEOUT model={} type={}", model, e.getClass().getSimpleName());
                throw new BusinessException(500, "咨询生成失败:LLM 服务调用超时");
            }

            if (response == null) {
                throw new BusinessException(500, "咨询生成失败:LLM 响应为空");
            }

            Object choicesObj = response.get("choices");
            if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty()) {
                throw new BusinessException(500, "咨询生成失败:LLM 返回 choices 为空");
            }

            Map<String, Object> firstChoice = (Map<String, Object>) ((List<?>) choicesObj).get(0);
            Object messageObj = firstChoice == null ? null : firstChoice.get("message");
            if (!(messageObj instanceof Map)) {
                throw new BusinessException(500, "咨询生成失败:LLM 返回 message 为空");
            }
            Object contentObj = ((Map<?, ?>) messageObj).get("content");
            if (!(contentObj instanceof String) || ((String) contentObj).isBlank()) {
                throw new BusinessException(500, "咨询生成失败:LLM 返回 content 为空");
            }

            String content = (String) contentObj;
            log.info("LLM_CALL_END model={} ms={} chars={}", model, System.currentTimeMillis() - start, content.length());
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM_CALL_FAILED model={} type={}", model, e.getClass().getSimpleName());
            throw new BusinessException(500, "咨询生成失败:LLM 调用异常");
        }
    }
}
