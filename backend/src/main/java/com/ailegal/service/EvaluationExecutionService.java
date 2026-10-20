package com.ailegal.service;

import com.ailegal.dto.EvaluationResultView;
import com.ailegal.dto.EvaluationRunDetail;
import com.ailegal.dto.ExecuteNextResponse;
import com.ailegal.dto.RunSummaryResponse;
import com.ailegal.dto.ScoreRequest;
import com.ailegal.entity.EvaluationRun;

import java.util.List;

/**
 * 模块6B 评测执行与统计。三种方案在同一批内各自执行全部30题：
 *   DIRECT_LLM   直接问答（无检索）
 *   BASIC_RAG    原问题 -> Basic RAG(limit=10) -> gate -> grounded answer
 *   ENHANCED_RAG QU理解 -> canonical+rewritten -> Enhanced RAG(limit=100) -> gate -> grounded answer
 *
 * 冻结约定（6A 验收锁定 + Gaps-E 修正为参考法条口径）：
 *   - expected_evidence_keywords 仅为辅助关键词（历史兼容），绝不进入任何 RAG query / rerank / gate / prompt，
 *     也不参与正式命中判定。
 *   - reference_articles_json（人工标注参考法条，documentName+articleNumber 稳定标识）为正式命中判定依据；
 *     run 级 metric_version 决定口径：
 *       REFERENCE_ARTICLE_TOP5_V2（新 run）：人工标注参考法条解析为当前有效 legal_article.id，
 *        与最终权威 Top5（retrieved_article_ids）取精确集合交集 -> retrieval_hit=1/0；
 *        解析失败（REFERENCE_ARTICLE_NOT_FOUND / REFERENCE_ARTICLE_AMBIGUOUS）-> hit=NULL，不判 miss。
 *       LEGACY_KEYWORD_V1（历史 run #1/#3/#4）：关键词命中旧口径，不重算不修改 result。
 *   - DIRECT_LLM 的 retrieval_hit 恒为 NULL（不适用，不进命中率分母）；
 *     QU reject / 检索完成前技术失败 为 NULL；gate reject 但 Top5 已填充 -> 仍判定；
 *     检索已完成 Top5 后即使 Evidence Gate 拒绝 / Answer LLM 失败，仍可计算 hit。
 *   - retrieval_hit 的判定只使用当前有效 legal_article（两侧 valid_status=1），严禁以检索结果反向拟合。
 *   - response_time_ms = 端到端（含 Answer LLM 与失败路径）。
 *   - REJECTED 是合法系统结果（gate/QU 判定），FAILED 才是执行失败。
 */
public interface EvaluationExecutionService {

    EvaluationRun createRun(String name, Long userId, String username, String ip);

    ExecuteNextResponse executeNext(Long runId, Long userId, String username, String ip);

    EvaluationRunDetail getRunDetail(Long runId);

    List<EvaluationResultView> listResults(Long runId);

    RunSummaryResponse getSummary(Long runId);

    void updateScore(Long resultId, ScoreRequest request, Long userId, String username, String ip);

    /** 导出 CSV 内容（UTF-8，调用方负责写响应） */
    String exportCsv(Long runId, Long userId, String username, String ip);
}
