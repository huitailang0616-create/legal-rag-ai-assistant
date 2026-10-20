package com.ailegal.service.impl;

import com.ailegal.dto.CaseFactExtraction;
import com.ailegal.dto.DocumentGenerationDraft;
import com.ailegal.entity.CaseAnalysis;
import com.ailegal.entity.DocumentDraft;
import com.ailegal.exception.BusinessException;
import com.ailegal.mapper.CaseAnalysisMapper;
import com.ailegal.mapper.DocumentDraftMapper;
import com.ailegal.service.CaseFactExtractionValidator;
import com.ailegal.service.DocumentDraftDocxBuilder;
import com.ailegal.service.DocumentGenerationLlmService;
import com.ailegal.service.DocumentGenerationValidator;
import com.ailegal.service.DocumentTemplateService;
import com.ailegal.service.OperationLogService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Final-Requirement-Gaps-C-Fix1 聚焦回归（模块5）：
 * 确认后的 extraction 必须进入 Document Generation 输入（BLOCKER 1 闭环）。
 * buildGenerationPrompt 即真实生成链路输入构建点——其返回值直接作为
 * DocumentGenerationLlmService.generate 的输入；校验器用真实实例（同一严格契约）。
 * 1) confirmed extraction（含人工修改短语）→ 进入 Prompt（caseSummary/claims/evidenceItems）
 * 2) unconfirmed extraction → 不作为用户确认事实进入 Prompt（仍仅原始字段）
 * 3) confirmed 但 JSON 校验不过（防御）→ 跳过确认区块
 * 4) amount 未提供不输出 0 / UNKNOWN 合同情况不推断
 * 5) 原始字段输入不被替换（确认 extraction 是补充而非替代）
 */
@ExtendWith(MockitoExtension.class)
class DocumentDraftServiceImplTest {

    private static final String PHRASE = "2026年7月已通过微信向公司催要工资";
    private static final String CONFIRMED_MARK = "【用户已人工确认的结构化案件信息】";

    @Mock
    private DocumentDraftMapper documentDraftMapper;
    @Mock
    private CaseAnalysisMapper caseAnalysisMapper;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private DocumentGenerationLlmService documentGenerationLlmService;
    @Mock
    private DocumentGenerationValidator documentGenerationValidator;
    @Mock
    private DocumentTemplateService documentTemplateService;
    @Mock
    private DocumentDraftDocxBuilder documentDraftDocxBuilder;

    /** 真实校验器：确认区块只放行能通过严格契约的 extraction（与模块4 同一契约） */
    private static final CaseFactExtractionValidator VALIDATOR = new CaseFactExtractionValidator();

