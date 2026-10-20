package com.ailegal.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fixed keyword concept groups for 3A-K calibration (13 concepts across 3 domains).
 *
 * Each concept has:
 * - conceptName: unique identifier
 * - domain: parent domain
 * - questionAliases: if the user question contains ANY of these, the concept enters queryConcepts
 * - evidenceAliases: if an article's content or documentName contains ANY of these, the article matches the concept
 *
 * Scoring rule: matchedConceptCount / queryConceptCount. queryConceptCount==0 -> 0.0.
 * A concept is counted at most once regardless of how many aliases match.
 *
 * Evidence aliases have been verified against the real 316 legal articles in MySQL.
 * Adjustments from the task specification:
 * - CONSUMER_FRAUD: "价款的三倍"/"服务费用的三倍" do NOT appear verbatim;
 *   replaced with "费用的三倍" which appears in the real text ("...费用的三倍")
 * - PRODUCT_QUALITY: "商品或者服务不符合质量要求" does NOT appear verbatim;
 *   replaced with "不符合质量要求" which appears in the real text ("经营者提供的商品或者服务不符合质量要求")
 * - DEPOSIT_RETURN: no reliable evidence alias found in the 316 articles (押金/保证金 not present in HOUSE_RENTAL);
 *   concept is defined but will not match any article evidence — documented in the report.
 *
 * 3A-FinalCal tightening (verified against the real 316-article texts in MySQL):
 * - WAGE_ARREARS: removed over-broad "工资"/"劳动报酬"/"支付工资" (20/26/7 hits);
 *   kept only real wage-withholding phrases (第五十条/第九十一条/第三十条).
 * - ILLEGAL_TERMINATION: removed zero-hit "违法解除"/"违法终止" and over-broad
 *   "解除劳动合同"(23)/"终止劳动合同"(12)/"赔偿金"(7); kept 第八十七条 verbatim phrases.
 * - EARLY_TERMINATION: rental chapter has no direct advance-termination/违约金 clause;
 *   reduced to "随时解除" (第七百三十/七百三十一条) only — concept allowed to weaken, no
 *   mass +0.2 across the rental chapter.
 * - PRODUCT_QUALITY: removed over-broad "质量"/"修理"/"更换"/"退货";
 *   kept only quality-defect phrases (第二十四/四十八/五十二/五十四条).
 */
public final class KeywordConceptGroups {

    private KeywordConceptGroups() {}

