package com.ailegal.service.impl;

import com.ailegal.dto.CaseAnalysisResponse;
import com.ailegal.dto.CaseFactExtraction;
import com.ailegal.dto.ConsultCitation;
import com.ailegal.dto.ConsultResponse;
import com.ailegal.dto.LegalArticleDocument;
import com.ailegal.dto.QueryUnderstandingResult;
import com.ailegal.dto.RagRetrieveTestResponse;
import com.ailegal.dto.RagTop5Hit;
import com.ailegal.exception.BusinessException;
import com.ailegal.service.CaseAnalysisService;
import com.ailegal.service.ConsultRuntimeRecordService;
import com.ailegal.service.ConsultationHistoryService;
import com.ailegal.service.LegalKnowledgeService;
import com.ailegal.service.LlmService;
import com.ailegal.service.OperationLogService;
import com.ailegal.service.QueryUnderstandingService;
import com.ailegal.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Demo-Reliability-1: Query Understanding technical failure degrades to the locked
 * Basic RAG pipeline instead of killing /api/consult; NON_LEGAL_INTENT and
 * UNSUPPORTED_DOMAIN (normal QU outcomes) must NOT trigger the fallback.
 */
@ExtendWith(MockitoExtension.class)
class ConsultServiceImplTest {

    @Mock
    private RagService ragService;
    @Mock
    private LegalKnowledgeService legalKnowledgeService;
    @Mock
    private LlmService llmService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private QueryUnderstandingService queryUnderstandingService;
    @Mock
    private ConsultationHistoryService consultationHistoryService;
    @Mock
    private ConsultRuntimeRecordService consultRuntimeRecordService;
    @Mock
    private CaseAnalysisService caseAnalysisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConsultServiceImpl consultService;

    private static final String QUESTION = "老板让我无偿加班，不加班就扣工资，这种情况合法吗？";
    /** 人工确认后的 extraction 中存在的具体事实（Final-Requirement-Gaps-G 联动实证锚点） */
    private static final String CONFIRMED_FACT = "2026年7月已通过微信向公司催要工资";

    @BeforeEach
    void setUp() {
        consultService = new ConsultServiceImpl(ragService, legalKnowledgeService, llmService,
                operationLogService, queryUnderstandingService, consultationHistoryService,
                consultRuntimeRecordService, caseAnalysisService, objectMapper, "deepseek-v4-pro");
    }

    private RagRetrieveTestResponse passResponse() {
        RagTop5Hit hit = new RagTop5Hit(1L, "中华人民共和国劳动法", "第四十四条",
                0.8, 0.5, 1.0, 0.78, List.of("加班"));
        return new RagRetrieveTestResponse("LABOR_DISPUTE", List.of("加班"), List.of(),
                List.of(hit), true, "PASS");
    }

    private void stubOrderedArticle() {
        LegalArticleDocument article = new LegalArticleDocument();
        article.setArticleId(1L);
        article.setDocumentId(1L);
        article.setDocumentName("中华人民共和国劳动法");
        article.setArticleNumber("第四十四条");
        article.setContent("延长工作时间的，应当支付相应的加班工资。");
        article.setSource("法律法规库");
        when(legalKnowledgeService.selectValidArticlesWithDocuments(any())).thenReturn(List.of(article));
    }

