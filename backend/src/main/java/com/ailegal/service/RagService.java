package com.ailegal.service;

import com.ailegal.dto.RagRetrieveTestResponse;

/**
 * RAG retrieval. One shared pipeline (RagServiceImpl.retrieveInternal); the only
 * difference between Basic and Enhanced is the first-stage Qdrant candidate limit:
 *   - retrieveTest    -> candidateLimit = 10  (3A director-accepted, locked)
 *   - retrieveEnhanced -> candidateLimit = 100 (4A-EnhancedCandidateRecall, Enhanced only)
 * Everything downstream is shared: MySQL authoritative JOIN, KeywordConceptGroups
 * scoring, domain score, 0.70/0.20/0.10 weights, Top-5, 0.65 relevance gate.
 */
public interface RagService {

    /** Basic (3A locked): Qdrant candidate limit = 10. */
    RagRetrieveTestResponse retrieveTest(String question, Long userId, String username, String ip);

    /** Enhanced (4A): Qdrant candidate limit = 100. */
    RagRetrieveTestResponse retrieveEnhanced(String question, Long userId, String username, String ip);
}