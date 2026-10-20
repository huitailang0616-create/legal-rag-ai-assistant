package com.ailegal.service.impl;

import com.ailegal.dto.ConsultCitation;
import com.ailegal.dto.ConsultRuntimeRecordDetailResponse;
import com.ailegal.dto.LegalArticleDocument;
import com.ailegal.dto.PageResult;
import com.ailegal.dto.RuntimeArticleView;
import com.ailegal.entity.ConsultRuntimeRecord;
import com.ailegal.exception.BusinessException;
import com.ailegal.mapper.ConsultRuntimeRecordMapper;
import com.ailegal.service.ConsultRuntimeRecordService;
import com.ailegal.service.LegalKnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行记录（管理员遥测）。写入走 saveQuietly（吞异常，绝不影响业务）；
 * 查询仿 OperationLogServiceImpl.pageLogs 的 LambdaQueryWrapper 风格，只读，不写操作日志。
 */
@Service
public class ConsultRuntimeRecordServiceImpl implements ConsultRuntimeRecordService {

    private static final Logger log = LoggerFactory.getLogger(ConsultRuntimeRecordServiceImpl.class);

    private static final long MAX_SIZE = 100;

    private final ConsultRuntimeRecordMapper consultRuntimeRecordMapper;
    private final LegalKnowledgeService legalKnowledgeService;
    private final ObjectMapper objectMapper;

    public ConsultRuntimeRecordServiceImpl(ConsultRuntimeRecordMapper consultRuntimeRecordMapper,
                                           LegalKnowledgeService legalKnowledgeService,
                                           ObjectMapper objectMapper) {
        this.consultRuntimeRecordMapper = consultRuntimeRecordMapper;
        this.legalKnowledgeService = legalKnowledgeService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveQuietly(ConsultRuntimeRecord record) {
        try {
            consultRuntimeRecordMapper.insert(record);
        } catch (Exception e) {
            // telemetry: a failed runtime record must never turn a successful consult into a 500
            log.warn("consult runtime record save failed (consult result unaffected): {}", e.getMessage());
        }
    }

    @Override
    public PageResult<ConsultRuntimeRecord> pageRecords(long page, long size,
                                                        LocalDateTime startTime, LocalDateTime endTime,
                                                        String domain, String executionStatus, String username) {
        LambdaQueryWrapper<ConsultRuntimeRecord> query = new LambdaQueryWrapper<>();
        query.ge(startTime != null, ConsultRuntimeRecord::getCreatedAt, startTime);
        query.le(endTime != null, ConsultRuntimeRecord::getCreatedAt, endTime);
        query.eq(domain != null && !domain.isBlank(), ConsultRuntimeRecord::getDomain, domain);
        query.eq(executionStatus != null && !executionStatus.isBlank(), ConsultRuntimeRecord::getExecutionStatus, executionStatus);
        query.like(username != null && !username.isBlank(), ConsultRuntimeRecord::getUsername, username);
        query.orderByDesc(ConsultRuntimeRecord::getId);
        Page<ConsultRuntimeRecord> result =
                consultRuntimeRecordMapper.selectPage(new Page<>(page, Math.min(size, MAX_SIZE)), query);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public ConsultRuntimeRecordDetailResponse getDetail(Long id) {
        ConsultRuntimeRecord r = consultRuntimeRecordMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(404, "运行记录不存在");
        }
        ConsultRuntimeRecordDetailResponse resp = new ConsultRuntimeRecordDetailResponse();
        resp.setId(r.getId());
        resp.setUserId(r.getUserId());
        resp.setUsername(r.getUsername());
        resp.setQuestion(r.getQuestion());
        resp.setDomain(r.getDomain());
        resp.setRetrievalMode(r.getRetrievalMode());
        resp.setFallbackReason(r.getFallbackReason());
        resp.setRelevanceReason(r.getRelevanceReason());
        resp.setAnswer(r.getAnswer());
        resp.setResponseTimeMs(r.getResponseTimeMs());
        resp.setExecutionStatus(r.getExecutionStatus());
        resp.setErrorCode(r.getErrorCode());
        resp.setCreatedAt(r.getCreatedAt());
        resp.setTop5Articles(loadTop5Articles(r.getRetrievedArticleIds()));
        resp.setCitations(parseCitations(r.getCitationsJson()));
        return resp;
    }

    /**
     * Top5 条款可读视图：按记录中保存的 articleId 顺序反查当前 MySQL authoritative article；
     * 已失效/不存在的条款以 missing=true 呈现，绝不因历史数据缺失而 500。
     */
    private List<RuntimeArticleView> loadTop5Articles(String retrievedArticleIds) {
        List<Long> ids = parseIdList(retrievedArticleIds);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<LegalArticleDocument> articles = legalKnowledgeService.selectValidArticlesWithDocuments(ids);
        Map<Long, LegalArticleDocument> byId = new HashMap<>();
        for (LegalArticleDocument a : articles) {
            byId.put(a.getArticleId(), a);
        }
        List<RuntimeArticleView> views = new ArrayList<>();
        for (Long id : ids) {
            RuntimeArticleView v = new RuntimeArticleView();
            v.setArticleId(id);
            LegalArticleDocument a = byId.get(id);
            if (a == null) {
                v.setMissing(true);
            } else {
                v.setDocumentName(a.getDocumentName());
                v.setArticleNumber(a.getArticleNumber());
                v.setContent(a.getContent());
            }
            views.add(v);
        }
        return views;
    }

    private List<Long> parseIdList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("runtime retrieved ids parse failed id={}", e.getMessage());
            return List.of();
        }
    }

    private List<ConsultCitation> parseCitations(String citationsJson) {
        if (citationsJson == null || citationsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(citationsJson, new TypeReference<List<ConsultCitation>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("runtime citations parse failed id={}", e.getMessage());
            return List.of();
        }
    }
}
