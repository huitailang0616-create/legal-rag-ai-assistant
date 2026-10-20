package com.ailegal.service.impl;

import com.ailegal.dto.ConsultCitation;
import com.ailegal.dto.ConsultResponse;
import com.ailegal.dto.ConsultationHistoryDetailResponse;
import com.ailegal.dto.ConsultationHistoryListResponse;
import com.ailegal.dto.PageResult;
import com.ailegal.entity.ConsultationHistory;
import com.ailegal.exception.BusinessException;
import com.ailegal.mapper.ConsultationHistoryMapper;
import com.ailegal.service.ConsultationHistoryService;
import com.ailegal.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 咨询历史（Final-System-Closeout-A）。
 *
 * 保存语义（Pro 审查决策 A）：历史是"用户得到过回答"的正式业务数据，保存失败必须让
 * /api/consult 整体失败（先 operation_log.logFailure 记录技术失败，再抛 500），
 * 不允许静默缺行 —— 否则"保存记录"功能被不可见地破坏。operation_log 的 safeLog
 * 模式（绝不破坏流程）继续只用于技术日志，本表是用户可见正式数据，不套用。
 *
 * 所有单条取数（GET/DELETE）统一经 getOwn(id, userId) 联合定位：
 * id + 当前用户 id + deleted=0，一处实现防 IDOR；不存在/非本人统一 404。
 * 本表禁止物理删除：删除 = deleted 置 1；查询显式过滤 deleted=0。
 */
@Service
public class ConsultationHistoryServiceImpl implements ConsultationHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ConsultationHistoryServiceImpl.class);

    private static final int DELETED_NO = 0;
    private static final int DELETED_YES = 1;
    private static final int MAX_SIZE = 50;
    private static final String DELETE_OP = "CONSULTATION_HISTORY_DELETE";

    private final ConsultationHistoryMapper consultationHistoryMapper;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    public ConsultationHistoryServiceImpl(ConsultationHistoryMapper consultationHistoryMapper,
                                          OperationLogService operationLogService,
                                          ObjectMapper objectMapper) {
        this.consultationHistoryMapper = consultationHistoryMapper;
        this.operationLogService = operationLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(Long userId, String question, ConsultResponse response) {
        ConsultationHistory h = new ConsultationHistory();
        h.setUserId(userId);
        h.setQuestion(question);
        h.setDomain(response.getDetectedDomain());
        h.setAnswer(response.getAnswer());
        h.setEvidenceInsufficient(response.isEvidenceInsufficient() ? 1 : 0);
        h.setRelevanceReason(response.getRelevanceReason());
        h.setCitationsJson(serializeCitations(response.getCitations()));
        h.setCreatedAt(LocalDateTime.now());
        h.setDeleted(DELETED_NO);
        try {
            consultationHistoryMapper.insert(h);
        } catch (Exception e) {
            // 方案 A：先记录技术失败（REQUIRES_NEW 独立事务必落库），再让整个 consult 失败
            try {
                operationLogService.logFailure(userId, "unknown", "CONSULT_HISTORY_SAVE",
                        "relevanceReason=" + response.getRelevanceReason(),
                        "失败:consultationHistory:" + shortMessage(e), "");
            } catch (Exception logEx) {
                log.warn("consultation history failure log failed", logEx);
            }
            throw new BusinessException(500, "咨询记录保存失败，请稍后重试");
        }
    }

    @Override
    public PageResult<ConsultationHistoryListResponse> list(long page, long size, Long userId) {
        LambdaQueryWrapper<ConsultationHistory> q = new LambdaQueryWrapper<ConsultationHistory>()
                .eq(ConsultationHistory::getUserId, userId)
                .eq(ConsultationHistory::getDeleted, DELETED_NO)
                .orderByDesc(ConsultationHistory::getCreatedAt)
                .orderByDesc(ConsultationHistory::getId);
        Page<ConsultationHistory> result =
                consultationHistoryMapper.selectPage(new Page<>(page, Math.min(size, MAX_SIZE)), q);
        List<ConsultationHistoryListResponse> records = result.getRecords().stream()
                .map(ConsultationHistoryListResponse::fromEntity)
                .toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    @Override
    public ConsultationHistoryDetailResponse get(Long id, Long userId) {
        ConsultationHistory h = getOwn(id, userId);
        ConsultationHistoryDetailResponse r = new ConsultationHistoryDetailResponse();
        r.setId(h.getId());
        r.setQuestion(h.getQuestion());
        r.setDomain(h.getDomain());
        r.setAnswer(h.getAnswer());
        r.setCitations(parseCitations(h.getCitationsJson()));
        r.setEvidenceInsufficient(h.getEvidenceInsufficient() != null && h.getEvidenceInsufficient() == 1);
        r.setRelevanceReason(h.getRelevanceReason());
        r.setCreatedAt(h.getCreatedAt());
        return r;
    }

    @Override
    public void delete(Long id, Long userId, String username, String ip) {
        ConsultationHistory h = getOwn(id, userId);
        h.setDeleted(DELETED_YES);
        consultationHistoryMapper.updateById(h);
        try {
            operationLogService.log(userId, username, DELETE_OP, "id=" + h.getId(), "删除成功（逻辑删除）", ip);
        } catch (Exception logEx) {
            log.warn("consultation history delete log failed", logEx);
        }
    }

    private ConsultationHistory getOwn(Long id, Long userId) {
        ConsultationHistory h = consultationHistoryMapper.selectOne(
                new LambdaQueryWrapper<ConsultationHistory>()
                        .eq(ConsultationHistory::getId, id)
                        .eq(ConsultationHistory::getUserId, userId)
                        .eq(ConsultationHistory::getDeleted, DELETED_NO));
        if (h == null) {
            throw new BusinessException(404, "记录不存在");
        }
        return h;
    }

    private String serializeCitations(List<ConsultCitation> citations) {
        try {
            return objectMapper.writeValueAsString(citations == null ? List.of() : citations);
        } catch (JsonProcessingException e) {
            log.warn("citation serialize failed", e);
            return "[]";
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
            log.warn("citation parse failed id={}", e);
            throw new BusinessException(500, "历史数据异常，无法读取");
        }
    }

    private String shortMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 120 ? msg.substring(0, 120) : msg;
    }
}
