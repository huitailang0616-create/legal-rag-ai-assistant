package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.CreateRunRequest;
import com.ailegal.dto.EvaluationResultView;
import com.ailegal.dto.EvaluationRunDetail;
import com.ailegal.dto.ExecuteNextResponse;
import com.ailegal.dto.RunSummaryResponse;
import com.ailegal.dto.ScoreRequest;
import com.ailegal.entity.EvaluationRun;
import com.ailegal.security.UserContext;
import com.ailegal.service.EvaluationExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 模块6B 评测执行与可视化 API。
 * 路径 /api/admin/** 已由 SecurityConfig 统一限制为 ADMIN 角色，
 * 方法级 @PreAuthorize 作为纵深防御。
 */
@RestController
@RequestMapping("/api/admin/evaluations")
public class EvaluationExecutionController {

    private final EvaluationExecutionService evaluationExecutionService;

    public EvaluationExecutionController(EvaluationExecutionService evaluationExecutionService) {
        this.evaluationExecutionService = evaluationExecutionService;
    }

    /** 创建评测批次：30 题 x 3 方案 = 90 条 PENDING 结果 */
    @PostMapping("/runs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<EvaluationRun> createRun(@Valid @RequestBody CreateRunRequest request,
                                                HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(evaluationExecutionService.createRun(
                request.getName(), userId, username, ip));
    }

    /**
     * 推进一条：抢占 -> 真实执行 -> 落终态。
     * 前端循环调用，直到 pendingCount = 0；短事务设计保证并发安全。
     */
    @PostMapping("/runs/{runId}/execute-next")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ExecuteNextResponse> executeNext(@PathVariable Long runId,
                                                        HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(evaluationExecutionService.executeNext(runId, userId, username, ip));
    }

    /** 批次详情（含进度计数），前端刷新恢复用 */
    @GetMapping("/runs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<EvaluationRunDetail> getRun(@PathVariable Long id) {
        return ApiResponse.success(evaluationExecutionService.getRunDetail(id));
    }

    /** 批次全部结果（90 条，含题目信息） */
    @GetMapping("/runs/{id}/results")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<EvaluationResultView>> listResults(@PathVariable Long id) {
        return ApiResponse.success(evaluationExecutionService.listResults(id));
    }

    /** 批次自动统计（按 mode 聚合） */
    @GetMapping("/runs/{id}/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RunSummaryResponse> getSummary(@PathVariable Long id) {
        return ApiResponse.success(evaluationExecutionService.getSummary(id));
    }

    /** 人工评分（四个 1~5 分 + 备注） */
    @PutMapping("/results/{resultId}/score")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> updateScore(@PathVariable Long resultId,
                                           @Valid @RequestBody ScoreRequest request,
                                           HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        evaluationExecutionService.updateScore(resultId, request, userId, username, ip);
        return ApiResponse.success("评分已保存", "ok");
    }

    /** CSV 导出：真实文件下载（BOM + RFC4180 + 公式注入防护） */
    @GetMapping(value = "/runs/{id}/export.csv")
    @PreAuthorize("hasRole('ADMIN')")
    public void exportCsv(@PathVariable Long id, HttpServletResponse response,
                          HttpServletRequest httpRequest) throws IOException {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        String csv = evaluationExecutionService.exportCsv(id, userId, username, ip);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"evaluation_run_" + id + ".csv\"");
        response.setContentLength(csv.getBytes(StandardCharsets.UTF_8).length);
        response.getWriter().write(csv);
        response.getWriter().flush();
    }
}
