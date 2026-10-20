package com.ailegal.service.impl;

import com.ailegal.config.EvaluationQuestionInitializer;
import com.ailegal.dto.LegalArticleDocument;
import com.ailegal.dto.QueryUnderstandingResult;
import com.ailegal.dto.RagRetrieveTestResponse;
import com.ailegal.dto.RagTop5Hit;
import com.ailegal.entity.EvaluationQuestion;
import com.ailegal.entity.EvaluationResult;
import com.ailegal.entity.EvaluationRun;
import com.ailegal.exception.BusinessException;
import com.ailegal.mapper.EvaluationQuestionMapper;
import com.ailegal.mapper.EvaluationResultMapper;
import com.ailegal.mapper.EvaluationRunMapper;
import com.ailegal.service.LegalKnowledgeService;
import com.ailegal.service.LlmService;
import com.ailegal.service.OperationLogService;
import com.ailegal.service.QueryUnderstandingService;
import com.ailegal.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Final-Requirement-Gaps-E 自动化测试 A-J：
 * Reference Article Hit@5 正式口径（人工标注参考法条 vs 最终权威 Top5 精确集合交集）。
 *
 *  A 单参考在 Top5 -> hit=1
 *  B 多参考任一在 Top5 -> hit=1
 *  C 全部不在 Top5 -> hit=0
 *  D 关键词出现在检索正文但 article 不在 Top5 -> hit=0（证明关键词完全退出命中判定）
 *  E DIRECT 模式 -> hit=NULL（不适用）
 *  F 检索前技术失败 / QU 拒绝 -> hit=NULL
 *  G 参考法条解析失败 -> 稳定错误码 + hit=NULL（NOT_FOUND / AMBIGUOUS，不判 miss）
 *  H BASIC / ENHANCED 走同一判定规则
 *  I 历史 run（LEGACY_KEYWORD_V1）口径保留，不重算；新 run 默认 REFERENCE_ARTICLE_TOP5_V2
 *  J 30 题标注 validator：30/30 参考法条非空，每题 1~3 条，文档名属于正式知识库 7 文档
 */
@ExtendWith(MockitoExtension.class)
class EvaluationHitMetricTest {

    private static final String LCB = "中华人民共和国劳动合同法";
    private static final String LABOR_LAW = "中华人民共和国劳动法";
    private static final String CC_RENTAL = "中华人民共和国民法典（租赁合同章）";
    private static final String CC_TERMINATION = "中华人民共和国民法典（合同解除与违约责任相关条款）";
    private static final String RENTAL_REGULATION = "住房租赁条例";
    private static final String CONSUMER_LAW = "中华人民共和国消费者权益保护法";

    private static final List<String> KB_DOCUMENTS = List.of(
            LCB, LABOR_LAW, CC_RENTAL, CC_TERMINATION, RENTAL_REGULATION, CONSUMER_LAW,
            "中华人民共和国民法典（其他合同相关条款）");

    @Mock
    private EvaluationRunMapper evaluationRunMapper;
    @Mock
    private EvaluationQuestionMapper evaluationQuestionMapper;
    @Mock
    private EvaluationResultMapper evaluationResultMapper;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private QueryUnderstandingService queryUnderstandingService;
    @Mock
    private RagService ragService;
    @Mock
    private LegalKnowledgeService legalKnowledgeService;
    @Mock
    private LlmService llmService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EvaluationExecutionServiceImpl service() {
        return new EvaluationExecutionServiceImpl(evaluationRunMapper, evaluationQuestionMapper,
                evaluationResultMapper, operationLogService, queryUnderstandingService, ragService,
                legalKnowledgeService, llmService, objectMapper, transactionManager);
    }

    // ---------------------------------------------------------------- helpers

    /** 构造标注了参考法条的题目 */
    private EvaluationQuestion question(String json) {
        EvaluationQuestion q = new EvaluationQuestion();
        q.setQuestionText("测试问题：加班费怎么计算？");
        q.setExpectedEvidenceKeywords("[\"加班费\"]");
        q.setReferenceArticlesJson(json);
        return q;
    }

