package com.ailegal.mapper;

import com.ailegal.entity.EvaluationResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EvaluationResultMapper extends BaseMapper<EvaluationResult> {

    /**
     * 抢占一条 PENDING 结果：行锁 + LIMIT 1，必须在短事务内调用并尽快提交。
     * 同一时间只有一个 execute-next 调用者能拿到该行，其余调用者阻塞在该锁上。
     */
    @Select("SELECT id FROM evaluation_result WHERE run_id = #{runId} "
            + "AND execution_status = 'PENDING' ORDER BY id LIMIT 1 FOR UPDATE")
    Long selectFirstPendingIdForUpdate(@Param("runId") Long runId);
}
