package com.ailegal.dto;

import java.util.List;

/**
 * 评测批次自动统计：按 mode 聚合。
 */
public class RunSummaryResponse {

    private long runId;
    private List<ModeSummary> modes;

    public RunSummaryResponse(long runId, List<ModeSummary> modes) {
        this.runId = runId;
        this.modes = modes;
    }

    public long getRunId() { return runId; }
    public void setRunId(long runId) { this.runId = runId; }
    public List<ModeSummary> getModes() { return modes; }
    public void setModes(List<ModeSummary> modes) { this.modes = modes; }
}