    private String refs(String... entries) {
        return "[" + String.join(",", entries) + "]";
    }

    private String ref(String doc, String number) {
        return "{\"documentName\":\"" + doc + "\",\"articleNumber\":\"" + number + "\"}";
    }

    private RagRetrieveTestResponse retrieval(Long... top5Ids) {
        List<RagTop5Hit> hits = java.util.Arrays.stream(top5Ids)
                .map(id -> new RagTop5Hit(id, LABOR_LAW, "第四十四条", 0.9, 0.5, 1.0, 0.85, List.of("加班费")))
                .toList();
        return new RagRetrieveTestResponse("LABOR_DISPUTE", List.of("加班费"),
                List.of(), hits, true, "PASS");
    }

    private LegalArticleDocument article(Long id, String doc, String number, String content) {
        LegalArticleDocument a = new LegalArticleDocument();
        a.setArticleId(id);
        a.setDocumentId(id);
        a.setDocumentName(doc);
        a.setArticleNumber(number);
        a.setContent(content);
        a.setArticleValidStatus(1);
        a.setDocumentValidStatus(1);
        return a;
    }

    private LegalArticleDocument refRow(Long id, String doc, String number) {
        LegalArticleDocument a = new LegalArticleDocument();
        a.setArticleId(id);
        a.setDocumentName(doc);
        a.setArticleNumber(number);
        return a;
    }

    /** 反射调用私有方法 */
    private Object invoke(String name, Class<?>[] params, Object... args) throws Exception {
        Method m = EvaluationExecutionServiceImpl.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m.invoke(service(), args);
    }

    private Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private Integer hit(Object judgeResult) throws Exception {
        return (Integer) field(judgeResult, "hit");
    }

    private String err(Object judgeResult) throws Exception {
        return (String) field(judgeResult, "errorCode");
    }

    private Integer outcomeHit(Object outcome) throws Exception {
        return (Integer) field(outcome, "retrievalHit");
    }

    private String outcomeStatus(Object outcome) throws Exception {
        return (String) field(outcome, "executionStatus");
    }

    private String outcomeError(Object outcome) throws Exception {
        return (String) field(outcome, "errorCode");
    }

    // ---------------------------------------------------------------- A B C D G（judge 层）

    @Test
    @DisplayName("A: 单参考法条出现在最终权威 Top5 -> hit=1")
    void singleReferenceInTop5Hits() throws Exception {
        when(legalKnowledgeService.selectValidReferenceIds(anyList(), anyList()))
                .thenReturn(List.of(refRow(44L, LABOR_LAW, "第四十四条")));
        Object v = invoke("judgeReferenceHit",
                new Class<?>[]{EvaluationQuestion.class, List.class},
                question(refs(ref(LABOR_LAW, "第四十四条"))), List.of(11L, 44L, 33L));
        assertEquals(1, hit(v), "参考法条 id=44 应命中 Top5");
        assertNull(err(v));
    }

    @Test
    @DisplayName("B: 多参考法条任一条在 Top5 -> hit=1")
    void anyOfMultipleReferencesHits() throws Exception {
        when(legalKnowledgeService.selectValidReferenceIds(anyList(), anyList()))
                .thenReturn(List.of(refRow(7L, LCB, "第七条"), refRow(10L, LCB, "第十条")));
        Object v = invoke("judgeReferenceHit",
                new Class<?>[]{EvaluationQuestion.class, List.class},
                question(refs(ref(LCB, "第七条"), ref(LCB, "第十条"))), List.of(1L, 7L, 3L));
        assertEquals(1, hit(v), "第二条参考法条 id=7 在 Top5 中即应命中");
        assertNull(err(v));
    }

    @Test
    @DisplayName("C: 所有参考法条均不在 Top5 -> hit=0（精确集合交集，非相似度/包含）")
    void noReferenceInTop5Misses() throws Exception {
        when(legalKnowledgeService.selectValidReferenceIds(anyList(), anyList()))
                .thenReturn(List.of(refRow(50L, LABOR_LAW, "第四十四条")));
        Object v = invoke("judgeReferenceHit",
                new Class<?>[]{EvaluationQuestion.class, List.class},
                question(refs(ref(LABOR_LAW, "第四十四条"))), List.of(11L, 22L, 33L, 44L, 55L));
        assertEquals(0, hit(v), "Top5 不包含 id=50 应判未命中");
        assertNull(err(v));
    }

