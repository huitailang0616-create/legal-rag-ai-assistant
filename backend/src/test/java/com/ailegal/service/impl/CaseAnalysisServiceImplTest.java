package com.ailegal.service.impl;

import com.ailegal.dto.CaseAnalysisCreateRequest;
import com.ailegal.dto.CaseAnalysisResponse;
import com.ailegal.dto.CaseAnalysisUpdateRequest;
import com.ailegal.dto.CaseExtractionUpdateRequest;
import com.ailegal.dto.CaseFactExtraction;
import com.ailegal.dto.CaseRiskResult;
import com.ailegal.entity.CaseAnalysis;
import com.ailegal.exception.BusinessException;
import com.ailegal.mapper.CaseAnalysisMapper;
import com.ailegal.service.CaseExtractionLlmService;
import com.ailegal.service.CaseFactExtractionValidator;
import com.ailegal.service.CaseRiskAnalyzer;
import com.ailegal.service.OperationLogService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Final-Requirement-Gaps-C 案件分析正式需求对齐回归测试（模块4）：
 * 1) extraction 人工确认保存（人工版落库 + confirmed=1 + 旧风险清空回 DRAFT）
 * 2) ownership（非本人案件 404，且无任何写库）
 * 3) 确认后刷新读取仍为人工版（不回退模型原结果）
 * 4) 重新 LLM extraction 把 confirmed 重置为 false（需重新人工确认）
 * 5) amount / contractStatus CRUD（含 null 清空语义与 UNKNOWN 归一化）
 * 6) missingInformation 不改变 Java 风险等级（规则结果完全由算法决定）
 * 校验器用真实实例（与生产同一严格契约），其余依赖 Mockito 模拟。
 */
@ExtendWith(MockitoExtension.class)
class CaseAnalysisServiceImplTest {

    @Mock
    private CaseAnalysisMapper caseAnalysisMapper;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private CaseExtractionLlmService extractionLlmService;
    @Mock
    private CaseRiskAnalyzer riskAnalyzer;

    /** 真实校验器：人工编辑与 LLM 输出走同一严格契约（结构/长度/数量） */
    private final CaseFactExtractionValidator validator = new CaseFactExtractionValidator();

    private CaseAnalysisServiceImpl service;

