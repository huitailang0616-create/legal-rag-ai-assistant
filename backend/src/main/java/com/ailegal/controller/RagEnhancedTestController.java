package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.EnhancedRetrieveTestResponse;
import com.ailegal.dto.RagRetrieveTestRequest;
import com.ailegal.security.UserContext;
import com.ailegal.service.ConsultService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4A technical acceptance API (ADMIN only, no frontend entry):
 * POST /api/admin/rag/enhanced-retrieve-test — Query Understanding + Basic RAG chain
 * up to the gate, WITHOUT the Answer LLM. Request body reuses RagRetrieveTestRequest.
 * USER -> HTTP 403, unauthenticated -> HTTP 401 (SecurityConfig /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin/rag")
@PreAuthorize("hasRole('ADMIN')")
public class RagEnhancedTestController {

    private final ConsultService consultService;

    public RagEnhancedTestController(ConsultService consultService) {
        this.consultService = consultService;
    }

    @PostMapping("/enhanced-retrieve-test")
    public ApiResponse<EnhancedRetrieveTestResponse> enhancedRetrieveTest(@Valid @RequestBody RagRetrieveTestRequest request,
                                                                          HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(consultService.enhancedRetrieveTest(request.getQuestion(), userId, username, ip));
    }
}
