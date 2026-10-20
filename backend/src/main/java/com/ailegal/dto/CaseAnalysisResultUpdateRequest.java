package com.ailegal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 4C 编辑分析说明请求：仅携带 analysis_result 文本。
 * Bean Validation：必填 + 上限 10000 字符（对应 analysis_result TEXT 列）。
 * 本请求不可篡改 risk_result_json / analysis_status / extraction_json / user_id（服务端显式只 set 两列）。
 */
public class CaseAnalysisResultUpdateRequest {

    @NotBlank(message = "分析结果不能为空")
    @Size(max = 10000, message = "分析结果长度不能超过10000字符")
    private String analysisResult;

    public String getAnalysisResult() { return analysisResult; }
    public void setAnalysisResult(String analysisResult) { this.analysisResult = analysisResult; }
}
