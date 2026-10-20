package com.ailegal.dto;

import com.ailegal.entity.EvaluationRun;

/**
 * 评测批次详情：run 基本信息 + 进度计数（不返回巨大 raw 日志）。
 */
public class EvaluationRunDetail {

    private EvaluationRun run;
    private long total;
    private long pending;
    private long running;
    private long success;
    private long rejected;
    private long failed;

    public EvaluationRun getRun() { return run; }
    public void setRun(EvaluationRun run) { this.run = run; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getPending() { return pending; }
    public void setPending(long pending) { this.pending = pending; }
    public long getRunning() { return running; }
    public void setRunning(long running) { this.running = running; }
    public long getSuccess() { return success; }
    public void setSuccess(long success) { this.success = success; }
    public long getRejected() { return rejected; }
    public void setRejected(long rejected) { this.rejected = rejected; }
    public long getFailed() { return failed; }
    public void setFailed(long failed) { this.failed = failed; }
}