    @Test
    @DisplayName("QU 429 技术失败 -> 降级 Basic RAG -> 正常回答 + citations，不抛\"问题理解失败\"")
    void qu429Failure_fallsBackToBasicRag_andAnswers() {
        when(queryUnderstandingService.understand(any()))
                .thenThrow(new BusinessException(500, "问题理解失败:理解服务繁忙(429)"));
        when(ragService.retrieveTest(eq(QUESTION), any(), any(), any())).thenReturn(passResponse());
        stubOrderedArticle();
        when(llmService.chat(any(), any()))
                .thenReturn("根据法律依据……以上内容仅供法律信息参考，不构成正式法律意见。");

        ConsultResponse resp = consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1");

        assertEquals("根据法律依据……以上内容仅供法律信息参考，不构成正式法律意见。", resp.getAnswer());
        assertFalse(resp.isEvidenceInsufficient());
        assertEquals("PASS", resp.getRelevanceReason());
        assertNotNull(resp.getCitations());
        assertEquals(1, resp.getCitations().size());
        ConsultCitation c = resp.getCitations().get(0);
        assertEquals(1L, c.getArticleId());
        assertEquals("中华人民共和国劳动法", c.getDocumentName());
        // fallback must go through the Basic public entry, never Enhanced
        verify(ragService).retrieveTest(eq(QUESTION), any(), any(), any());
        verify(ragService, never()).retrieveEnhanced(any(), any(), any(), any());
        // successful business response is saved to history
        verify(consultationHistoryService).save(eq(1L), eq(QUESTION), any(ConsultResponse.class));
        // runtime record: fallback 成功 = SUCCESS + BASIC_FALLBACK + 降级原因（绝不算 FAILED）
        verify(consultRuntimeRecordService).saveQuietly(argThat(r ->
                "SUCCESS".equals(r.getExecutionStatus())
                        && "BASIC_FALLBACK".equals(r.getRetrievalMode())
                        && r.getFallbackReason() != null
                        && r.getErrorCode() == null));
    }

    @Test
    @DisplayName("QU 超时技术失败 -> 同样降级 Basic RAG")
    void quTimeoutFailure_fallsBackToBasicRag() {
        when(queryUnderstandingService.understand(any()))
                .thenThrow(new BusinessException(500, "问题理解失败:理解服务调用超时"));
        when(ragService.retrieveTest(eq(QUESTION), any(), any(), any())).thenReturn(passResponse());
        stubOrderedArticle();
        when(llmService.chat(any(), any())).thenReturn("回答");

        ConsultResponse resp = consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1");

        assertEquals("回答", resp.getAnswer());
        verify(ragService).retrieveTest(eq(QUESTION), any(), any(), any());
    }

    @Test
    @DisplayName("QU 5xx 技术失败 -> 降级 Basic RAG")
    void qu5xxFailure_fallsBackToBasicRag() {
        when(queryUnderstandingService.understand(any()))
                .thenThrow(new BusinessException(500, "问题理解失败:理解服务返回错误状态"));
        when(ragService.retrieveTest(eq(QUESTION), any(), any(), any())).thenReturn(passResponse());
        stubOrderedArticle();
        when(llmService.chat(any(), any())).thenReturn("回答");

        ConsultResponse resp = consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1");

        assertEquals("回答", resp.getAnswer());
        verify(ragService).retrieveTest(eq(QUESTION), any(), any(), any());
    }

    @Test
    @DisplayName("fallback 后 Basic gate 拒绝 -> 正常安全拒绝，不调用 Answer LLM")
    void basicGateReject_onFallback_returnsSafeRejection() {
        when(queryUnderstandingService.understand(any()))
                .thenThrow(new BusinessException(500, "问题理解失败:理解服务繁忙(429)"));
        RagTop5Hit hit = new RagTop5Hit(1L, "劳动法", "第一条", 0.5, 0.2, 0.0, 0.45, List.of());
        when(ragService.retrieveTest(eq(QUESTION), any(), any(), any()))
                .thenReturn(new RagRetrieveTestResponse("LABOR_DISPUTE", List.of(), List.of(),
                        List.of(hit), false, "LOW_RELEVANCE"));

        ConsultResponse resp = consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1");

        assertTrue(resp.isEvidenceInsufficient());
        assertEquals("KNOWLEDGE_GAP", resp.getRelevanceReason());
        assertTrue(resp.getAnswer().contains("当前知识库未检索到足够直接的法律依据"));
        verify(llmService, never()).chat(any(), any());
        // normal business rejection is still saved to history
        verify(consultationHistoryService).save(eq(1L), eq(QUESTION), any(ConsultResponse.class));
    }

