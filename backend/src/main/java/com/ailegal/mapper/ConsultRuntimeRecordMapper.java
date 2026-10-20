package com.ailegal.mapper;

import com.ailegal.entity.ConsultRuntimeRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 正式法律咨询运行记录表 Mapper。BaseMapper 提供 insert 与分页 selectPage。
 */
@Mapper
public interface ConsultRuntimeRecordMapper extends BaseMapper<ConsultRuntimeRecord> {
}