    @Test
    @DisplayName("D: 关键词出现在检索正文但参考 article 不在 Top5 -> hit=0（关键词退出命中判定）")
    void keywordInEvidenceButArticleNotInTop5Misses() throws Exception {
        // 检索正文含关键词"加班费"（旧关键词口径会误判命中），但参考法条 id=44 不在 Top5
        when(legalKnowledgeService.selectValidArticlesWithDocuments(anyList()))
                .thenReturn(List.of(article(11L, LABOR_LAW, "第四十四条", "用人单位安排加班费的，应当支付加班工资"),
                        article(22L, LABOR_LAW, "第四十五条", "加班费按照法律规定计算")));
        when(legalKnowledgeService.selectValidReferenceIds(anyList(), anyList()))
                .thenReturn(List.of(refRow(44L, LABOR_LAW, "第四十四条")));
        when(llmService.chat(any(String.class), any(String.class))).thenReturn("依据[依据1]计算加班费");
        Object outcome = invoke("runGrounded",
                new Class<?>[]{EvaluationQuestion.class, RagRetrieveTestResponse.class, long.class,
                        Long.class, String.class, String.class},
                question(refs(ref(LABOR_LAW, "第四十四条"))), retrieval(11L, 22L), 0L, 1L, "t", "ip");
        assertEquals(0, outcomeHit(outcome), "关键词在正文不参与判定，参考 article 不在 Top5 -> 未命中");
        assertEquals("SUCCESS", outcomeStatus(outcome));
    }

    @Test
    @DisplayName("G1: 参考法条解析 0 条 -> REFERENCE_ARTICLE_NOT_FOUND + hit=NULL（不判 miss）")
    void referenceNotFoundYieldsNullHit() throws Exception {
        when(legalKnowledgeService.selectValidReferenceIds(anyList(), anyList())).thenReturn(List.of());
        Object v = invoke("judgeReferenceHit",
                new Class<?>[]{EvaluationQuestion.class, List.class},
                question(refs(ref("中华人民共和国社会保险法", "第一条"))), List.of(11L, 22L));
        assertNull(hit(v), "解析失败不得判 miss");
        assertEquals("REFERENCE_ARTICLE_NOT_FOUND", err(v));
    }

    @Test
    @DisplayName("G2: 参考法条解析多条 -> REFERENCE_ARTICLE_AMBIGUOUS + hit=NULL（标注需人工修正）")
    void referenceAmbiguousYieldsNullHit() throws Exception {
        when(legalKnowledgeService.selectValidReferenceIds(anyList(), anyList()))
                .thenReturn(List.of(refRow(44L, LABOR_LAW, "第四十四条"), refRow(444L, LABOR_LAW, "第四十四条")));
        Object v = invoke("judgeReferenceHit",
                new Class<?>[]{EvaluationQuestion.class, List.class},
                question(refs(ref(LABOR_LAW, "第四十四条"))), List.of(11L, 44L));
        assertNull(hit(v), "歧义不得判 miss");
        assertEquals("REFERENCE_ARTICLE_AMBIGUOUS", err(v));
    }

    // ---------------------------------------------------------------- E F（模式层）

    @Test
    @DisplayName("E: DIRECT_LLM 模式 -> retrieval_hit=NULL（不适用）")
    void directModeHitIsNull() throws Exception {
        when(llmService.chat(any(String.class), any(String.class))).thenReturn("直接回答");
        Object outcome = invoke("runDirect",
                new Class<?>[]{EvaluationQuestion.class, long.class},
                question(refs(ref(LABOR_LAW, "第四十四条"))), 0L);
        assertNull(outcomeHit(outcome));
        assertEquals("SUCCESS", outcomeStatus(outcome));
    }

