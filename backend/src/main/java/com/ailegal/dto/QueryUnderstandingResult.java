package com.ailegal.dto;

import java.util.List;
import java.util.Set;

/**
 * Structured output of the Query Understanding module (4A).
 *
 * Fields are exactly what the task spec allows — nothing more:
 *   legalIntent     : whether the user is actually asking a legal question
 *   supportedDomain : LABOR_DISPUTE / HOUSE_RENTAL / CONSUMER_RIGHTS / UNKNOWN
 *   rewrittenQuery  : neutral legal phrasing for semantic retrieval (only when legalIntent=true)
 *   keyFacts        : facts the user explicitly stated (only when legalIntent=true)
 *
 * No confidence / debug fields — keep the undergraduate project simple.
 */
public class QueryUnderstandingResult {

    public static final String LABOR_DISPUTE = "LABOR_DISPUTE";
    public static final String HOUSE_RENTAL = "HOUSE_RENTAL";
    public static final String CONSUMER_RIGHTS = "CONSUMER_RIGHTS";
    public static final String UNKNOWN = "UNKNOWN";

    public static final Set<String> ALLOWED_DOMAINS = Set.of(
            LABOR_DISPUTE, HOUSE_RENTAL, CONSUMER_RIGHTS, UNKNOWN);

    private boolean legalIntent;
    private String supportedDomain;
    private String rewrittenQuery;
    private List<String> keyFacts;

    public QueryUnderstandingResult() {
    }

    public QueryUnderstandingResult(boolean legalIntent, String supportedDomain,
                                    String rewrittenQuery, List<String> keyFacts) {
        this.legalIntent = legalIntent;
        this.supportedDomain = supportedDomain;
        this.rewrittenQuery = rewrittenQuery;
        this.keyFacts = keyFacts == null ? List.of() : keyFacts;
    }

    public boolean isLegalIntent() {
        return legalIntent;
    }

    public void setLegalIntent(boolean legalIntent) {
        this.legalIntent = legalIntent;
    }

    public String getSupportedDomain() {
        return supportedDomain;
    }

    public void setSupportedDomain(String supportedDomain) {
        this.supportedDomain = supportedDomain;
    }

    public String getRewrittenQuery() {
        return rewrittenQuery;
    }

    public void setRewrittenQuery(String rewrittenQuery) {
        this.rewrittenQuery = rewrittenQuery;
    }

    public List<String> getKeyFacts() {
        return keyFacts;
    }

    public void setKeyFacts(List<String> keyFacts) {
        this.keyFacts = keyFacts;
    }
}
