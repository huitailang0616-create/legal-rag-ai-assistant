package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.ProcessGuideResponse;
import com.ailegal.service.ProcessGuideService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 固定办事流程指引只读 API（模块5A）。
 * Java 固定结构返回，无 DB、无 LLM、无 RAG；USER 登录后访问（/api/** 默认 authenticated）。
 * GET 读操作不记录操作日志（与模块4 list/get 一致）。
 */
@RestController
@RequestMapping("/api/process-guides")
public class ProcessGuideController {

    private final ProcessGuideService processGuideService;

    public ProcessGuideController(ProcessGuideService processGuideService) {
        this.processGuideService = processGuideService;
    }

    @GetMapping
    public ApiResponse<List<ProcessGuideResponse>> list() {
        return ApiResponse.success(processGuideService.listAll());
    }
}