    @Test
    @DisplayName("QU 正常返回 legalIntent=false -> NON_LEGAL_INTENT，绝不 fallback Basic")
    void nonLegalIntent_keepsRejection_noFallback() {
        when(queryUnderstandingService.understand(any()))
                .thenReturn(new QueryUnderstandingResult(false, "UNKNOWN", "", List.of()));

        ConsultResponse resp = consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1");

        assertEquals("NON_LEGAL_INTENT", resp.getRelevanceReason());
        assertTrue(resp.getAnswer().contains("似乎不是法律咨询问题"));
        verify(ragService, never()).retrieveTest(any(), any(), any(), any());
        verify(ragService, never()).retrieveEnhanced(any(), any(), any(), any());
        verify(llmService, never()).chat(any(), any());
    }

    @Test
    @DisplayName("QU 正常返回 supportedDomain=UNKNOWN -> UNSUPPORTED_DOMAIN，绝不 fallback Basic")
    void unsupportedDomain_keepsRejection_noFallback() {
        when(queryUnderstandingService.understand(any()))
                .thenReturn(new QueryUnderstandingResult(true, "UNKNOWN", "问题", List.of("事实")));

        ConsultResponse resp = consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1");

        assertEquals("UNSUPPORTED_DOMAIN", resp.getRelevanceReason());
        assertTrue(resp.getAnswer().contains("超出本系统支持"));
        verify(ragService, never()).retrieveTest(any(), any(), any(), any());
        verify(ragService, never()).retrieveEnhanced(any(), any(), any(), any());
        verify(llmService, never()).chat(any(), any());
    }

