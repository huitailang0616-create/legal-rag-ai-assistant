package com.ailegal.service;

import com.ailegal.dto.PageResult;
import com.ailegal.entity.OperationLog;

import java.time.LocalDateTime;

public interface OperationLogService {

    void log(Long userId, String username, String operation, String target, String result, String ip);

    /**
     * Write a failure log in an independent transaction.
     * Used when the outer business transaction will roll back but the failure record must survive.
     */
    void logFailure(Long userId, String username, String operation, String target, String result, String ip);

    /**
     * Read-only paged query for the ADMIN operation log page (module 6).
     * Filters: operation exact match, userId exact match, username like,
     * created_at in [startTime, endTime] (either bound optional).
     * NOTE: this read path deliberately never writes a log entry.
     */
    PageResult<OperationLog> pageLogs(long page, long size, String operation, Long userId, String username,
                                      LocalDateTime startTime, LocalDateTime endTime);
}
