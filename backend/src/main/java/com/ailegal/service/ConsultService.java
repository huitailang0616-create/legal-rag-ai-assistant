package com.ailegal.service;

import com.ailegal.dto.ConsultResponse;
import com.ailegal.dto.EnhancedRetrieveTestResponse;

/**
 * Enhanced legal consult (4A): Query Understanding -> (reject or) Basic RAG -> gate
 * -> Answer LLM. Basic RAG (embedding -> Qdrant Top-10 -> MySQL JOIN -> weighted
 * Top-5 -> 0.65 gate) is reused untouched. No duplicated RAG logic; no history; no streaming.
 *
 * Reject reasons (evidenceInsufficient=true, citations always empty):
 *   NON_LEGAL_INTENT    — user question is not a legal consultation
 *   UNSUPPORTED_DOMAIN  — legal question outside the three supported domains
 *   KNOWLEDGE_GAP       — domain understood, but the current KB lacks direct evidence
 * Basic technical reasons (PASS / UNKNOWN_DOMAIN / LOW_RELEVANCE / NO_RESULT) remain
 * unchanged on the /api/admin/rag/retrieve-test API.
 */
public interface ConsultService {

    /**
     * 单轮咨询。caseAnalysisId 可选（Final-Requirement-Gaps-G）：
     * 非 null 时服务端按 userId+caseAnalysisId 读取本人已确认案件作为请求级上下文；
     * null 时行为与历史完全一致。history / runtime 始终保存用户原始 question。
     */
    ConsultResponse consult(String question, Long caseAnalysisId, Long userId, String username, String ip);

    /**
     * 4A technical acceptance API (ADMIN only): full understanding + retrieval chain
     * up to the gate, WITHOUT calling the Answer LLM.
     */
    EnhancedRetrieveTestResponse enhancedRetrieveTest(String question, Long userId, String username, String ip);
}
