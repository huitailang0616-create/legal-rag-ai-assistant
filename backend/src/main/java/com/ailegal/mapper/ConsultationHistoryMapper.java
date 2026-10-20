package com.ailegal.mapper;

import com.ailegal.entity.ConsultationHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 咨询历史表 Mapper。BaseMapper 提供分页 selectPage 与条件查询。
 */
@Mapper
public interface ConsultationHistoryMapper extends BaseMapper<ConsultationHistory> {
}
