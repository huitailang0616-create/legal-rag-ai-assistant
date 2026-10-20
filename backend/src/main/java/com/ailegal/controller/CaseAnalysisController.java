package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.CaseAnalysisCreateRequest;
import com.ailegal.dto.CaseAnalysisResponse;
import com.ailegal.dto.CaseAnalysisResultUpdateRequest;
import com.ailegal.dto.CaseAnalysisUpdateRequest;
import com.ailegal.dto.CaseExtractionUpdateRequest;
import com.ailegal.security.UserContext;
import com.ailegal.service.CaseAnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 案件诉求分析 USER 业务 API（模块4）。
 * 数据隔离：所有单条操作由 service 按 id + 当前 userId 联合定位（防 IDOR）。
 * 鉴权：默认 authenticated 规则（/api/**），未登录 401。
 */
@RestController
@RequestMapping("/api/case-analysis")
public class CaseAnalysisController {

    private final CaseAnalysisService caseAnalysisService;

    public CaseAnalysisController(CaseAnalysisService caseAnalysisService) {
        this.caseAnalysisService = caseAnalysisService;
    }

    @PostMapping
    public ApiResponse<CaseAnalysisResponse> create(@Valid @RequestBody CaseAnalysisCreateRequest request,
                                                    HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(caseAnalysisService.create(request, userId, username, ip));
    }

    @GetMapping
    public ApiResponse<List<CaseAnalysisResponse>> list() {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(caseAnalysisService.listByUser(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<CaseAnalysisResponse> get(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(caseAnalysisService.get(id, userId));
    }

    @PutMapping("/{id}")
    public ApiResponse<CaseAnalysisResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody CaseAnalysisUpdateRequest request,
                                                    HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(caseAnalysisService.update(id, request, userId, username, ip));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        caseAnalysisService.delete(id, userId, username, ip);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 4B 智能提取案情：无请求体，事实只从 DB 记录读取（id + 当前 userId 联合定位，防 IDOR）。
     * LLM 调用可能耗时较长（最长约 60s），前端需按请求覆盖 axios 超时。
     */
    @PostMapping("/{id}/extract")
    public ApiResponse<CaseAnalysisResponse> extract(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(caseAnalysisService.extract(id, userId, username, ip));
    }

    /**
     * 4C 风险分析：无请求体，输入只来自已保存的 extraction_json（id + 当前 userId 联合定位）。
     * 纯 Java 确定性规则计算，秒级返回，无需覆盖 axios 超时。
     */
    @PostMapping("/{id}/risk-analysis")
    public ApiResponse<CaseAnalysisResponse> riskAnalysis(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(caseAnalysisService.riskAnalysis(id, userId, username, ip));
    }

    /**
     * 4B→4C 人工确认保存提取结果：请求仅含 extraction（人工可编辑的 8 项提取字段）。
     * 服务端经同一严格校验契约后覆盖 extraction_json 并置 extraction_confirmed=1；
     * 同时作废旧 risk_result_json / analysis_result 回 DRAFT（确认后的新版本才是风险分析输入）。
     * 未登录 401；非本人案件 404（ownership 由 service 按 id + userId 联合定位）。
     */
    @PutMapping("/{id}/extraction")
    public ApiResponse<CaseAnalysisResponse> confirmExtraction(@PathVariable Long id,
                                                               @Valid @RequestBody CaseExtractionUpdateRequest request,
                                                               HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(caseAnalysisService.confirmExtraction(id, request, userId, username, ip));
    }

    /**
     * 4C 保存分析说明：仅 analysisResult 字段（Bean Validation），
     * 只更新 analysis_result + updated_at；risk_result_json 等不可由客户端修改。
     */
    @PutMapping("/{id}/analysis-result")
    public ApiResponse<CaseAnalysisResponse> updateAnalysisResult(@PathVariable Long id,
                                                                  @Valid @RequestBody CaseAnalysisResultUpdateRequest request,
                                                                  HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(caseAnalysisService.updateAnalysisResult(
                id, request.getAnalysisResult(), userId, username, ip));
    }
}
