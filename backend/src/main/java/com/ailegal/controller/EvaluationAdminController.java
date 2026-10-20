package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.ConsultRuntimeRecordDetailResponse;
import com.ailegal.dto.ConsultRuntimeRecordListResponse;
import com.ailegal.dto.PageResult;
import com.ailegal.entity.ConsultRuntimeRecord;
import com.ailegal.entity.EvaluationQuestion;
import com.ailegal.entity.EvaluationRun;
import com.ailegal.entity.OperationLog;
import com.ailegal.mapper.EvaluationQuestionMapper;
import com.ailegal.mapper.EvaluationRunMapper;
import com.ailegal.service.ConsultRuntimeRecordService;
import com.ailegal.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模块6 系统评测与运行管理 - ADMIN 只读接口。
 * 路径 /api/admin/** 已由 SecurityConfig 统一限制为 ADMIN 角色，
 * 方法级 @PreAuthorize 作为纵深防御。
 * 纯读接口，不写 operation_log（避免"查看日志写日志"）。
 */
@RestController
@RequestMapping("/api/admin")
public class EvaluationAdminController {

    private final EvaluationQuestionMapper evaluationQuestionMapper;
    private final EvaluationRunMapper evaluationRunMapper;
    private final OperationLogService operationLogService;
    private final ConsultRuntimeRecordService consultRuntimeRecordService;

    public EvaluationAdminController(EvaluationQuestionMapper evaluationQuestionMapper,
                                     EvaluationRunMapper evaluationRunMapper,
                                     OperationLogService operationLogService,
                                     ConsultRuntimeRecordService consultRuntimeRecordService) {
        this.evaluationQuestionMapper = evaluationQuestionMapper;
        this.evaluationRunMapper = evaluationRunMapper;
        this.operationLogService = operationLogService;
        this.consultRuntimeRecordService = consultRuntimeRecordService;
    }

    /** 固定评测题列表，可按 domain 过滤，按 question_code 升序 */
    @GetMapping("/evaluations/questions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<EvaluationQuestion>> listQuestions(
            @RequestParam(required = false) String domain) {
        LambdaQueryWrapper<EvaluationQuestion> query = new LambdaQueryWrapper<>();
        query.eq(domain != null && !domain.isBlank(), EvaluationQuestion::getDomain, domain);
        query.orderByAsc(EvaluationQuestion::getQuestionCode);
        return ApiResponse.success(evaluationQuestionMapper.selectList(query));
    }

    /** 评测批次列表，最新在前 */
    @GetMapping("/evaluations/runs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<EvaluationRun>> listRuns() {
        LambdaQueryWrapper<EvaluationRun> query = new LambdaQueryWrapper<>();
        query.orderByDesc(EvaluationRun::getId);
        return ApiResponse.success(evaluationRunMapper.selectList(query));
    }

    /** 操作日志分页查询（只读）：时间范围 [startTime, endTime]（可只传一端或都不传） */
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<OperationLog>> listLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        return ApiResponse.success(operationLogService.pageLogs(safePage, safeSize, operation, userId, username,
                startTime, endTime));
    }

    /** 正式咨询运行记录分页（ADMIN）：时间范围 [startTime, endTime] + domain + executionStatus + username 过滤 */
    @GetMapping("/consult-runtime-records")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<ConsultRuntimeRecordListResponse>> listRuntimeRecords(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String executionStatus,
            @RequestParam(required = false) String username) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        PageResult<ConsultRuntimeRecord> result = consultRuntimeRecordService
                .pageRecords(safePage, safeSize, startTime, endTime, domain, executionStatus, username);
        List<ConsultRuntimeRecordListResponse> list = result.getRecords().stream()
                .map(ConsultRuntimeRecordListResponse::fromEntity)
                .collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(list, result.getTotal(), safePage, safeSize));
    }

    /** 咨询运行记录详情（ADMIN）：完整字段 + Top5 条款可读视图 + 引用；不存在 404 */
    @GetMapping("/consult-runtime-records/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ConsultRuntimeRecordDetailResponse> runtimeRecordDetail(@PathVariable Long id) {
        return ApiResponse.success(consultRuntimeRecordService.getDetail(id));
    }
}
