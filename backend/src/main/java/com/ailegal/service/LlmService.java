package com.ailegal.service;

/**
 * Runtime LLM chat service (3B-1): DeepSeek V4 Pro (deepseek-v4-pro) via official
 * OpenAI-compatible REST API. Non-streaming, single round. The only contract is:
 * given a system prompt and a user prompt, return the final message content.
 *
 * Failure semantics: every failure (non-2xx, empty body, empty choices, empty
 * content, timeout, JSON parse error) must throw BusinessException -> HTTP 500.
 * Never return a fake answer or fall back to a canned legal response.
 */
public interface LlmService {

    /**
     * @param systemPrompt system-level instruction (grounding rules)
     * @param userPrompt   the evidence block + user question
     * @return the final assistant message content
     */
    String chat(String systemPrompt, String userPrompt);
}
