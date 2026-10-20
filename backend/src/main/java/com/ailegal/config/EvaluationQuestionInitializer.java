package com.ailegal.config;

import com.ailegal.entity.EvaluationQuestion;
import com.ailegal.mapper.EvaluationQuestionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模块6 固定评测题种子数据（30 题，幂等）。
 * 逐题按 question_code 判存在：不存在 -> INSERT；存在且 reference_articles_json IS NULL -> UPDATE 回填标注；
 * 存在且有标注 -> skip（中途失败重跑不会重复/覆盖人工标注）。
 * 与 DataInitializer 采用同一 ApplicationRunner 模式。
 *
 * 参考法条（reference_articles）为本项目正式命中口径 REFERENCE_ARTICLE_TOP5_V2 的人工标准答案标签：
 * 以当前正式本地法律知识库为准逐题人工标注，使用稳定业务标识 documentName + articleNumber
 * （不保存 DB 自增 articleId，避免 schema 重建后 ID 漂移）。expected_evidence_keywords 保留为
 * 辅助描述 / 历史兼容，不参与正式 Hit@5 计算。
 */
@Component
public class EvaluationQuestionInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationQuestionInitializer.class);

    private static final String LABOR = "LABOR_DISPUTE";
    private static final String RENTAL = "HOUSE_RENTAL";
    private static final String CONSUMER = "CONSUMER_RIGHTS";

    private static final String LCB_LAW = "中华人民共和国劳动合同法";
    private static final String LABOR_LAW = "中华人民共和国劳动法";
    private static final String CC_RENTAL = "中华人民共和国民法典（租赁合同章）";
    private static final String CC_TERMINATION = "中华人民共和国民法典（合同解除与违约责任相关条款）";
    private static final String RENTAL_REGULATION = "住房租赁条例";
    private static final String CONSUMER_PROTECTION_LAW = "中华人民共和国消费者权益保护法";

    private final EvaluationQuestionMapper evaluationQuestionMapper;
    private final ObjectMapper objectMapper;

    public EvaluationQuestionInitializer(EvaluationQuestionMapper evaluationQuestionMapper,
                                         ObjectMapper objectMapper) {
        this.evaluationQuestionMapper = evaluationQuestionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        int inserted = 0;
        int backfilled = 0;
        for (QuestionSeed seed : SEEDS) {
            LambdaQueryWrapper<EvaluationQuestion> exists = new LambdaQueryWrapper<>();
            exists.eq(EvaluationQuestion::getQuestionCode, seed.code);
            EvaluationQuestion existing = evaluationQuestionMapper.selectOne(exists);
            try {
                if (existing == null) {
                    EvaluationQuestion q = new EvaluationQuestion();
                    q.setQuestionCode(seed.code);
                    q.setDomain(seed.domain);
                    q.setQuestionText(seed.text);
                    q.setExpectedEvidenceKeywords(objectMapper.writeValueAsString(seed.keywords));
                    q.setReferenceArticlesJson(toReferenceJson(seed.refs));
                    q.setEnabled(1);
                    q.setCreatedAt(LocalDateTime.now());
                    q.setUpdatedAt(LocalDateTime.now());
                    evaluationQuestionMapper.insert(q);
                    inserted++;
                    continue;
                }
                // 已存在但缺标注：回填参考法条（历史库升级路径），不覆盖 question_text / keywords
                if (existing.getReferenceArticlesJson() == null || existing.getReferenceArticlesJson().isBlank()) {
                    int affected = evaluationQuestionMapper.update(null, new UpdateWrapper<EvaluationQuestion>()
                            .eq("question_code", seed.code)
                            .set("reference_articles_json", toReferenceJson(seed.refs))
                            .set("updated_at", LocalDateTime.now()));
                    if (affected > 0) {
                        backfilled++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to init evaluation question {}", seed.code, e);
            }
        }
        if (inserted > 0 || backfilled > 0) {
            log.info("Evaluation questions initialized: inserted={}, backfilled={}, total expected={}",
                    inserted, backfilled, SEEDS.size());
        } else {
            log.info("Evaluation questions already initialized ({} expected), nothing to do", SEEDS.size());
        }
    }

    /** 公开访问器：测试与人工标注审计用 */
    public static List<QuestionSeed> seeds() {
        return SEEDS;
    }

    /** 公开校验：30 题每道参考法条非空（annotation validator 用） */
    public static boolean allSeedsHaveReferences() {
        return SEEDS.size() == 30 && SEEDS.stream().allMatch(s -> s.refs() != null && !s.refs().isEmpty());
    }

    private String toReferenceJson(List<RefSeed> refs) {
        try {
            return objectMapper.writeValueAsString(refs);
        } catch (Exception e) {
            throw new IllegalStateException("reference json serialize failed", e);
        }
    }

    /**
     * 参考法条稳定标识：documentName（legal_document.document_name）+ articleNumber（legal_article.article_number）。
     * 运行期解析为当前有效 legal_article.id 与 final Top5 求交集，不保存自增 ID。
     */
    public record RefSeed(String documentName, String articleNumber) {
    }

    private record QuestionSeed(String code, String domain, String text, List<String> keywords, List<RefSeed> refs) {
    }

    private static final List<QuestionSeed> SEEDS = List.of(
            // ================= 劳动争议 L01-L10 =================
            new QuestionSeed("L01", LABOR,
                    "我在一家公司工作快一年了，公司一直没跟我签书面劳动合同。最近我提出想签合同，公司说大家都这样，不用签。请问这种情况下，我能要求公司给我什么补偿吗？",
                    List.of("未签订书面劳动合同", "双倍工资", "事实劳动关系"),
                    List.of(new RefSeed(LCB_LAW, "第十条"), new RefSeed(LCB_LAW, "第八十二条"))),
            new QuestionSeed("L02", LABOR,
                    "我在一家餐馆当服务员，老板已经两个月没发工资了，每次问都说资金周转不过来，让再等等。我担心他拖到最后不给了。请问被拖欠工资该怎么处理，我可以要求哪些钱？",
                    List.of("拖欠工资", "劳动报酬", "足额支付", "责令限期支付"),
                    List.of(new RefSeed(LCB_LAW, "第三十条"), new RefSeed(LCB_LAW, "第八十五条"))),
            new QuestionSeed("L03", LABOR,
                    "我在一家电商公司做客服，经常被要求周末和法定节假日加班，公司说安排调休就行，从来没发过加班费。我想问周末加班和法定节假日加班，公司应该怎么给我算加班工资？",
                    List.of("加班工资", "休息日加班", "法定休假日加班", "百分之一百五十", "百分之二百", "百分之三百"),
                    List.of(new RefSeed(LABOR_LAW, "第四十四条"))),
            new QuestionSeed("L04", LABOR,
                    "我在公司干了三年，业绩一直达标，前几天公司突然以\"业务调整\"为由把我辞退了，只给了一个月工资当补偿。我听说公司这样做要赔钱。请问公司这样辞退我合法吗，我应该得到什么赔偿？",
                    List.of("违法解除劳动合同", "经济补偿标准二倍", "赔偿金", "单方解除"),
                    List.of(new RefSeed(LCB_LAW, "第四十七条"), new RefSeed(LCB_LAW, "第八十七条"))),
            new QuestionSeed("L05", LABOR,
                    "我入职新公司才两个月，还在试用期。上周公司说我不符合录用条件，让我第二天不用来了。请问公司以\"不符合录用条件\"为由在试用期辞退我，需要拿出证据吗？我可以要求补偿吗？",
                    List.of("试用期", "不符合录用条件", "解除劳动合同", "试用期约定"),
                    List.of(new RefSeed(LCB_LAW, "第二十一条"), new RefSeed(LCB_LAW, "第三十九条"))),
            new QuestionSeed("L06", LABOR,
                    "我入职半年了，最近发现公司一直没有给我缴纳社会保险，问人事说试用期转正后才开始交。请问公司这种做法合法吗？我可以要求公司补缴或者赔偿吗？",
                    List.of("社会保险", "缴纳社保", "补缴", "用人单位义务"),
                    List.of(new RefSeed(LABOR_LAW, "第七十二条"))),
            new QuestionSeed("L07", LABOR,
                    "我表哥在建筑工地干活时从脚手架上摔下来，腿骨折了，住院花了三万多。工地老板只来医院看了一次，说给两千块营养费了事，也不愿意申请工伤认定。请问他这种情况算工伤吗？接下来该怎么办？",
                    List.of("工伤", "工伤认定", "工伤保险待遇", "用人单位责任"),
                    List.of(new RefSeed(LABOR_LAW, "第七十三条"))),
            new QuestionSeed("L08", LABOR,
                    "我上个月从一家公司离职，办完交接后公司说工资要等下个月发薪日统一结算，还要扣我一千块说是培训费。请问离职时工资应该什么时候结清？公司可以扣我的培训费吗？",
                    List.of("离职", "工资结清", "克扣工资", "培训费"),
                    List.of(new RefSeed(LABOR_LAW, "第五十条"), new RefSeed(LCB_LAW, "第九条"))),
            new QuestionSeed("L09", LABOR,
                    "我在公司工作了四年，劳动合同快到期了，公司通知我说不再续签。请问公司不续签劳动合同需要给我经济补偿吗？补偿金应该怎么计算？",
                    List.of("劳动合同期满", "不续签", "经济补偿", "每满一年支付一个月工资"),
                    List.of(new RefSeed(LCB_LAW, "第四十六条"), new RefSeed(LCB_LAW, "第四十七条"))),
            new QuestionSeed("L10", LABOR,
                    "我在一家装修公司做木工，没签合同，工资都是包工头微信转账的。现在包工头不认账，说我不是他们公司的人，还欠我三个月工钱。请问我没有合同，怎么证明我和公司之间存在劳动关系？",
                    List.of("事实劳动关系", "工资支付记录", "考勤记录", "证明劳动关系"),
                    List.of(new RefSeed(LCB_LAW, "第七条"), new RefSeed(LCB_LAW, "第十条"))),

            // ================= 房屋租赁 R01-R10 =================
            new QuestionSeed("R01", RENTAL,
                    "我租了一套房子，交了三个月房租当押金。退租时房子没有损坏，但房东以\"墙上有挂画痕迹\"为由扣了我一半押金。请问房东这样扣押金合理吗？我应该怎么要回押金？",
                    List.of("押金", "押金返还", "合理扣减", "损坏赔偿"),
                    List.of(new RefSeed(RENTAL_REGULATION, "第十条"), new RefSeed(CC_RENTAL, "第七百一十条"))),
            new QuestionSeed("R02", RENTAL,
                    "我租的房子卫生间水管老化漏水，把楼下邻居的天花板泡坏了，邻居找我要赔偿。我多次找房东维修，房东说房子租给我了，维修的事应该我自己解决。请问这个维修责任到底该谁承担？",
                    List.of("维修义务", "出租人维修", "租赁物损坏", "维修费用"),
                    List.of(new RefSeed(CC_RENTAL, "第七百一十二条"), new RefSeed(CC_RENTAL, "第七百一十三条"))),
            new QuestionSeed("R03", RENTAL,
                    "我签了一年的租房合同，才住了三个月，因为工作调动要去外地，想提前退租。房东说提前退租要扣我全部押金，还说按合同要赔两个月的房租。请问提前退租真的要赔这么多吗？",
                    List.of("提前退租", "解除租赁合同", "违约责任", "损失赔偿"),
                    List.of(new RefSeed(CC_TERMINATION, "第五百六十二条"), new RefSeed(CC_TERMINATION, "第五百八十五条"))),
            new QuestionSeed("R04", RENTAL,
                    "我和房东签的租房合同里写着，谁提前解约要付对方三个月房租作为违约金。现在因为楼上漏水一直修不好，我想搬走，房东要求我按合同付三个月违约金。请问这个违约金数额合理吗？可以要求减少吗？",
                    List.of("违约金", "违约金调整", "过分高于损失", "适当减少"),
                    List.of(new RefSeed(CC_TERMINATION, "第五百八十五条"))),
            new QuestionSeed("R05", RENTAL,
                    "我签了一年的租房合同，住了半年，房东突然说儿子结婚要用房，让我一个月内搬走，而且只同意退还剩余租金，不给任何补偿。请问房东可以这样提前解约吗？我应该得到什么补偿？",
                    List.of("出租人提前解约", "提前收回房屋", "违约责任", "违约金"),
                    List.of(new RefSeed(CC_TERMINATION, "第五百八十四条"), new RefSeed(RENTAL_REGULATION, "第十二条"))),
            new QuestionSeed("R06", RENTAL,
                    "因为收入不稳定，我已经欠了房东两个月的房租，房东说再不给钱就换锁把我的东西扔出去。我承认欠租不对，正在想办法筹钱。请问房东能直接换锁赶我走吗？我该怎么处理？",
                    List.of("拖欠租金", "催告", "解除合同", "租金支付义务"),
                    List.of(new RefSeed(CC_RENTAL, "第七百二十二条"), new RefSeed(RENTAL_REGULATION, "第十二条"))),
            new QuestionSeed("R07", RENTAL,
                    "我租房子养了一只猫，把房东的布艺沙发抓坏了，房东要求我按新沙发的价格赔偿。我觉得沙发本来就用了好几年，不应该按新的赔。请问租客损坏屋内物品，赔偿标准该怎么定？",
                    List.of("承租人保管义务", "正常损耗", "损害赔偿"),
                    List.of(new RefSeed(CC_RENTAL, "第七百一十四条"), new RefSeed(CC_TERMINATION, "第五百八十四条"))),
            new QuestionSeed("R08", RENTAL,
                    "我租了一套两居室，把其中一间转租给了朋友，收了点房租分摊费用。房东发现后很生气，说我没经他同意转租，要解除合同让我搬走。请问房东有这个权利吗？我的转租行为合法吗？",
                    List.of("转租", "经出租人同意", "未经同意转租", "解除合同"),
                    List.of(new RefSeed(CC_RENTAL, "第七百一十六条"))),
            new QuestionSeed("R09", RENTAL,
                    "我的一年租房合同上个月就到期了，房东没说什么，我也继续住着，这个月的租金我也照常转了。现在房东突然说合同早就到期了，要我三天内搬走。请问合同到期后我继续住，原来的合同还有效吗？",
                    List.of("租赁合同到期", "继续使用", "不定期租赁", "解除合同"),
                    List.of(new RefSeed(CC_RENTAL, "第七百三十四条"))),
            new QuestionSeed("R10", RENTAL,
                    "我和房东因为退还押金的事闹得很僵，房东把我微信拉黑了，电话也不接。我去派出所，民警说这是民事纠纷不归他们管。请问租房押金纠纷应该通过什么途径解决？可以直接去法院起诉吗？",
                    List.of("租赁纠纷", "协商", "调解", "诉讼", "证据"),
                    List.of(new RefSeed(RENTAL_REGULATION, "第三十五条"))),

            // ================= 消费者权益 C01-C10 =================
            new QuestionSeed("C01", CONSUMER,
                    "我在商场买了一台三千多的洗衣机，用了不到一个月就出故障，找商家，商家说只能修不能换，修了一次还是坏的。请问这种情况我可以要求商家给我换新机或者退货吗？",
                    List.of("商品质量", "不符合质量要求", "修理更换退货", "三包"),
                    List.of(new RefSeed(CONSUMER_PROTECTION_LAW, "第二十四条"))),
            new QuestionSeed("C02", CONSUMER,
                    "我在直播间买了一件\"品牌\"羽绒服，收货后发现做工粗糙，吊牌也是仿冒的。联系商家，商家只同意退货退款，不承认是假货。请问买到假货我可以要求什么赔偿？",
                    List.of("假货", "欺诈行为", "增加赔偿", "三倍赔偿"),
                    List.of(new RefSeed(CONSUMER_PROTECTION_LAW, "第五十五条"), new RefSeed(CONSUMER_PROTECTION_LAW, "第四十条"))),
            new QuestionSeed("C03", CONSUMER,
                    "我在网上买了一个电饭煲，用了两天觉得不太喜欢，想退货。商家说电饭煲已经使用过了，影响二次销售，不能七天无理由退货。请问我这种情况还能享受七天无理由退货吗？",
                    List.of("七天无理由退货", "网络购物", "商品完好", "不影响二次销售"),
                    List.of(new RefSeed(CONSUMER_PROTECTION_LAW, "第二十五条"))),
            new QuestionSeed("C04", CONSUMER,
                    "我在购物平台买了一台标注\"512G内存\"的笔记本电脑，收到后发现实际只有256G，商家说是我看错了页面。我保留了商品页面截图。请问商家这种宣传和实物不符的情况，我可以怎么维权？",
                    List.of("虚假宣传", "商品描述不符", "欺诈", "退一赔三"),
                    List.of(new RefSeed(CONSUMER_PROTECTION_LAW, "第二十条"))),
            new QuestionSeed("C05", CONSUMER,
                    "我在网上买了一部手机，收货当天就发现屏幕有亮点，联系商家要求退款，商家以\"已激活不支持退款\"为由拒绝，只同意维修。请问商品存在质量问题，商家可以拒绝我的退款要求吗？",
                    List.of("拒绝退款", "质量问题", "退货退款", "经营者义务"),
                    List.of(new RefSeed(CONSUMER_PROTECTION_LAW, "第二十三条"), new RefSeed(CONSUMER_PROTECTION_LAW, "第二十四条"))),
            new QuestionSeed("C06", CONSUMER,
                    "我在健身房办了年卡，教练承诺\"一个月瘦十斤\"，结果三个月下来体重没变化。我要求退卡，健身房说课程已经上了，不能退。请问健身房这种承诺能作为我要求退款的依据吗？",
                    List.of("服务消费", "虚假宣传", "退费", "经营者违约"),
                    List.of(new RefSeed(CONSUMER_PROTECTION_LAW, "第二十条"))),
            new QuestionSeed("C07", CONSUMER,
                    "我在一家早教机构给孩子报了两年课程，交了两万多块钱。半年后机构突然关门跑路，负责人联系不上，还有很多家长的钱都没退。请问预付了学费机构跑路，我们消费者该怎么追回这笔钱？",
                    List.of("预付消费", "预付款", "经营者跑路", "退还未消费费用"),
                    List.of(new RefSeed(CONSUMER_PROTECTION_LAW, "第五十三条"))),
            new QuestionSeed("C08", CONSUMER,
                    "我在网上买了一个充电宝，充电时突然起火，把家里的床头柜烧坏了，人也轻微烫伤。找商家，商家只同意退充电宝的钱，拒绝赔偿其他损失。请问商品造成人身财产损害，我可以要求哪些赔偿？",
                    List.of("商品缺陷", "人身财产损害", "损害赔偿", "侵权责任"),
                    List.of(new RefSeed(CONSUMER_PROTECTION_LAW, "第五十二条"), new RefSeed(CONSUMER_PROTECTION_LAW, "第四十九条"))),
            new QuestionSeed("C09", CONSUMER,
                    "我买演唱会门票时看到票面写着\"一经售出概不退换\"。后来因为台风演出取消了，主办方却说票面有约定，不退票。请问票面上这种\"概不退换\"的条款有效吗？",
                    List.of("格式条款", "不公平不合理", "免除经营者责任", "无效条款"),
                    List.of(new RefSeed(CONSUMER_PROTECTION_LAW, "第二十六条"))),
            new QuestionSeed("C10", CONSUMER,
                    "我在一家手机店买了一部\"全新国行\"手机，用了一个月发现是翻新机，找店家理论，店家不承认。我当时没开发票，只有微信转账记录和聊天记录。请问我手头这些材料能作为维权证据吗？维权前应该收集哪些证据？",
                    List.of("维权证据", "发票凭证", "转账记录", "聊天记录", "投诉举报途径"),
                    List.of(new RefSeed(CONSUMER_PROTECTION_LAW, "第二十二条"), new RefSeed(CONSUMER_PROTECTION_LAW, "第三十九条")))
    );
}