    /**
     * 纯单元测试无 Spring/MyBatis 运行时：LambdaQueryWrapper/LambdaUpdateWrapper 依赖
     * TableInfoHelper 的 lambda 缓存，手动初始化（MyBatis-Plus 官方单测标准做法）。
     */
    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), CaseAnalysis.class);
    }

    @BeforeEach
    void setUp() {
        service = new CaseAnalysisServiceImpl(caseAnalysisMapper, operationLogService,
                extractionLlmService, validator, riskAnalyzer);
    }

    // ---- 1. 人工确认保存 ----

    @Test
    @DisplayName("人工确认保存：extraction_json 覆盖为人工版（trim 生效）、confirmed=1、旧风险清空回 DRAFT")
    void confirmExtraction_persistsHumanVersion_markedConfirmed_clearsStaleRisk() {
        CaseAnalysis dbCa = caseEntity(10L, 1L, "ANALYZED",
                validator.toCanonicalJson(extraction("AI原始摘要", List.of("AI原始诉求"), List.of("AI缺口"))), 0, null, "UNKNOWN");
        dbCa.setRiskResultJson("{\"overallLevel\":\"MEDIUM\"}");
        dbCa.setAnalysisResult("旧分析说明");
        // 人工编辑：关键事实 + 新增一条核心诉求 + 手输首尾空白（验证 trim）
        CaseFactExtraction human = extraction("  人工修改后的案件摘要  ",
                List.of("AI原始诉求", "2026年7月已通过微信向公司催要工资"), List.of());
        CaseFactExtraction trimmedHuman = extraction("人工修改后的案件摘要",
                List.of("AI原始诉求", "2026年7月已通过微信向公司催要工资"), List.of());
        CaseAnalysis stored = caseEntity(10L, 1L, "DRAFT", validator.toCanonicalJson(trimmedHuman), 1, null, "UNKNOWN");
        when(caseAnalysisMapper.selectOne(any())).thenReturn(dbCa, stored);

        CaseExtractionUpdateRequest req = new CaseExtractionUpdateRequest();
        req.setExtraction(human);
        CaseAnalysisResponse resp = service.confirmExtraction(10L, req, 1L, "alice", "127.0.0.1");

        ArgumentCaptor<AbstractWrapper> cap = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(caseAnalysisMapper).update(any(), cap.capture());
        Map<String, Object> set = setColumns(cap.getValue());
        // 落库：人工版 canonical + 确认位 1 + 旧风险/分析清空 + 回 DRAFT
        assertEquals(validator.toCanonicalJson(trimmedHuman), set.get("extraction_json"));
        assertEquals(1, set.get("extraction_confirmed"));
        assertNull(set.get("risk_result_json"));
        assertNull(set.get("analysis_result"));
        assertEquals("DRAFT", set.get("analysis_status"));
        // 防篡改：SET 子句只含白名单列，不含 user_id
        assertFalse(set.containsKey("user_id"));
        // 响应即人工版 + 已确认
        assertEquals("人工修改后的案件摘要", resp.getExtraction().getCaseSummary());
        assertTrue(resp.getExtraction().getClaims().contains("2026年7月已通过微信向公司催要工资"));
        assertEquals(1, resp.getExtractionConfirmed());
        verify(operationLogService).log(any(), any(), eq("CASE_ANALYSIS_EXTRACTION_CONFIRM"), any(), any(), any());
    }

    @Test
    @DisplayName("确认保存校验失败：不落库、记录失败日志、400")
    void confirmExtraction_invalidHumanEdit_noWrite() {
        CaseAnalysis dbCa = caseEntity(10L, 1L, "DRAFT",
                validator.toCanonicalJson(extraction("摘要", List.of(), List.of())), 0, null, "UNKNOWN");
        when(caseAnalysisMapper.selectOne(any())).thenReturn(dbCa);
        // 人工把摘要清空 → 校验契约拒绝（案件摘要必须非空）
        CaseFactExtraction bad = extraction("   ", List.of(), List.of());
        CaseExtractionUpdateRequest req = new CaseExtractionUpdateRequest();
        req.setExtraction(bad);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirmExtraction(10L, req, 1L, "alice", "127.0.0.1"));
        assertEquals(400, ex.getCode());
        verify(caseAnalysisMapper, never()).update(any(), any());
        verify(operationLogService).logFailure(any(), any(), eq("CASE_ANALYSIS_EXTRACTION_CONFIRM"), any(), any(), any());
    }

    // ---- 2. ownership ----

    @Test
    @DisplayName("ownership：非本人案件确认 → 404 且无任何写库")
    void confirmExtraction_otherUsersCase_throws404() {
        when(caseAnalysisMapper.selectOne(any())).thenReturn(null);
        CaseExtractionUpdateRequest req = new CaseExtractionUpdateRequest();
        req.setExtraction(extraction("摘要", List.of(), List.of()));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirmExtraction(10L, req, 2L, "bob", "127.0.0.1"));
        assertEquals(404, ex.getCode());
        verify(caseAnalysisMapper, never()).update(any(), any());
    }

    // ---- 3. 确认后刷新读取仍为人工版 ----

    @Test
    @DisplayName("确认后刷新读取：GET 返回仍为人工修改版（不回退模型原结果）")
    void get_returnsConfirmedHumanVersion() {
        CaseFactExtraction human = extraction("人工修改后的案件摘要",
                List.of("AI原始诉求", "2026年7月已通过微信向公司催要工资"), List.of());
        CaseAnalysis stored = caseEntity(10L, 1L, "DRAFT", validator.toCanonicalJson(human), 1, null, "UNKNOWN");
        when(caseAnalysisMapper.selectOne(any())).thenReturn(stored);

        CaseAnalysisResponse resp = service.get(10L, 1L);
        assertEquals("人工修改后的案件摘要", resp.getExtraction().getCaseSummary());
        assertEquals(1, resp.getExtractionConfirmed());
        assertTrue(resp.getExtraction().getClaims().contains("2026年7月已通过微信向公司催要工资"));
    }

    // ---- 4. 重新提取重置 confirmed ----

    @Test
    @DisplayName("重新提取：新结果覆盖 extraction_json 并重置 confirmed=0（需重新人工确认）")
    void reExtract_resetsConfirmedToFalse() {
        String oldJson = validator.toCanonicalJson(extraction("旧AI摘要", List.of("旧诉求"), List.of("旧缺口")));
        CaseAnalysis dbCa = caseEntity(10L, 1L, "ANALYZED", oldJson, 1, null, "UNKNOWN");
        CaseAnalysis stored = caseEntity(10L, 1L, "DRAFT",
                validator.toCanonicalJson(extraction("新AI摘要", List.of("新诉求"), List.of("新缺口"))), 0, null, "UNKNOWN");
        when(caseAnalysisMapper.selectOne(any())).thenReturn(dbCa, stored);
        when(extractionLlmService.extract(any()))
                .thenReturn(validator.toCanonicalJson(extraction("新AI摘要", List.of("新诉求"), List.of("新缺口"))));

        CaseAnalysisResponse resp = service.extract(10L, 1L, "alice", "127.0.0.1");

        ArgumentCaptor<AbstractWrapper> cap = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(caseAnalysisMapper).update(any(), cap.capture());
        Map<String, Object> set = setColumns(cap.getValue());
        assertEquals(0, set.get("extraction_confirmed"));
        assertEquals(validator.toCanonicalJson(extraction("新AI摘要", List.of("新诉求"), List.of("新缺口"))),
                set.get("extraction_json"));
        assertNull(set.get("risk_result_json"));
        assertEquals("DRAFT", set.get("analysis_status"));
        assertEquals(0, resp.getExtractionConfirmed());
    }

    // ---- 5. amount / contractStatus CRUD ----

    @Test
    @DisplayName("CRUD-新建：amount/contractStatus 落库，contractStatus 未传归一化 UNKNOWN")
    void create_persistsAmountAndContractStatus() {
        CaseAnalysisCreateRequest create = new CaseAnalysisCreateRequest();
        create.setTitle("未签劳动合同工资纠纷");
        create.setDomain("LABOR_DISPUTE");
        create.setCaseFacts("公司未签书面劳动合同");
        create.setAmount(new BigDecimal("12000.00"));
        create.setContractStatus("UNSIGNED");
        CaseAnalysisResponse resp = service.create(create, 1L, "alice", "127.0.0.1");

        ArgumentCaptor<CaseAnalysis> insertCap = ArgumentCaptor.forClass(CaseAnalysis.class);
        verify(caseAnalysisMapper).insert(insertCap.capture());
        assertEquals(new BigDecimal("12000.00"), insertCap.getValue().getAmount());
        assertEquals("UNSIGNED", insertCap.getValue().getContractStatus());
        assertEquals(0, insertCap.getValue().getExtractionConfirmed());
        assertEquals(new BigDecimal("12000.00"), resp.getAmount());
        assertEquals("UNSIGNED", resp.getContractStatus());

        // contractStatus 未传（null）→ 归一化 UNKNOWN
        CaseAnalysisCreateRequest noStatus = new CaseAnalysisCreateRequest();
        noStatus.setTitle("t");
        noStatus.setDomain("LABOR_DISPUTE");
        noStatus.setCaseFacts("f");
        service.create(noStatus, 1L, "alice", "127.0.0.1");
        ArgumentCaptor<CaseAnalysis> insertCap2 = ArgumentCaptor.forClass(CaseAnalysis.class);
        verify(caseAnalysisMapper, times(2)).insert(insertCap2.capture());
        assertEquals("UNKNOWN", insertCap2.getAllValues().get(1).getContractStatus());
        assertNull(insertCap2.getAllValues().get(1).getAmount());
    }

    @Test
    @DisplayName("CRUD-编辑：amount/contractStatus 更新；amount 清空 → 显式 set null；extraction/confirmed 一并重置")
    void update_persistsAmountAndContractStatus_clearsNullAmount() {
        CaseAnalysis dbCa = caseEntity(10L, 1L, "DRAFT", null, 0, new BigDecimal("5000"), "UNKNOWN");
        when(caseAnalysisMapper.selectOne(any())).thenReturn(dbCa);

        // 编辑：amount 5000 → 15000，contractStatus UNKNOWN → SIGNED
        CaseAnalysisUpdateRequest update = new CaseAnalysisUpdateRequest();
        update.setTitle("t");
        update.setDomain("LABOR_DISPUTE");
        update.setCaseFacts("f");
        update.setAmount(new BigDecimal("15000"));
        update.setContractStatus("SIGNED");
        service.update(10L, update, 1L, "alice", "127.0.0.1");
        ArgumentCaptor<CaseAnalysis> updateByIdCap = ArgumentCaptor.forClass(CaseAnalysis.class);
        verify(caseAnalysisMapper).updateById(updateByIdCap.capture());
        assertEquals(new BigDecimal("15000"), updateByIdCap.getValue().getAmount());
        assertEquals("SIGNED", updateByIdCap.getValue().getContractStatus());
        verify(caseAnalysisMapper).update(any(), any()); // 仅 extraction 清空一条（amount 非空无需显式 set null）

        // 编辑：amount 清空（null）→ 显式 set null（绕开 NOT_NULL 字段策略）；contractStatus null → UNKNOWN
        CaseAnalysisUpdateRequest clear = new CaseAnalysisUpdateRequest();
        clear.setTitle("t");
        clear.setDomain("LABOR_DISPUTE");
        clear.setCaseFacts("f");
        service.update(10L, clear, 1L, "alice", "127.0.0.1");
        ArgumentCaptor<AbstractWrapper> wcap = ArgumentCaptor.forClass(AbstractWrapper.class);
        // 三次 update(null, wrapper)：第一次 flow 的 extraction 清空 + 第二次 flow 的 amount 清空 + extraction 清空
        verify(caseAnalysisMapper, times(3)).update(any(), wcap.capture());
        Map<String, Object> amountSet = setColumns(wcap.getAllValues().get(1));
        assertTrue(amountSet.containsKey("amount"));
        assertNull(amountSet.get("amount"));
        Map<String, Object> resetSet = setColumns(wcap.getAllValues().get(2));
        assertNull(resetSet.get("extraction_json"));
        assertEquals(0, resetSet.get("extraction_confirmed"));
    }

    @Test
    @DisplayName("CRUD-列表：amount/contractStatus 透出")
    void list_exposesAmountAndContractStatus() {
        when(caseAnalysisMapper.selectList(any())).thenReturn(List.of(
                caseEntity(1L, 1L, "DRAFT", null, 0, new BigDecimal("12000.00"), "UNSIGNED")));
        List<CaseAnalysisResponse> list = service.listByUser(1L);
        assertEquals(new BigDecimal("12000.00"), list.get(0).getAmount());
        assertEquals("UNSIGNED", list.get(0).getContractStatus());
        assertEquals(0, list.get(0).getExtractionConfirmed());
    }

    // ---- 6. missingInformation 不改变 Java 风险等级 ----

    @Test
    @DisplayName("missingInformation 是规则分析输入而非等级输入：风险结果完全由 Java 规则算法决定")
    void missingInformation_doesNotChangeJavaRiskLevel() {
        // 提取结果带 5 项缺失信息（模拟真实案件样本）
        CaseFactExtraction rich = extraction("摘要", List.of("诉求"),
                List.of("缺口1", "缺口2", "缺口3", "缺口4", "缺口5"));
        CaseAnalysis dbCa = caseEntity(10L, 1L, "DRAFT", validator.toCanonicalJson(rich), 1, null, "UNKNOWN");
        CaseAnalysis stored = caseEntity(10L, 1L, "ANALYZED", validator.toCanonicalJson(rich), 1, null, "UNKNOWN");
        stored.setRiskResultJson("{\"overallLevel\":\"MEDIUM\"}");
        stored.setAnalysisResult("分析说明");
        when(caseAnalysisMapper.selectOne(any())).thenReturn(dbCa, stored);

        CaseRiskResult ruleRisk = new CaseRiskResult();
        ruleRisk.setOverallLevel(CaseRiskResult.RiskLevel.MEDIUM);
        when(riskAnalyzer.analyze(any())).thenReturn(ruleRisk);
        when(riskAnalyzer.toRiskJson(ruleRisk)).thenReturn("{\"overallLevel\":\"MEDIUM\"}");
        when(riskAnalyzer.buildAnalysisResult(any(), eq(ruleRisk))).thenReturn("分析说明");

        CaseAnalysisResponse resp = service.riskAnalysis(10L, 1L, "alice", "127.0.0.1");

        // 输入侧：analyze 收到含 5 项缺口的提取结果（missingInformation 原样透传）
        ArgumentCaptor<CaseFactExtraction> extCap = ArgumentCaptor.forClass(CaseFactExtraction.class);
        verify(riskAnalyzer).analyze(extCap.capture());
        assertEquals(5, extCap.getValue().getMissingInformation().size());
        // 输出侧：等级完全来自规则算法，与缺失信息内容无关
        assertEquals(CaseRiskResult.RiskLevel.MEDIUM, resp.getRisk().getOverallLevel());
        // 落库侧：risk_result_json 即算法输出
        ArgumentCaptor<AbstractWrapper> wcap = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(caseAnalysisMapper).update(any(), wcap.capture());
        assertEquals("{\"overallLevel\":\"MEDIUM\"}", setColumns(wcap.getValue()).get("risk_result_json"));
    }

    // ---- 7. Fix1：案件类型 domain 随确认操作持久化 ----

    @Test
    @DisplayName("Fix1-确认同时持久化案件类型修改：domain 进入 SET 子句、confirmed=1、刷新后仍为新 domain")
    void confirmExtraction_withValidDomain_persistsDomainInSameUpdate() {
        String json = validator.toCanonicalJson(extraction("摘要", List.of("诉求"), List.of()));
        CaseAnalysis dbCa = caseEntity(10L, 1L, "DRAFT", json, 0, null, "UNKNOWN");
        // 确认写库后的刷新读取：domain 已是修改后的新领域
        CaseAnalysis stored = caseEntity(10L, 1L, "DRAFT", json, 1, null, "UNKNOWN");
        stored.setDomain("HOUSE_RENTAL");
        when(caseAnalysisMapper.selectOne(any())).thenReturn(dbCa, stored);

        CaseExtractionUpdateRequest req = new CaseExtractionUpdateRequest();
        req.setExtraction(extraction("摘要", List.of("诉求"), List.of()));
        req.setDomain("HOUSE_RENTAL"); // 原 LABOR_DISPUTE → 另一正式领域
        CaseAnalysisResponse resp = service.confirmExtraction(10L, req, 1L, "alice", "127.0.0.1");

        ArgumentCaptor<AbstractWrapper> cap = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(caseAnalysisMapper).update(any(), cap.capture());
        Map<String, Object> set = setColumns(cap.getValue());
        // 同一次确认更新：domain + extraction_json + confirmed=1
        assertEquals("HOUSE_RENTAL", set.get("domain"));
        assertEquals(json, set.get("extraction_json"));
        assertEquals(1, set.get("extraction_confirmed"));
        // 刷新（get）后仍为新 domain
        assertEquals("HOUSE_RENTAL", resp.getDomain());
        assertEquals(1, resp.getExtractionConfirmed());
    }

    @Test
    @DisplayName("Fix1-确认不携带 domain：SET 子句不含 domain 列（案件类型保持不变）")
    void confirmExtraction_withoutDomain_keepsDomainUntouched() {
        String json = validator.toCanonicalJson(extraction("摘要", List.of("诉求"), List.of()));
        CaseAnalysis dbCa = caseEntity(10L, 1L, "DRAFT", json, 0, null, "UNKNOWN");
        CaseAnalysis stored = caseEntity(10L, 1L, "DRAFT", json, 1, null, "UNKNOWN");
        when(caseAnalysisMapper.selectOne(any())).thenReturn(dbCa, stored);

        CaseExtractionUpdateRequest req = new CaseExtractionUpdateRequest();
        req.setExtraction(extraction("摘要", List.of("诉求"), List.of()));
        service.confirmExtraction(10L, req, 1L, "alice", "127.0.0.1");

        ArgumentCaptor<AbstractWrapper> cap = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(caseAnalysisMapper).update(any(), cap.capture());
        assertFalse(setColumns(cap.getValue()).containsKey("domain"));
    }

    @Test
    @DisplayName("Fix1-非法 domain：400 且无任何写库（服务端纵深防御）")
    void confirmExtraction_invalidDomain_throws400() {
        String json = validator.toCanonicalJson(extraction("摘要", List.of("诉求"), List.of()));
        CaseAnalysis dbCa = caseEntity(10L, 1L, "DRAFT", json, 0, null, "UNKNOWN");
        when(caseAnalysisMapper.selectOne(any())).thenReturn(dbCa);

        CaseExtractionUpdateRequest req = new CaseExtractionUpdateRequest();
        req.setExtraction(extraction("摘要", List.of("诉求"), List.of()));
        req.setDomain("NOT_A_DOMAIN");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirmExtraction(10L, req, 1L, "alice", "127.0.0.1"));
        assertEquals(400, ex.getCode());
        verify(caseAnalysisMapper, never()).update(any(), any());
    }

    // ---- 测试工具 ----

    private static CaseFactExtraction extraction(String summary, List<String> claims, List<String> missing) {
        CaseFactExtraction e = new CaseFactExtraction();
        e.setCaseSummary(summary);
        e.setDisputeFocus("争议焦点");
        e.setClaims(claims);
        e.setKeyDates(List.of(keyDate("入职", "2025年3月")));
        e.setEvidenceItems(List.of(evidence("工资流水", "银行流水")));
        e.setCounterparty(desc("某公司"));
        e.setLocation(desc("北京市"));
        e.setMissingInformation(missing);
        return e;
    }

    private static CaseFactExtraction.KeyDateItem keyDate(String event, String dateText) {
        CaseFactExtraction.KeyDateItem i = new CaseFactExtraction.KeyDateItem();
        i.setEvent(event);
        i.setDateText(dateText);
        return i;
    }

    private static CaseFactExtraction.EvidenceItem evidence(String name, String description) {
        CaseFactExtraction.EvidenceItem i = new CaseFactExtraction.EvidenceItem();
        i.setName(name);
        i.setDescription(description);
        return i;
    }

    private static CaseFactExtraction.DescriptionItem desc(String d) {
        CaseFactExtraction.DescriptionItem i = new CaseFactExtraction.DescriptionItem();
        i.setDescription(d);
        return i;
    }

    private static CaseAnalysis caseEntity(Long id, Long userId, String status, String extractionJson,
                                           Integer confirmed, BigDecimal amount, String contractStatus) {
        CaseAnalysis ca = new CaseAnalysis();
        ca.setId(id);
        ca.setUserId(userId);
        ca.setTitle("未签劳动合同工资纠纷");
        ca.setDomain("LABOR_DISPUTE");
        ca.setCaseFacts("公司未签书面劳动合同");
        ca.setAnalysisStatus(status);
        ca.setExtractionJson(extractionJson);
        ca.setExtractionConfirmed(confirmed);
        ca.setAmount(amount);
        ca.setContractStatus(contractStatus);
        ca.setDeleted(0);
        return ca;
    }

    /**
     * 从 LambdaUpdateWrapper 提取 SET 子句（列名 → 值）。
     * getSqlSet() 形如 "extraction_json=#{ew.paramNameValuePairs.MPGENVAL1},..."，
     * 参数值按 SET 出现顺序写入 paramNameValuePairs（MyBatis-Plus 3.5.7 前缀 MPGENVAL）。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> setColumns(AbstractWrapper<?, ?, ?> wrapper) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String part : wrapper.getSqlSet().split(",")) {
            int eqIdx = part.indexOf('=');
            String col = part.substring(0, eqIdx).trim();
            int refStart = part.indexOf("MPGENVAL") + "MPGENVAL".length();
            String ref = part.substring(refStart, part.indexOf('}'));
            out.put(col, wrapper.getParamNameValuePairs().get("MPGENVAL" + ref));
        }
        return out;
    }
}