    @Test
    @DisplayName("F1: 检索前技术失败（Embedding）-> hit=NULL + 稳定错误码")
    void retrievalFailureBeforeJudgeYieldsNullHit() throws Exception {
        when(ragService.retrieveTest(any(String.class), any(Long.class), any(String.class), any(String.class)))
                .thenThrow(new BusinessException(500, "检索失败:Embedding 调用超时"));
        Object outcome = invoke("runBasic",
                new Class<?>[]{EvaluationQuestion.class, long.class, Long.class, String.class, String.class},
                question(refs(ref(LABOR_LAW, "第四十四条"))), 0L, 1L, "t", "ip");
        assertNull(outcomeHit(outcome));
        assertEquals("FAILED", outcomeStatus(outcome));
        assertEquals("EMBEDDING_ERROR", outcomeError(outcome));
    }

    @Test
    @DisplayName("F2: QU 理解失败 / 拒绝 -> hit=NULL（检索完成前无 Top5 可判定）")
    void quFailureOrRejectYieldsNullHit() throws Exception {
        when(queryUnderstandingService.understand(any(String.class)))
                .thenThrow(new BusinessException(500, "问题理解失败"));
        Object failed = invoke("runEnhanced",
                new Class<?>[]{EvaluationQuestion.class, long.class, Long.class, String.class, String.class},
                question(refs(ref(LABOR_LAW, "第四十四条"))), 0L, 1L, "t", "ip");
        assertNull(outcomeHit(failed));
        assertEquals("QU_ERROR", outcomeError(failed));

        when(queryUnderstandingService.understand(any(String.class)))
                .thenReturn(new QueryUnderstandingResult(false, null, null, List.of()));
        Object rejected = invoke("runEnhanced",
                new Class<?>[]{EvaluationQuestion.class, long.class, Long.class, String.class, String.class},
                question(refs(ref(LABOR_LAW, "第四十四条"))), 0L, 1L, "t", "ip");
        assertNull(outcomeHit(rejected));
        assertEquals("REJECTED", outcomeStatus(rejected));
    }

    // ---------------------------------------------------------------- H（同规则）

    @Test
    @DisplayName("H: BASIC 与 ENHANCED 使用同一判定规则（同一场景同一结果）")
    void basicAndEnhancedShareSameRule() throws Exception {
        // 同一参考法条（id=44）与同一 Top5（含 44）
        when(ragService.retrieveTest(any(String.class), any(Long.class), any(String.class), any(String.class)))
                .thenReturn(retrieval(11L, 44L));
        when(queryUnderstandingService.understand(any(String.class)))
                .thenReturn(new QueryUnderstandingResult(true, QueryUnderstandingResult.LABOR_DISPUTE,
                        "加班工资计算", List.of()));
        when(ragService.retrieveEnhanced(any(String.class), any(Long.class), any(String.class), any(String.class)))
                .thenReturn(retrieval(11L, 44L));
        when(legalKnowledgeService.selectValidArticlesWithDocuments(anyList()))
                .thenReturn(List.of(article(11L, LABOR_LAW, "第四十四条", "加班费内容"),
                        article(44L, LABOR_LAW, "第四十四条", "加班费内容")));
        when(legalKnowledgeService.selectValidReferenceIds(anyList(), anyList()))
                .thenReturn(List.of(refRow(44L, LABOR_LAW, "第四十四条")));
        when(llmService.chat(any(String.class), any(String.class))).thenReturn("加班费按法律规定计算");

        Object basic = invoke("runBasic",
                new Class<?>[]{EvaluationQuestion.class, long.class, Long.class, String.class, String.class},
                question(refs(ref(LABOR_LAW, "第四十四条"))), 0L, 1L, "t", "ip");
        Object enhanced = invoke("runEnhanced",
                new Class<?>[]{EvaluationQuestion.class, long.class, Long.class, String.class, String.class},
                question(refs(ref(LABOR_LAW, "第四十四条"))), 0L, 1L, "t", "ip");

        assertEquals(1, outcomeHit(basic), "BASIC 参考法条在 Top5 -> 命中");
        assertEquals(1, outcomeHit(enhanced), "ENHANCED 同一规则 -> 命中");
        // 关键词绝不进入检索 query（6A 冻结约定）
        verify(ragService).retrieveTest(org.mockito.ArgumentMatchers.eq("测试问题：加班费怎么计算？"),
                any(Long.class), any(String.class), any(String.class));
    }

