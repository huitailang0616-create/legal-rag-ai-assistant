package com.ailegal.service;

import com.ailegal.dto.ConsultRuntimeRecordDetailResponse;
import com.ailegal.dto.PageResult;
import com.ailegal.entity.ConsultRuntimeRecord;

import java.time.LocalDateTime;

/**
 * 正式法律咨询运行记录（管理员遥测，Final-Requirement-Gaps-B）。
 * 只写不读用户侧；管理员只读查询。查询不写 operation_log。
 */
public interface ConsultRuntimeRecordService {

    /**
     * Telemetry 语义：运行记录保存失败绝不影响正式咨询结果 —— 吞异常 + server warn。
     * （与 consultation_history 的"方案 A"相反：历史是用户可见正式数据，保存失败让咨询失败。）
     */
    void saveQuietly(ConsultRuntimeRecord record);

    /** ADMIN 分页查询：时间范围 [startTime, endTime]（可只传一端或都不传）+ domain + executionStatus + username */
    PageResult<ConsultRuntimeRecord> pageRecords(long page, long size,
                                                 LocalDateTime startTime, LocalDateTime endTime,
                                                 String domain, String executionStatus, String username);

    /** ADMIN 详情：完整字段 + Top5 条款可读视图（失效条款 missing=true）+ 解析后的引用；不存在 404 */
    ConsultRuntimeRecordDetailResponse getDetail(Long id);
}
