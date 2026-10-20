package com.ailegal.service.impl;

import com.ailegal.dto.PageResult;
import com.ailegal.entity.OperationLog;
import com.ailegal.mapper.OperationLogMapper;
import com.ailegal.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public void log(Long userId, String username, String operation, String target, String result, String ip) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setOperation(operation);
        log.setTarget(target);
        log.setResult(result);
        log.setIp(ip != null ? ip : "");
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(Long userId, String username, String operation, String target, String result, String ip) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setOperation(operation);
        log.setTarget(target);
        log.setResult(result);
        log.setIp(ip != null ? ip : "");
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    @Override
    public PageResult<OperationLog> pageLogs(long page, long size, String operation, Long userId, String username,
                                             LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<OperationLog> query = new LambdaQueryWrapper<>();
        query.eq(operation != null && !operation.isBlank(), OperationLog::getOperation, operation);
        query.eq(userId != null, OperationLog::getUserId, userId);
        query.like(username != null && !username.isBlank(), OperationLog::getUsername, username);
        query.ge(startTime != null, OperationLog::getCreatedAt, startTime);
        query.le(endTime != null, OperationLog::getCreatedAt, endTime);
        query.orderByDesc(OperationLog::getCreatedAt).orderByDesc(OperationLog::getId);

        Page<OperationLog> result = operationLogMapper.selectPage(new Page<>(page, size), query);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }
}