    // ---------------------------------------------------------------- I（历史口径）

    @Test
    @DisplayName("I1: 新 run 默认 REFERENCE_ARTICLE_TOP5_V2；历史 run 保留 LEGACY 不重算")
    void newRunDefaultsV2AndLegacyPreserved() throws Exception {
        when(evaluationRunMapper.selectCount(any())).thenReturn(0L);
        when(evaluationQuestionMapper.selectList(any())).thenReturn(List.of());

        EvaluationExecutionServiceImpl svc = service();
        EvaluationRun run = svc.createRun("t", 1L, "u", "ip");
        assertEquals("REFERENCE_ARTICLE_TOP5_V2", run.getMetricVersion(),
                "新建 run 默认参考法条 Hit@5 口径");

        // 历史 run 显示 Legacy：判定函数不接受 metricVersion 参数（不存在按 run 口径重算的入口）
        Method judge = EvaluationExecutionServiceImpl.class.getDeclaredMethod("judgeReferenceHit",
                EvaluationQuestion.class, List.class);
        assertEquals(2, judge.getParameterCount(),
                "judgeReferenceHit 只按参考法条判定，历史 run 结果绝不重算");
    }

    @Test
    @DisplayName("I2: CSV 含命中口径与参考法条可读文本列；Legacy 显示'关键词命中(Legacy)'")
    void csvCarriesMetricColumns() throws Exception {
        when(evaluationRunMapper.selectById(any(Long.class))).thenReturn(legacyRun());
        when(evaluationResultMapper.selectList(any())).thenReturn(List.of());

        String csv = service().exportCsv(1L, 1L, "u", "ip");
        assertTrue(csv.contains("命中口径"), "CSV 必须含命中口径列");
        assertTrue(csv.contains("参考法条(人工标注)"), "CSV 必须含参考法条列");
        assertTrue(csv.contains("命中文档ID(Top5)"), "CSV 必须含 Top5 列");

        Object legacy = invoke("formatMetricVersion", new Class<?>[]{String.class}, "LEGACY_KEYWORD_V1");
        assertEquals("关键词命中(Legacy)", legacy);
        Object v2 = invoke("formatMetricVersion", new Class<?>[]{String.class}, "REFERENCE_ARTICLE_TOP5_V2");
        assertEquals("参考法条Hit@5", v2);
    }

    private EvaluationRun legacyRun() {
        EvaluationRun run = new EvaluationRun();
        run.setId(1L);
        run.setMetricVersion("LEGACY_KEYWORD_V1");
        return run;
    }

    // ---------------------------------------------------------------- J（标注 validator）

    @Test
    @DisplayName("J: 30/30 题参考法条非空，每题 1~3 条，文档名属于正式知识库文档")
    void thirtyAnnotationsAreComplete() throws Exception {
        List<?> seeds = EvaluationQuestionInitializer.seeds();
        assertEquals(30, seeds.size());
        assertTrue(EvaluationQuestionInitializer.allSeedsHaveReferences(),
                "30/30 题目都必须有人工标注的参考法条");
        for (Object s : seeds) {
            List<?> refs = readRefs(s);
            assertFalse(refs.isEmpty(), "参考法条不得为空");
            assertTrue(refs.size() <= 3, "每题建议 1~3 条参考法条，实际 " + refs.size());
            for (Object r : refs) {
                String doc = (String) readField(r, "documentName");
                assertTrue(KB_DOCUMENTS.contains(doc),
                        "文档名必须是正式知识库文档: " + doc);
            }
        }
    }

    private List<?> readRefs(Object seed) throws Exception {
        return (List<?>) readField(seed, "refs");
    }

    private Object readField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
