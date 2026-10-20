package com.ailegal.service;

import com.ailegal.dto.QueryUnderstandingResult;

/**
 * Query Understanding module (4A) — sits BEFORE Basic RAG in the consult pipeline.
 *
 * It only understands: originalQuestion -> {legalIntent, supportedDomain,
 * rewrittenQuery, keyFacts}. It NEVER answers legal questions and NEVER contributes
 * its own legal knowledge to the final answer.
 *
 * Failure semantics (spec section 9): empty body / empty choices / empty content /
 * non-JSON / missing legalIntent / invalid supportedDomain / empty rewrittenQuery /
 * null keyFacts ALL fail as BusinessException -> HTTP 500. There is NO silent
 * fallback to the originalQuestion, and no fabricated default JSON.
 */
public interface QueryUnderstandingService {

    /**
     * @param originalQuestion user's natural-language question (never rewritten here)
     * @return structured understanding; throws BusinessException(500) on any malformed response
     */
    QueryUnderstandingResult understand(String originalQuestion);
}
