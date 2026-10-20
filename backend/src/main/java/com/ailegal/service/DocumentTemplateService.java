package com.ailegal.service;

/**
 * 5B 固定文书模板服务（Java 固定结构组装，无 Thymeleaf/Freemarker/DB 模板表/复杂模板 DSL）。
 *
 * 模板边界：
 *   - LLM 输出只能进入 factsDraft / reasoningDraft 两个固定位置；
 *   - 用户原始数据（claims / counterpartyDescription / 案件标题）按需进入固定槽位；
 *   - 身份/机构/日期等缺失信息一律留 [请填写……] 占位符，模板不猜不补；
 *   - 文书首行标题使用固定类型名（模板内部确定性，不引用 draft.title 跨字段耦合）。
 */
public interface DocumentTemplateService {

    /**
     * documentType 与 domain 适用关系（5B 正式锁定）：
     *   LABOR_ARBITRATION_APPLICATION → 仅 LABOR_DISPUTE
     *   CIVIL_COMPLAINT              → HOUSE_RENTAL / CONSUMER_RIGHTS
     *   DEMAND_LETTER                → 三领域全允许
     * 5A CRUD 不强制本矩阵；仅在正式生成（generate）前校验，不兼容返回 400 且不调 LLM。
     */
    boolean isCompatible(String documentType, String domain);

    /**
     * 按固定模板渲染完整文书文本。输入仅含 LLM 两字段 + 案件原始字段。
     * 纯函数、无 I/O、确定性；渲染异常视为生成失败（外层失败原子性路径）。
     */
    String render(String documentType, TemplateInput input);

    /** 模板渲染输入：唯一数据源 = LLM 两字段 + case_analysis 原始列（claims/counterpartyDescription/title） */
    record TemplateInput(String factsDraft,
                         String reasoningDraft,
                         String claims,
                         String counterpartyDescription,
                         String caseTitle) {
    }
}
