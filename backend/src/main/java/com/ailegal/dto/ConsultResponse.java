package com.ailegal.dto;

import java.util.List;

/**
 * Response of POST /api/consult (3B-1).
 * If relevancePassed=false (gate reject): evidenceInsufficient=true, fixed safe answer,
 * citations MUST be empty — low-relevance hits are never shown as evidence.
 */
public class ConsultResponse {

    private String answer;
    private String detectedDomain;
    private boolean evidenceInsufficient;
    private String relevanceReason;
    private List<ConsultCitation> citations;

    public ConsultResponse() {
    }

    public ConsultResponse(String answer, String detectedDomain, boolean evidenceInsufficient,
                           String relevanceReason, List<ConsultCitation> citations) {
        this.answer = answer;
        this.detectedDomain = detectedDomain;
        this.evidenceInsufficient = evidenceInsufficient;
        this.relevanceReason = relevanceReason;
        this.citations = citations;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getDetectedDomain() {
        return detectedDomain;
    }

    public void setDetectedDomain(String detectedDomain) {
        this.detectedDomain = detectedDomain;
    }

    public boolean isEvidenceInsufficient() {
        return evidenceInsufficient;
    }

    public void setEvidenceInsufficient(boolean evidenceInsufficient) {
        this.evidenceInsufficient = evidenceInsufficient;
    }

    public String getRelevanceReason() {
        return relevanceReason;
    }

    public void setRelevanceReason(String relevanceReason) {
        this.relevanceReason = relevanceReason;
    }

    public List<ConsultCitation> getCitations() {
        return citations;
    }

    public void setCitations(List<ConsultCitation> citations) {
        this.citations = citations;
    }
}