    public static final List<ConceptGroup> CONCEPTS = List.of(
            // === LABOR_DISPUTE ===
            new ConceptGroup("WAGE_ARREARS", "LABOR_DISPUTE",
                    List.of("拖欠工资", "欠工资", "工资不给", "不发工资", "克扣工资"),
                    List.of("克扣或者无故拖欠", "拖欠或者未足额支付劳动报酬", "拖欠劳动者工资")),

            new ConceptGroup("DOUBLE_WAGE_NO_CONTRACT", "LABOR_DISPUTE",
                    List.of("没签劳动合同", "未签劳动合同", "没有劳动合同", "双倍工资", "二倍工资"),
                    List.of("未与劳动者订立书面劳动合同", "未订立书面劳动合同", "书面劳动合同", "二倍的工资", "二倍工资")),

            new ConceptGroup("ILLEGAL_TERMINATION", "LABOR_DISPUTE",
                    List.of("违法辞退", "违法解除", "被公司辞退", "辞退赔偿", "违法开除"),
                    List.of("违反本法规定解除或者终止劳动合同", "经济补偿标准的二倍", "支付赔偿金")),

            new ConceptGroup("ECONOMIC_COMPENSATION", "LABOR_DISPUTE",
                    List.of("经济补偿", "经济补偿金", "辞退补偿", "离职补偿"),
                    List.of("经济补偿", "经济补偿金", "工作年限", "每满一年支付一个月工资")),

            new ConceptGroup("OVERTIME_PAY", "LABOR_DISPUTE",
                    List.of("加班费", "加班工资", "加班不给钱", "周末加班", "法定节假日加班"),
                    List.of("延长工作时间", "工资报酬", "百分之一百五十", "百分之二百", "百分之三百", "休息日", "法定休假日")),

            // === HOUSE_RENTAL ===
            new ConceptGroup("RENTAL_REPAIR", "HOUSE_RENTAL",
                    List.of("房屋漏水", "漏水维修", "房东不维修", "房屋维修", "谁负责维修"),
                    List.of("维修义务", "维修租赁物", "出租人未履行维修义务", "维修费用", "租赁物需要维修")),

            new ConceptGroup("SUBLEASE", "HOUSE_RENTAL",
                    List.of("转租", "把房子转租", "租客转租", "未经房东同意转租"),
                    List.of("转租", "经出租人同意", "未经出租人同意", "第三人")),

            new ConceptGroup("EARLY_TERMINATION", "HOUSE_RENTAL",
                    List.of("提前退租", "提前解除租约", "提前解除租赁合同", "退租违约", "违约金"),
                    List.of("随时解除")),

            new ConceptGroup("CONTRACT_PENALTY", "HOUSE_RENTAL",
                    // 4A-RetrievalTune corpus synchronization: the knowledge base grew from 316 to 376
                    // (doc16 民法典合同解除与违约责任相关条款, incl. 第五百八十五条 违约金调整规则).
                    // This concept covers penalty-clause agreement / amount / adjustment questions.
                    // Evidence aliases verified against the real 376-article corpus: only 第五百八十五条
                    // contains these verbatim phrases (约定的违约金 / 予以增加 / 予以适当减少 / ...).
                    // Kept independent from EARLY_TERMINATION: a question may hit both — the user asks
                    // two legal sub-questions (advance termination AND penalty amount adjustment).
                    List.of("违约金", "约定违约金", "违约金调整", "违约金数额", "违约金过高", "违约金过低"),
                    List.of("约定的违约金", "予以增加", "予以适当减少", "过分高于造成的损失", "低于造成的损失")),

            new ConceptGroup("DEPOSIT_RETURN", "HOUSE_RENTAL",
                    List.of("不退押金", "退押金", "押金不退", "租房押金"),
                    // 4A-DepositCorpusSync (verified against the real 376-article corpus):
                    // 押金 appears in 5 articles of 住房租赁条例 (360/第十条, 385/第三十五条,
                    // 375/第二十五条, 386/第三十六条, 394/第四十四条); 保证金/定金 = 0 hits.
                    // Only 第十条/第三十五条 are deposit-return related; 375/386/394 are
                    // 经纪机构/网络平台 代收代付 禁止性规定 (must NOT be treated as evidence).
                    // Evidence aliases chosen verbatim from 第十条 (扣减押金, 返还时间) and
                    // 第三十五条 (押金返还): hit exactly 360+385, zero false hits on 375/386/394.
                    List.of("扣减押金", "押金返还", "返还时间")),

            // === CONSUMER_RIGHTS ===
            new ConceptGroup("CONSUMER_FRAUD", "CONSUMER_RIGHTS",
                    List.of("假货", "买到假货", "商家欺诈", "消费欺诈", "三倍赔偿"),
                    // Adjusted: "价款的三倍"/"服务费用的三倍" -> "费用的三倍" (real text: "...费用的三倍")
                    List.of("欺诈行为", "增加赔偿", "费用的三倍")),

            new ConceptGroup("SEVEN_DAY_RETURN", "CONSUMER_RIGHTS",
                    List.of("七天无理由", "七日无理由", "无理由退货", "七天退货"),
                    List.of("七日内退货", "无需说明理由", "无理由退货")),

            new ConceptGroup("REFUND_RETURN", "CONSUMER_RIGHTS",
                    // 3A-GateLock: bare "退款" removed — hard negative "退款这个英文单词怎么说？"
                    // was triggered by it and gained keywordScore=1. Only consult-context phrases remain.
                    List.of("不给退款", "商家不退款", "拒绝退款", "退货退款", "要求退款", "申请退款", "退款怎么办", "退货"),
                    List.of("退货", "退款", "返还消费者支付的商品价款", "更换", "修理")),

            new ConceptGroup("PRODUCT_QUALITY", "CONSUMER_RIGHTS",
                    List.of("质量问题", "商品质量", "商品坏了", "三包", "质量不合格"),
                    // Adjusted: "商品或者服务不符合质量要求" -> "不符合质量要求" (real text: "经营者提供的商品或者服务不符合质量要求")
                    List.of("不符合质量要求", "商品或者服务存在缺陷", "修理、重作、更换、退货", "不合格的商品"))
    );

    /**
     * A keyword concept with question-trigger aliases and evidence-match aliases.
     */
    public static class ConceptGroup {
        private final String conceptName;
        private final String domain;
        private final List<String> questionAliases;
        private final List<String> evidenceAliases;

        public ConceptGroup(String conceptName, String domain,
                            List<String> questionAliases, List<String> evidenceAliases) {
            this.conceptName = conceptName;
            this.domain = domain;
            this.questionAliases = List.copyOf(questionAliases);
            this.evidenceAliases = List.copyOf(evidenceAliases);
        }

        public String getConceptName() {
            return conceptName;
        }

        public String getDomain() {
            return domain;
        }

        public List<String> getQuestionAliases() {
            return questionAliases;
        }

        public List<String> getEvidenceAliases() {
            return evidenceAliases;
        }

        /**
         * Whether the question triggers this concept (any questionAlias matches).
         */
        public boolean matchesQuestion(String question) {
            if (question == null) return false;
            for (String alias : questionAliases) {
                if (question.contains(alias)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Whether an article matches this concept's evidence (any evidenceAlias in content or documentName).
         * If evidenceAliases is empty (e.g. DEPOSIT_RETURN), always returns false.
         */
        public boolean matchesEvidence(String content, String documentName) {
            if (evidenceAliases.isEmpty()) return false;
            String haystack = content + documentName;
            for (String alias : evidenceAliases) {
                if (haystack.contains(alias)) {
                    return true;
                }
            }
            return false;
        }
    }
}
