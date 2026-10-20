package com.ailegal.service;

import com.ailegal.dto.ConsultResponse;
import com.ailegal.dto.ConsultationHistoryDetailResponse;
import com.ailegal.dto.ConsultationHistoryListResponse;
import com.ailegal.dto.PageResult;

/**
 * 咨询历史（Final-System-Closeout-A）。
 * save 只在正式 /api/consult 形成业务响应后调用；失败路径（HTTP 500）不产生历史。
 */
public interface ConsultationHistoryService {

    /**
     * 保存一条咨询历史（方案 A 语义：保存失败 → 记录技术失败日志后抛 500，使整个 consult 失败）。
     * 只接受 question + ConsultResponse（与用户所见响应完全一致），类型上封死
     * rewrittenQuery / keyFacts / Top5 debug / Qdrant payload 等内部数据落库可能。
     */
    void save(Long userId, String question, ConsultResponse response);

    /** 当前用户历史分页列表（deleted=0，created_at 倒序）。size 上限 50。 */
    PageResult<ConsultationHistoryListResponse> list(long page, long size, Long userId);

    /** id + 当前 userId + deleted=0 联合定位；非本人或不存在统一 404（防 IDOR）。 */
    ConsultationHistoryDetailResponse get(Long id, Long userId);

    /** 逻辑删除：deleted=1，禁止物理删除。 */
    void delete(Long id, Long userId, String username, String ip);
}
