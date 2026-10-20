package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.ConsultRequest;
import com.ailegal.dto.ConsultResponse;
import com.ailegal.security.UserContext;
import com.ailegal.service.ConsultService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User legal consult (3B-1). Accessible to USER and ADMIN (default authenticated
 * rule in SecurityConfig: /api/consult -> anyRequest().authenticated()), 401 when
 * unauthenticated. Not ADMIN-only, not streaming.
 */
@RestController
@RequestMapping("/api/consult")
public class ConsultController {

    private final ConsultService consultService;

    public ConsultController(ConsultService consultService) {
        this.consultService = consultService;
    }

    @PostMapping
    public ApiResponse<ConsultResponse> consult(@Valid @RequestBody ConsultRequest request,
                                                HttpServletRequest httpRequest) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String ip = httpRequest.getRemoteAddr();
        return ApiResponse.success(consultService.consult(request.getQuestion(), request.getCaseAnalysisId(),
                userId, username, ip));
    }
}