    private DocumentDraftServiceImpl service;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), DocumentDraft.class);
        TableInfoHelper.initTableInfo(
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), CaseAnalysis.class);
    }

    @BeforeEach
    void setUp() {
        service = new DocumentDraftServiceImpl(documentDraftMapper, caseAnalysisMapper,
                operationLogService, documentGenerationLlmService, documentGenerationValidator,
                documentTemplateService, documentDraftDocxBuilder, VALIDATOR);
    }

    @Test
    @DisplayName("Fix1-confirmed：确认后的 extraction（含人工修改短语）进入生成 Prompt，原始字段不被替换")
    void prompt_includesConfirmedExtraction() {
        CaseFactExtraction human = extraction("人工修改后的案件摘要",
                List.of("要求支付拖欠工资", PHRASE),
                List.of("劳动合同原件"));
        CaseAnalysis ca = caseEntity(human, 1, new BigDecimal("12000.00"), "UNSIGNED");

        String prompt = service.buildGenerationPrompt(draft(), ca);

        assertTrue(prompt.contains(CONFIRMED_MARK));
        // 人工修改的关键事实短语（仅存在于确认 extraction）进入 Prompt
        assertTrue(prompt.contains(PHRASE));
        // 确认 extraction 的 caseSummary / claims / evidenceItems 全部进入
        assertTrue(prompt.contains("人工修改后的案件摘要"));
        assertTrue(prompt.contains("要求支付拖欠工资"));
        assertTrue(prompt.contains("工资流水（银行流水）"));
        assertTrue(prompt.contains("入职：2025年3月"));
        // missingInformation 只作为"仍缺失的信息"标注，不混入事实区
        assertTrue(prompt.contains("仍缺失的信息"));
        assertTrue(prompt.contains("劳动合同原件"));
        // 原始用户字段输入未被替换（补充而非替代）
        assertTrue(prompt.contains("公司未签书面劳动合同"));
        // amount / contractStatus 作为已有结构化信息进入
        assertTrue(prompt.contains("12000元"));
        assertTrue(prompt.contains("UNSIGNED"));
    }

    @Test
    @DisplayName("Fix1-unconfirmed：未确认 extraction 不作为确认事实进入 Prompt（仅原始字段）")
    void prompt_excludesUnconfirmedExtraction() {
        CaseFactExtraction human = extraction("人工修改后的案件摘要", List.of(PHRASE), List.of());
        CaseAnalysis ca = caseEntity(human, 0, new BigDecimal("12000.00"), "UNSIGNED");

        String prompt = service.buildGenerationPrompt(draft(), ca);

        assertFalse(prompt.contains(CONFIRMED_MARK));
        // confirmed-only 短语（只存在于 extraction）不得作为人工确认事实进入
        assertFalse(prompt.contains(PHRASE));
        assertFalse(prompt.contains("人工修改后的案件摘要"));
        // 原始字段输入保持
        assertTrue(prompt.contains("公司未签书面劳动合同"));
    }

    @Test
    @DisplayName("Fix1-防御：confirmed 但 extraction JSON 校验不过 → 确认区块不进入 Prompt")
    void prompt_skipsConfirmedBlockWhenJsonInvalid() {
        CaseAnalysis ca = caseEntity(null, 1, null, null);
        ca.setExtractionJson("not-a-json");

        String prompt = service.buildGenerationPrompt(draft(), ca);

        assertFalse(prompt.contains(CONFIRMED_MARK));
        assertFalse(prompt.contains("案件摘要"));
        assertTrue(prompt.contains("公司未签书面劳动合同"));
    }

    @Test
    @DisplayName("Fix1-金额/合同防编造：amount 未提供不输出 0 元，UNKNOWN 合同情况不推断已签/未签")
    void prompt_amountNullAndUnknownContract_noFabrication() {
        CaseAnalysis ca = caseEntity(null, 0, null, "UNKNOWN");

        String prompt = service.buildGenerationPrompt(draft(), ca);

        assertTrue(prompt.contains("涉及金额（元，用户填写）：未提供"));
        assertFalse(prompt.contains("0元"));
        // 合同情况行原样标注未知（原始 caseFacts 中的"未签书面劳动合同"是用户事实，不在此断言）
        assertTrue(prompt.contains("合同情况（用户填写）：UNKNOWN（合同情况未知）"));
    }

    // ---- 5B generate extraction_confirmed gate ----

    @Test
    @DisplayName("A：extractionConfirmed=0 → 拒绝生成、不调LLM、不更新草稿")
    void generate_rejectsWhenExtractionConfirmedZero() {
        DocumentDraft draft = draftWithCaseId();
        when(documentDraftMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(draft);
        when(documentTemplateService.isCompatible(draft.getDocumentType(), draft.getDomain())).thenReturn(true);

        CaseAnalysis ca = caseEntity(null, 0, null, null);
        when(caseAnalysisMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ca);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.generate(1L, 1L, "user", "127.0.0.1"));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("确认"));

        verify(documentGenerationLlmService, never()).generate(any());
        verify(documentDraftMapper, never()).update(ArgumentMatchers.isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("B：extractionConfirmed=null → 拒绝生成、不调LLM、不更新草稿")
    void generate_rejectsWhenExtractionConfirmedNull() {
        DocumentDraft draft = draftWithCaseId();
        when(documentDraftMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(draft);
        when(documentTemplateService.isCompatible(draft.getDocumentType(), draft.getDomain())).thenReturn(true);

        CaseAnalysis ca = caseEntity(null, null, null, null);
        when(caseAnalysisMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ca);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.generate(1L, 1L, "user", "127.0.0.1"));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("确认"));

        verify(documentGenerationLlmService, never()).generate(any());
        verify(documentDraftMapper, never()).update(ArgumentMatchers.isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("C：extractionConfirmed=1 → 正常生成、调LLM、更新草稿")
    void generate_succeedsWhenExtractionConfirmed() {
        DocumentDraft draft = draftWithCaseId();
        DocumentDraft updatedDraft = draftWithCaseId();
        updatedDraft.setContent("劳动仲裁申请书\n事实描述\n理由说明");
        updatedDraft.setGenerationStatus("GENERATED");
        when(documentDraftMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(draft, updatedDraft);
        when(documentTemplateService.isCompatible(draft.getDocumentType(), draft.getDomain())).thenReturn(true);

        CaseAnalysis ca = caseEntity(null, 1, null, null);
        when(caseAnalysisMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ca);

        String llmRaw = "{\"factsDraft\":\"事实描述\",\"reasoningDraft\":\"理由说明\"}";
        when(documentGenerationLlmService.generate(any())).thenReturn(llmRaw);

        DocumentGenerationDraft parsed = new DocumentGenerationDraft();
        parsed.setFactsDraft("事实描述");
        parsed.setReasoningDraft("理由说明");
        when(documentGenerationValidator.parseAndValidate(llmRaw)).thenReturn(parsed);

        String rendered = "劳动仲裁申请书\n事实描述\n理由说明";
        when(documentTemplateService.render(any(), any())).thenReturn(rendered);

        when(documentDraftMapper.update(ArgumentMatchers.isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        var resp = service.generate(1L, 1L, "user", "127.0.0.1");

        assertEquals("GENERATED", resp.getGenerationStatus());
        assertEquals(rendered, resp.getContent());

        verify(documentGenerationLlmService).generate(any());
        verify(documentDraftMapper).update(ArgumentMatchers.isNull(), any(LambdaUpdateWrapper.class));
    }

    // ---- 测试工具 ----

    private static DocumentDraft draft() {
        DocumentDraft d = new DocumentDraft();
        d.setDocumentType("LABOR_ARBITRATION_APPLICATION");
        d.setDomain("LABOR_DISPUTE");
        return d;
    }

    private static DocumentDraft draftWithCaseId() {
        DocumentDraft d = draft();
        d.setId(1L);
        d.setUserId(1L);
        d.setCaseAnalysisId(10L);
        d.setContent(null);
        d.setGenerationStatus("NOT_GENERATED");
        d.setDeleted(0);
        return d;
    }

    /** 原始字段固定为不含人工短语的事实（与 PHRASE 区分），确认 extraction 单独携带 */
    private static CaseAnalysis caseEntity(CaseFactExtraction extraction, Integer confirmed,
                                           BigDecimal amount, String contractStatus) {
        CaseAnalysis ca = new CaseAnalysis();
        ca.setDomain("LABOR_DISPUTE");
        ca.setTitle("未签劳动合同工资纠纷");
        ca.setCaseFacts("公司未签书面劳动合同");
        ca.setClaims("要求支付拖欠工资");
        ca.setKeyDates("2025年3月入职，2026年6月离职");
        ca.setEvidenceDescription("工资流水、聊天记录");
        ca.setCounterpartyDescription("某公司");
        ca.setLocationDescription("北京市");
        ca.setAmount(amount);
        ca.setContractStatus(contractStatus);
        ca.setExtractionConfirmed(confirmed);
        ca.setExtractionJson(extraction == null ? null : VALIDATOR.toCanonicalJson(extraction));
        return ca;
    }

    private static CaseFactExtraction extraction(String summary, List<String> claims, List<String> missing) {
        CaseFactExtraction e = new CaseFactExtraction();
        e.setCaseSummary(summary);
        e.setDisputeFocus("争议焦点");
        e.setClaims(claims);
        CaseFactExtraction.KeyDateItem kd = new CaseFactExtraction.KeyDateItem();
        kd.setEvent("入职");
        kd.setDateText("2025年3月");
        e.setKeyDates(List.of(kd));
        CaseFactExtraction.EvidenceItem ev = new CaseFactExtraction.EvidenceItem();
        ev.setName("工资流水");
        ev.setDescription("银行流水");
        e.setEvidenceItems(List.of(ev));
        CaseFactExtraction.DescriptionItem cp = new CaseFactExtraction.DescriptionItem();
        cp.setDescription("某公司");
        e.setCounterparty(cp);
        CaseFactExtraction.DescriptionItem loc = new CaseFactExtraction.DescriptionItem();
        loc.setDescription("北京市");
        e.setLocation(loc);
        e.setMissingInformation(missing);
        return e;
    }
}