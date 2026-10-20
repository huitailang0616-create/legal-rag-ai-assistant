package com.ailegal.dto;

/**
 * execute-next 单条推进结果。最小 DTO，不回传 answer/引用。
 */
public class ExecuteNextResponse {

    /** 本次实际执行的结果 id；无 PENDING 可执行时为 null */
    private Long executedResultId;
    private String resultExecutionStatus;
    private String runStatus;
    private long pendingCount;
    private long totalCount;

    public ExecuteNextResponse() {
    }

    public ExecuteNextResponse(Long executedResultId, String resultExecutionStatus,
                               String runStatus, long pendingCount, long totalCount) {
        this.executedResultId = executedResultId;
        this.resultExecutionStatus = resultExecutionStatus;
        this.runStatus = runStatus;
        this.pendingCount = pendingCount;
        this.totalCount = totalCount;
    }

    public Long getExecutedResultId() { return executedResultId; }
    public void setExecutedResultId(Long executedResultId) { this.executedResultId = executedResultId; }
    public String getResultExecutionStatus() { return resultExecutionStatus; }
    public void setResultExecutionStatus(String resultExecutionStatus) { this.resultExecutionStatus = resultExecutionStatus; }
    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String runStatus) { this.runStatus = runStatus; }
    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }
    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
}
