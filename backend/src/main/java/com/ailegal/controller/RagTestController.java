package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.RagRetrieveTestRequest;
import com.ailegal.dto.RagRetrieveTestResponse;
import com.ailegal.security.UserContext;
import com.ailegal.service.RagService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 3A calibration-only retrieval API. Not the final /api/consult (that belongs to 3B).
 * ADMIN only: USER -> HTTP 403, unauthenticated -> HTTP 401.
 */
@RestController
@RequestMapping("/api/admin/rag")
@PreAuthorize("hasRole('ADMIN')")
public class RagTestController {

    private final RagService ragService;

    public RagTestController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/retrieve-test")
    public ApiResponse<RagRetrieveTestResponse> retrieveTest(@Valid @RequestBody RagRetrieveTestRequest request,
                                                             HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(ragService.retrieveTest(request.getQuestion(), userId, username, ip));
    }
}