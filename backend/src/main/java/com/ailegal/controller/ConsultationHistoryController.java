package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.ConsultationHistoryDetailResponse;
import com.ailegal.dto.ConsultationHistoryListResponse;
import com.ailegal.dto.PageResult;
import com.ailegal.security.UserContext;
import com.ailegal.service.ConsultationHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户咨询历史 USER API（Final-System-Closeout-A）。
 * 数据隔离：所有单条操作由 service 按 id + 当前 userId + deleted=0 联合定位（防 IDOR）。
 * 鉴权：默认 authenticated 规则（/api/**），未登录 401。
 * 无 Update：历史是"当时回答"的不可变快照，不允许用户编辑问题/答案。
 */
@RestController
@RequestMapping("/api/consultations")
public class ConsultationHistoryController {

    private final ConsultationHistoryService consultationHistoryService;

    public ConsultationHistoryController(ConsultationHistoryService consultationHistoryService) {
        this.consultationHistoryService = consultationHistoryService;
    }

    @GetMapping
    public ApiResponse<PageResult<ConsultationHistoryListResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(consultationHistoryService.list(page, size, userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConsultationHistoryDetailResponse> get(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(consultationHistoryService.get(id, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        consultationHistoryService.delete(id, userId, username, ip);
        return ApiResponse.success("删除成功", null);
    }
}
