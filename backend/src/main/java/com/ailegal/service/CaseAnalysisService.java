package com.ailegal.service;

import com.ailegal.dto.CaseAnalysisCreateRequest;
import com.ailegal.dto.CaseAnalysisResponse;
import com.ailegal.dto.CaseAnalysisUpdateRequest;
import com.ailegal.dto.CaseExtractionUpdateRequest;

import java.util.List;

public interface CaseAnalysisService {

    CaseAnalysisResponse create(CaseAnalysisCreateRequest request, Long userId, String username, String ip);

    List<CaseAnalysisResponse> listByUser(Long userId);

    CaseAnalysisResponse get(Long id, Long userId);

    CaseAnalysisResponse update(Long id, CaseAnalysisUpdateRequest request, Long userId, String username, String ip);

    void delete(Long id, Long userId, String username, String ip);

    /**
     * 4B：对已保存案件做智能案情提取（事实整理/结构化提取，非风险分析）。
     * 事实只从 DB 记录读取；LLM 调用、严格校验全部成功后才会更新 extraction_json（失败原子性）。
     */
    CaseAnalysisResponse extract(Long id, Long userId, String username, String ip);

    /**
     * 4C：基于已保存的 extraction_json 用确定性 Java 规则计算案件风险（无 LLM/RAG）。
     * 全部计算在内存完成，成功后才一次性写入 risk_result_json + analysis_result + ANALYZED。
     */
    CaseAnalysisResponse riskAnalysis(Long id, Long userId, String username, String ip);

    /**
     * 4B→4C 人工确认保存：请求仅含 extraction（人工可编辑字段），
     * 人工编辑经同一严格校验契约（往返 STRICT 校验 + trim）后覆盖 extraction_json 并置
     * extraction_confirmed=1；同时作废旧 risk_result_json / analysis_result 回 DRAFT
     * （确认后的新版本才是后续风险分析的输入，避免旧风险与人工版事实不一致）。
     */
    CaseAnalysisResponse confirmExtraction(Long id, CaseExtractionUpdateRequest request,
                                           Long userId, String username, String ip);

    /**
     * 4C：保存用户编辑的分析说明。仅更新 analysis_result + updated_at，
     * 只允许在 ANALYZED 状态下编辑；risk_result_json / analysis_status 等不可由客户端修改。
     */
    CaseAnalysisResponse updateAnalysisResult(Long id, String analysisResult, Long userId, String username, String ip);
}