    @Test
    @DisplayName("QU 正常但 RAG 阶段失败 -> 直接失败，不降级 Basic")
    void ragStageFailure_doesNotFallback() {
        when(queryUnderstandingService.understand(any()))
                .thenReturn(new QueryUnderstandingResult(true, "LABOR_DISPUTE",
                        "无偿加班是否合法", List.of("无偿加班")));
        when(ragService.retrieveEnhanced(any(), any(), any(), any()))
                .thenThrow(new BusinessException(500, "检索失败:Qdrant 检索异常"));

        assertThrows(BusinessException.class,
                () -> consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1"));
        verify(ragService, never()).retrieveTest(any(), any(), any(), any());
        verify(llmService, never()).chat(any(), any());
    }

    @Test
    @DisplayName("A(检索前): QU 技术失败 + Basic fallback 也失败 -> FAILED 落库（BASIC_FALLBACK + []，非 NO_RETRIEVAL）")
    void quFailure_fallbackAlsoFails_persistsFailedRecord() {
        when(queryUnderstandingService.understand(any()))
                .thenThrow(new BusinessException(500, "问题理解失败:理解服务繁忙(429)"));
        when(ragService.retrieveTest(eq(QUESTION), any(), any(), any()))
                .thenThrow(new BusinessException(500, "检索失败:Qdrant 检索异常"));

        assertThrows(BusinessException.class,
                () -> consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1"));

        // fallback 被真实尝试过 -> retrieval_mode=BASIC_FALLBACK（真实执行路径），NOT NULL 列全部可落库
        verify(consultRuntimeRecordService).saveQuietly(argThat(r ->
                "FAILED".equals(r.getExecutionStatus())
                        && "QDRANT_ERROR".equals(r.getErrorCode())
                        && "BASIC_FALLBACK".equals(r.getRetrievalMode())
                        && "UNKNOWN".equals(r.getDomain())
                        && "[]".equals(r.getCitationsJson())
                        && "[]".equals(r.getRetrievedArticleIds())
                        && r.getAnswer() == null
                        && QUESTION.equals(r.getQuestion())));
        verify(consultationHistoryService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("C/D(检索后): Answer LLM 超时 -> FAILED 落库（TIMEOUT + 真实 domain + []）")
    void answerLlmTimeout_persistsFailedRecord() {
        when(queryUnderstandingService.understand(any()))
                .thenReturn(new QueryUnderstandingResult(true, "LABOR_DISPUTE",
                        "公司拖欠工资怎么办", List.of("拖欠工资")));
        when(ragService.retrieveEnhanced(any(), any(), any(), any())).thenReturn(passResponse());
        stubOrderedArticle();
        when(llmService.chat(any(), any()))
                .thenThrow(new BusinessException(500, "模型服务调用超时"));

        assertThrows(BusinessException.class,
                () -> consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1"));

        verify(consultRuntimeRecordService).saveQuietly(argThat(r ->
                "FAILED".equals(r.getExecutionStatus())
                        && "TIMEOUT".equals(r.getErrorCode())
                        && "ENHANCED_RAG".equals(r.getRetrievalMode())
                        && "LABOR_DISPUTE".equals(r.getDomain())
                        && "[]".equals(r.getCitationsJson())
                        && "[]".equals(r.getRetrievedArticleIds())
                        && r.getAnswer() == null
                        && QUESTION.equals(r.getQuestion())));
        verify(consultationHistoryService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("C: QU 已识别 LABOR_DISPUTE -> 后续 RAG failure -> FAILED domain 保留 LABOR_DISPUTE（不退化 UNKNOWN）")
    void quRecognisedLaborDomain_ragFailure_keepsDomain() {
        when(queryUnderstandingService.understand(any()))
                .thenReturn(new QueryUnderstandingResult(true, "LABOR_DISPUTE",
                        "公司拖欠工资怎么办", List.of("拖欠工资")));
        when(ragService.retrieveEnhanced(any(), any(), any(), any()))
                .thenThrow(new BusinessException(500, "检索失败:Qdrant 检索异常"));

        assertThrows(BusinessException.class,
                () -> consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1"));

        verify(consultRuntimeRecordService).saveQuietly(argThat(r ->
                "FAILED".equals(r.getExecutionStatus())
                        && "QDRANT_ERROR".equals(r.getErrorCode())
                        && "LABOR_DISPUTE".equals(r.getDomain())
                        && "ENHANCED_RAG".equals(r.getRetrievalMode())
                        && "[]".equals(r.getCitationsJson())
                        && "[]".equals(r.getRetrievedArticleIds())));
    }

    @Test
    @DisplayName("D: QU 已识别 HOUSE_RENTAL -> Answer timeout -> FAILED domain 保留 HOUSE_RENTAL")
    void quRecognisedRentalDomain_answerTimeout_keepsDomain() {
        when(queryUnderstandingService.understand(any()))
                .thenReturn(new QueryUnderstandingResult(true, "HOUSE_RENTAL",
                        "房东不退押金怎么办", List.of("押金")));
        RagTop5Hit hit = new RagTop5Hit(2L, "中华人民共和国民法典", "第七百一十条", 0.8, 0.5, 1.0, 0.78, List.of("押金"));
        when(ragService.retrieveEnhanced(any(), any(), any(), any()))
                .thenReturn(new RagRetrieveTestResponse("HOUSE_RENTAL", List.of("押金"), List.of(),
                        List.of(hit), true, "PASS"));
        LegalArticleDocument article = new LegalArticleDocument();
        article.setArticleId(2L);
        article.setDocumentId(1L);
        article.setDocumentName("中华人民共和国民法典");
        article.setArticleNumber("第七百一十条");
        article.setContent("租赁物在承租人按照租赁合同占有期限内发生所有权变动的，不影响租赁合同的效力。");
        article.setSource("法律法规库");
        when(legalKnowledgeService.selectValidArticlesWithDocuments(any())).thenReturn(List.of(article));
        when(llmService.chat(any(), any()))
                .thenThrow(new BusinessException(500, "模型服务调用超时"));

        assertThrows(BusinessException.class,
                () -> consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1"));

        verify(consultRuntimeRecordService).saveQuietly(argThat(r ->
                "FAILED".equals(r.getExecutionStatus())
                        && "TIMEOUT".equals(r.getErrorCode())
                        && "HOUSE_RENTAL".equals(r.getDomain())
                        && "ENHANCED_RAG".equals(r.getRetrievalMode())
                        && "[]".equals(r.getCitationsJson())
                        && "[]".equals(r.getRetrievedArticleIds())
                        && r.getAnswer() == null));
        verify(consultationHistoryService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("F: QU 技术失败 -> Basic fallback 已确定 HOUSE_RENTAL -> Answer timeout -> FAILED 保存 Basic domain")
    void quFailure_basicFallbackDeterminedDomain_answerTimeout_keepsBasicDomain() {
        when(queryUnderstandingService.understand(any()))
                .thenThrow(new BusinessException(500, "问题理解失败:理解服务繁忙(429)"));
        RagTop5Hit hit = new RagTop5Hit(2L, "中华人民共和国民法典", "第七百一十条", 0.8, 0.5, 1.0, 0.78, List.of("押金"));
        when(ragService.retrieveTest(eq(QUESTION), any(), any(), any()))
                .thenReturn(new RagRetrieveTestResponse("HOUSE_RENTAL", List.of("押金"), List.of(),
                        List.of(hit), true, "PASS"));
        LegalArticleDocument article = new LegalArticleDocument();
        article.setArticleId(2L);
        article.setDocumentId(1L);
        article.setDocumentName("中华人民共和国民法典");
        article.setArticleNumber("第七百一十条");
        article.setContent("租赁物在承租人按照租赁合同占有期限内发生所有权变动的，不影响租赁合同的效力。");
        article.setSource("法律法规库");
        when(legalKnowledgeService.selectValidArticlesWithDocuments(any())).thenReturn(List.of(article));
        when(llmService.chat(any(), any()))
                .thenThrow(new BusinessException(500, "模型服务调用超时"));

        assertThrows(BusinessException.class,
                () -> consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1"));

        verify(consultRuntimeRecordService).saveQuietly(argThat(r ->
                "FAILED".equals(r.getExecutionStatus())
                        && "TIMEOUT".equals(r.getErrorCode())
                        && "HOUSE_RENTAL".equals(r.getDomain())
                        && "BASIC_FALLBACK".equals(r.getRetrievalMode())
                        && "[]".equals(r.getCitationsJson())
                        && "[]".equals(r.getRetrievedArticleIds())
                        && r.getAnswer() == null));
        verify(consultationHistoryService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("业务失败 -> 运行记录写 FAILED + errorCode，consultation_history 不新增")
    void failure_writesRuntimeFailed_butNotHistory() {
        when(queryUnderstandingService.understand(any()))
                .thenReturn(new QueryUnderstandingResult(true, "LABOR_DISPUTE",
                        "无偿加班是否合法", List.of("无偿加班")));
        when(ragService.retrieveEnhanced(any(), any(), any(), any()))
                .thenThrow(new BusinessException(500, "检索失败:Qdrant 检索异常"));

        assertThrows(BusinessException.class,
                () -> consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1"));

        // telemetry: FAILED 记录 + 短错误码；用户可见历史绝不被技术失败污染
        verify(consultRuntimeRecordService).saveQuietly(argThat(r ->
                "FAILED".equals(r.getExecutionStatus())
                        && "QDRANT_ERROR".equals(r.getErrorCode())
                        && r.getAnswer() == null
                        && QUESTION.equals(r.getQuestion())
                        // Runtime-Hotfix-1: NOT NULL 列必须可落库 —— citationsJson/retrievedArticleIds 为 [] 而非 null
                        && "[]".equals(r.getCitationsJson())
                        && "[]".equals(r.getRetrievedArticleIds())
                        // Runtime-Hotfix-1-Fix1: QU 已识别 LABOR_DISPUTE，RAG 失败后 domain 保留真实领域
                        && "LABOR_DISPUTE".equals(r.getDomain())
                        && "ENHANCED_RAG".equals(r.getRetrievalMode())));
        verify(consultationHistoryService, never()).save(any(), any(), any());
    }

    // ============ Final-Requirement-Gaps-G: linked confirmed case consult ============

    /** 本人已确认案件（extraction_confirmed=1，extraction 含人工确认事实） */
    private CaseAnalysisResponse confirmedCase() {
        CaseAnalysisResponse ca = new CaseAnalysisResponse();
        ca.setId(1L);
        ca.setTitle("未签劳动合同工资纠纷");
        ca.setDomain("LABOR_DISPUTE");
        ca.setExtractionConfirmed(1);
        ca.setAmount(new BigDecimal("12000"));
        ca.setContractStatus("UNSIGNED");
        CaseFactExtraction ext = new CaseFactExtraction();
        ext.setCaseSummary(CONFIRMED_FACT + "，公司未支付。2026年5月起公司连续两个月未发工资。");
        ext.setDisputeFocus("公司拖欠工资且未签书面劳动合同");
        ext.setClaims(List.of("要求支付拖欠工资12000元", "支付未签合同二倍工资差额"));
        ext.setKeyDates(List.of(
                keyDate("入职", "2025年3月"),
                keyDate("催要工资", "2026年7月")));
        ext.setEvidenceItems(List.of(evidence("微信催要记录", "2026年7月催要工资的微信聊天记录")));
        CaseFactExtraction.DescriptionItem counterparty = new CaseFactExtraction.DescriptionItem();
        counterparty.setDescription("某科技有限公司");
        ext.setCounterparty(counterparty);
        CaseFactExtraction.DescriptionItem location = new CaseFactExtraction.DescriptionItem();
        location.setDescription("北京市朝阳区");
        ext.setLocation(location);
        ext.setMissingInformation(List.of("未提供具体入职日期"));
        ca.setExtraction(ext);
        return ca;
    }

    private static CaseFactExtraction.KeyDateItem keyDate(String event, String dateText) {
        CaseFactExtraction.KeyDateItem k = new CaseFactExtraction.KeyDateItem();
        k.setEvent(event);
        k.setDateText(dateText);
        return k;
    }

    private static CaseFactExtraction.EvidenceItem evidence(String name, String description) {
        CaseFactExtraction.EvidenceItem e = new CaseFactExtraction.EvidenceItem();
        e.setName(name);
        e.setDescription(description);
        return e;
    }

    /** 成功链路公共 stub：QU 正常、RAG PASS、Top5 命中、LLM 正常回答 */
    private void stubLinkedSuccessPath() {
        when(queryUnderstandingService.understand(any()))
                .thenReturn(new QueryUnderstandingResult(true, "LABOR_DISPUTE",
                        "公司拖欠工资怎么办", List.of("拖欠工资")));
        when(ragService.retrieveEnhanced(any(), any(), any(), any())).thenReturn(passResponse());
        stubOrderedArticle();
        when(llmService.chat(any(), any()))
                .thenReturn("根据依据1……以上内容仅供法律信息参考，不构成正式法律意见。");
    }

    @Test
    @DisplayName("A: 关联本人已确认案件 -> 上下文成功加载并进入 QU 输入与 Answer user prompt")
    void linkedConsult_confirmedCase_contextLoaded() {
        when(caseAnalysisService.get(1L, 1L)).thenReturn(confirmedCase());
        stubLinkedSuccessPath();

        ConsultResponse resp = consultService.consult(QUESTION, 1L, 1L, "user", "127.0.0.1");

        ArgumentCaptor<String> quCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryUnderstandingService).understand(quCaptor.capture());
        assertTrue(quCaptor.getValue().contains("【案件背景】"));
        assertTrue(quCaptor.getValue().contains("【案件摘要】"));
        assertTrue(quCaptor.getValue().contains("【待补充信息】（以下事项在本案中尚未提供或尚未明确，是信息缺口，不是既定事实）"));
        assertTrue(quCaptor.getValue().contains(CONFIRMED_FACT));
        assertTrue(quCaptor.getValue().endsWith(QUESTION));
        // Answer：case 变体 system prompt（含"不是法律依据"grounding 规则）+ user prompt 含案件背景与原始问题
        verify(llmService).chat(argThat(sp -> sp.contains("不是法律依据")),
                argThat(up -> up.contains("【案件背景】") && up.contains(QUESTION)));
        assertEquals("PASS", resp.getRelevanceReason());
    }

    @Test
    @DisplayName("B: 关联未确认案件 -> 业务拒绝 400 CASE_EXTRACTION_NOT_CONFIRMED，不进入 QU/Answer")
    void linkedConsult_unconfirmedCase_businessReject() {
        CaseAnalysisResponse unconfirmed = confirmedCase();
        unconfirmed.setExtractionConfirmed(0);
        when(caseAnalysisService.get(1L, 1L)).thenReturn(unconfirmed);

        BusinessException e = assertThrows(BusinessException.class,
                () -> consultService.consult(QUESTION, 1L, 1L, "user", "127.0.0.1"));

        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("CASE_EXTRACTION_NOT_CONFIRMED"));
        verify(queryUnderstandingService, never()).understand(any());
        verify(llmService, never()).chat(any(), any());
        // telemetry: FAILED + 稳定短错误码
        verify(consultRuntimeRecordService).saveQuietly(argThat(r ->
                "FAILED".equals(r.getExecutionStatus())
                        && "CASE_NOT_CONFIRMED".equals(r.getErrorCode())
                        // Runtime-Hotfix-1: 检索前失败 -> NO_RETRIEVAL + UNKNOWN + []（NOT NULL 可落库）
                        && "NO_RETRIEVAL".equals(r.getRetrievalMode())
                        && "UNKNOWN".equals(r.getDomain())
                        && "[]".equals(r.getCitationsJson())
                        && "[]".equals(r.getRetrievedArticleIds())
                        && r.getAnswer() == null));
    }

    @Test
    @DisplayName("C: 他人案件 -> ownership 拒绝 404（getOwn 统一 404，不泄露存在性）")
    void linkedConsult_otherUsersCase_ownershipReject() {
        when(caseAnalysisService.get(1L, 1L)).thenThrow(new BusinessException(404, "案件不存在"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> consultService.consult(QUESTION, 1L, 1L, "user", "127.0.0.1"));

        assertEquals(404, e.getCode());
        verify(queryUnderstandingService, never()).understand(any());
        verify(llmService, never()).chat(any(), any());
        // Runtime-Hotfix-1-Fix1: 404 ownership 语义不变，但 runtime telemetry 不得误分类为 PROVIDER_ERROR
        verify(consultRuntimeRecordService).saveQuietly(argThat(r ->
                "FAILED".equals(r.getExecutionStatus())
                        && "CASE_NOT_FOUND".equals(r.getErrorCode())
                        && !"PROVIDER_ERROR".equals(r.getErrorCode())));
    }

    @Test
    @DisplayName("D: 不存在的案件 -> 拒绝 404 + CASE_NOT_FOUND")
    void linkedConsult_missingCase_reject() {
        when(caseAnalysisService.get(999L, 1L)).thenThrow(new BusinessException(404, "案件不存在"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> consultService.consult(QUESTION, 999L, 1L, "user", "127.0.0.1"));

        assertEquals(404, e.getCode());
        verify(consultRuntimeRecordService).saveQuietly(argThat(r ->
                "FAILED".equals(r.getExecutionStatus())
                        && "CASE_NOT_FOUND".equals(r.getErrorCode())
                        && "NO_RETRIEVAL".equals(r.getRetrievalMode())));
    }

    @Test
    @DisplayName("E: confirmed=1 但 extraction 缺失（脏数据/解析失败）-> 安全拒绝 400")
    void linkedConsult_confirmedButExtractionNull_safeReject() {
        CaseAnalysisResponse broken = confirmedCase();
        broken.setExtraction(null);
        when(caseAnalysisService.get(1L, 1L)).thenReturn(broken);

        BusinessException e = assertThrows(BusinessException.class,
                () -> consultService.consult(QUESTION, 1L, 1L, "user", "127.0.0.1"));

        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("CASE_EXTRACTION_NOT_CONFIRMED"));
    }

    @Test
    @DisplayName("F: 关联咨询的 QU 输入包含人工确认的 extraction 事实（不含则回归）")
    void linkedConsult_quInputContainsConfirmedFacts() {
        when(caseAnalysisService.get(1L, 1L)).thenReturn(confirmedCase());
        stubLinkedSuccessPath();

        consultService.consult(QUESTION, 1L, 1L, "user", "127.0.0.1");

        ArgumentCaptor<String> quCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryUnderstandingService).understand(quCaptor.capture());
        String quInput = quCaptor.getValue();
        // 人工确认后的案件事实必须进入内部输入（后端真正使用了案件信息，而非只做前端跳转）
        assertTrue(quInput.contains(CONFIRMED_FACT));
        assertTrue(quInput.contains("微信催要记录"));
        assertTrue(quInput.contains("某科技有限公司"));
        assertTrue(quInput.contains("未签合同"));
        assertTrue(quInput.contains("12000 元"));
    }

    @Test
    @DisplayName("G: 普通咨询（无 caseAnalysisId）-> QU 输入即原始问题，不拼接案件上下文")
    void normalConsult_noCaseId_noContextInjected() {
        stubLinkedSuccessPath();

        consultService.consult(QUESTION, null, 1L, "user", "127.0.0.1");

        verify(queryUnderstandingService).understand(eq(QUESTION));
        verify(caseAnalysisService, never()).get(any(), any());
        // Answer 仍使用全局 SYSTEM_PROMPT（无 case 规则），user prompt 不含案件背景
        verify(llmService).chat(argThat(sp -> !sp.contains("不是法律依据")),
                argThat(up -> !up.contains("【案件背景】")));
    }

    @Test
    @DisplayName("H: 关联咨询的 consultation_history 保存用户原始问题（不保存拼接上下文）")
    void linkedConsult_historySavesOriginalQuestion() {
        when(caseAnalysisService.get(1L, 1L)).thenReturn(confirmedCase());
        stubLinkedSuccessPath();

        consultService.consult(QUESTION, 1L, 1L, "user", "127.0.0.1");

        verify(consultationHistoryService).save(eq(1L), eq(QUESTION), any(ConsultResponse.class));
    }

    @Test
    @DisplayName("I: 关联咨询的 consult_runtime_record 保存用户原始问题（不复制案件上下文）")
    void linkedConsult_runtimeSavesOriginalQuestion() {
        when(caseAnalysisService.get(1L, 1L)).thenReturn(confirmedCase());
        stubLinkedSuccessPath();

        consultService.consult(QUESTION, 1L, 1L, "user", "127.0.0.1");

        verify(consultRuntimeRecordService).saveQuietly(argThat(r ->
                "SUCCESS".equals(r.getExecutionStatus())
                        && QUESTION.equals(r.getQuestion())
                        && r.getAnswer() != null
                        && r.getRetrievedArticleIds() != null));
    }

    @Test
    @DisplayName("J: 关联咨询检索输入与 citations 仍走既有 RAG（检索查询不含案件文本，引用为法规）")
    void linkedConsult_retrievalAndCitationsUnchanged() {
        when(caseAnalysisService.get(1L, 1L)).thenReturn(confirmedCase());
        stubLinkedSuccessPath();

        ConsultResponse resp = consultService.consult(QUESTION, 1L, 1L, "user", "127.0.0.1");

        // retrievalQuery 形态不变：域词 + QU 改写查询（案件事实经改写注入，不把背景文本塞进检索）
        verify(ragService).retrieveEnhanced(argThat(q ->
                        q.contains("劳动争议") && !q.contains("【案件背景】")),
                any(), any(), any());
        // citations 完全来自 Top-5 法规（案件字段绝不充当法律 citation）
        assertEquals(1, resp.getCitations().size());
        assertEquals("中华人民共和国劳动法", resp.getCitations().get(0).getDocumentName());
        assertFalse(resp.getCitations().get(0).getContentSnippet().contains(CONFIRMED_FACT));
    }
}
