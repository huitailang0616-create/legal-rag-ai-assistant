package com.ailegal.service.impl;

import com.ailegal.dto.ProcessGuideResponse;
import com.ailegal.dto.ProcessGuideResponse.Channel;
import com.ailegal.dto.ProcessGuideResponse.Faq;
import com.ailegal.dto.ProcessGuideResponse.Step;
import com.ailegal.service.ProcessGuideService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 固定办事流程指引（模块5A）。Java 固定结构 + 只读 API：
 * 内容无写入、无历史、无按用户状态，不建 DB 表、不做 CMS、无动态规则引擎。
 * 单一事实来源：5B 生成文书如需把对应领域流程拼入 Prompt，直接复用本服务。
 * 三领域统一六个分区：steps/materials/jurisdiction/precautions/faqs/officialChannels。
 * 内容原则：简明、保守、面向普通用户、不做个案法律结论；不写具体办理机关名称、
 * 法定期限天数、费用、电话或 URL；官方渠道仅全国统一官方热线与机构类别名称。
 */
@Service
public class ProcessGuideServiceImpl implements ProcessGuideService {

    /** 统一提示：一般性法律信息 + 以实际受理机构最新要求为准 */
    private static final String DISCLAIMER =
            "流程指引为一般性法律信息，具体办理要求可能因地区、案件情况及主管机关要求不同而有所差异，"
                    + "办理前请通过当地官方渠道核实。具体材料、受理条件及流程以实际受理机构最新要求为准。";

    private static final List<ProcessGuideResponse> GUIDES = List.of(
            laborGuide(),
            houseRentalGuide(),
            consumerRightsGuide()
    );

    @Override
    public List<ProcessGuideResponse> listAll() {
        return GUIDES;
    }

    private static ProcessGuideResponse laborGuide() {
        ProcessGuideResponse g = new ProcessGuideResponse();
        g.setDomain("LABOR_DISPUTE");
        g.setTitle("劳动争议处理流程");
        g.setDisclaimer(DISCLAIMER);
        g.setSteps(List.of(
                new Step(1, "整理劳动关系与争议材料",
                        "梳理劳动合同、工资支付记录、考勤记录、解除或终止劳动关系证明等与争议相关的材料。",
                        List.of("劳动合同", "工资支付记录", "考勤记录", "解除/终止劳动关系证明")),
                new Step(2, "与用人单位沟通",
                        "就争议事项与用人单位进行沟通，明确双方争议焦点。",
                        null),
                new Step(3, "准备仲裁申请材料",
                        "按要求准备仲裁申请书及与争议相关的证据材料。",
                        List.of("仲裁申请书", "身份证明", "争议相关证据")),
                new Step(4, "向有管辖权的劳动人事争议仲裁机构提交",
                        "向有管辖权的劳动人事争议仲裁委员会提交仲裁申请。",
                        null),
                new Step(5, "参加调解/仲裁程序",
                        "按通知参加调解或仲裁庭审程序，如实陈述争议事实。",
                        null),
                new Step(6, "根据处理结果决定后续依法处理",
                        "仲裁裁决作出后，如对结果不服，可在法定期限内依法通过诉讼等途径主张权利。",
                        null)
        ));
        g.setMaterials(List.of(
                "身份信息材料（如身份证）",
                "劳动关系证明材料（劳动合同、社保记录、工资发放记录等，视情况）",
                "工资支付资料（工资条、银行流水等）",
                "考勤记录（如争议涉及加班、考勤）",
                "解除或终止劳动关系材料（如争议涉及解除）",
                "相关沟通证据（微信、邮件、录音等，注意保留原始载体）",
                "以上为常见材料类别，并非所有案件都必须全部提供，具体以受理机构要求为准"
        ));
        g.setJurisdiction(List.of(
                "劳动争议仲裁案件由劳动合同履行地或者用人单位所在地的劳动人事争议仲裁委员会管辖。",
                "劳动合同履行地与用人单位所在地不一致的，可根据自身情况选择向其中之一申请仲裁。",
                "对仲裁裁决不服的，可以在法定期限内依法向人民法院提起诉讼。",
                "具体案件由哪个机构受理，以法律规定及受理机构认定为准，办理前可通过当地官方渠道核实。"
        ));
        g.setPrecautions(List.of(
                "注意保存证据的原始载体，避免仅保留截图或转发副本。",
                "劳动争议处理有法定时效规定，建议尽早启动处理程序。",
                "仲裁申请与投诉材料中的请求事项应明确、具体，避免请求不明确。",
                "提供的材料应当真实，不得伪造证据。",
                "涉及金额较大或情况复杂的，可先咨询当地法律援助机构或专业法律人士。"
        ));
        g.setFaqs(List.of(
                new Faq("没有签劳动合同，可以申请劳动仲裁吗？",
                        "可以申请。能否获得支持取决于案件事实与证据，建议先整理工资支付、考勤、工作沟通等证明材料，向有管辖权的劳动人事争议仲裁委员会咨询或申请。"),
                new Faq("劳动仲裁收费吗？",
                        "劳动人事争议仲裁不收取仲裁费用，具体程序与材料要求以当地仲裁机构规定为准。"),
                new Faq("被拖欠工资，应该先做什么？",
                        "建议先与用人单位书面沟通并保留沟通记录，同时整理劳动关系与工资支付证据；协商不成可向当地劳动保障监察机构反映，或依法申请劳动仲裁。"),
                new Faq("仲裁后对结果不服怎么办？",
                        "可以在法定期限内依法向人民法院提起诉讼；具体期限与程序以法律规定为准，建议尽快处理并咨询当地官方渠道。")
        ));
        g.setOfficialChannels(List.of(
                new Channel("12333 人力资源和社会保障服务热线", "劳动保障政策咨询、劳动监察投诉举报"),
                new Channel("当地劳动人事争议仲裁委员会", "劳动争议仲裁申请与案件受理"),
                new Channel("12348 法律服务热线", "免费法律咨询与法律援助指引")
        ));
        return g;
    }

    private static ProcessGuideResponse houseRentalGuide() {
        ProcessGuideResponse g = new ProcessGuideResponse();
        g.setDomain("HOUSE_RENTAL");
        g.setTitle("房屋租赁纠纷处理流程");
        g.setDisclaimer(DISCLAIMER);
        g.setSteps(List.of(
                new Step(1, "整理合同/付款/房屋状态材料",
                        "收集租赁合同、租金及押金支付凭证、房屋交付与现状记录等材料。",
                        List.of("租赁合同", "租金/押金支付凭证", "房屋交付与现状记录")),
                new Step(2, "与出租人/承租人沟通",
                        "就争议事项与合同相对方进行沟通，尝试协商解决。",
                        null),
                new Step(3, "固定证据",
                        "对沟通记录、房屋状况、付款记录等关键证据进行保存与固定。",
                        null),
                new Step(4, "根据争议选择调解/投诉/诉讼等适当途径",
                        "协商不成时，根据争议性质选择人民调解、向有关主管部门反映或依法诉讼等适当途径。",
                        null),
                new Step(5, "按受理机构要求提交材料",
                        "按受理机构要求提交申请与证明材料。",
                        null),
                new Step(6, "跟进处理结果",
                        "跟进调解、投诉或诉讼进展，根据处理结果决定后续依法处理。",
                        null)
        ));
        g.setMaterials(List.of(
                "租赁合同及补充协议",
                "租金、押金支付凭证（转账记录、收据等）",
                "房屋交付与退租交接记录（交接单、照片、视频等）",
                "维修、沟通记录（聊天记录、邮件、函件等）",
                "房屋现状照片或视频（如涉及损坏定责）",
                "以上为常见材料类别，并非所有案件都必须全部提供，具体以受理机构要求为准"
        ));
        g.setJurisdiction(List.of(
                "房屋租赁纠纷属于民事纠纷，进入诉讼程序时一般由被告住所地或者合同履行地人民法院管辖。",
                "租赁合同中就争议解决有约定管辖条款的，在不违反法律规定的前提下按约定办理。",
                "人民调解等途径不收取费用或费用较低，协商不成时可优先考虑。",
                "具体案件由哪个机构受理，以法律规定及受理机构认定为准，办理前可通过当地官方渠道核实。"
        ));
        g.setPrecautions(List.of(
                "保留租赁合同原件与全部付款凭证。",
                "退租交接时与对方共同确认房屋状态并留存记录。",
                "发现纠纷尽早处理，避免长期搁置导致证据流失。",
                "维修责任、违约金等以合同约定为优先，注意保存书面沟通记录。",
                "涉及房屋损坏定责时，及时拍照、录像固定现场状态。"
        ));
        g.setFaqs(List.of(
                new Faq("房东不退押金怎么办？",
                        "先核对租赁合同中押金退还的条件与约定，与房东书面沟通并保留记录；协商不成可通过人民调解、向有关主管部门反映或依法诉讼等途径处理。"),
                new Faq("房屋维修费用由谁承担？",
                        "一般以租赁合同约定为准；合同未约定的，按相关法律规定确定。建议对损坏情况拍照记录并及时书面通知对方。"),
                new Faq("合同到期后还能继续居住吗？",
                        "合同到期后继续使用房屋的，通常需要与出租人重新协商续租或按合同约定处理；建议提前沟通并签订书面续租约定。"),
                new Faq("租金逾期会有什么后果？",
                        "以合同约定为准，一般可能涉及违约金或解除合同；建议及时与对方沟通并保存协商记录。")
        ));
        g.setOfficialChannels(List.of(
                new Channel("12345 政务服务便民热线", "房屋租赁相关投诉、咨询与转办"),
                new Channel("当地人民法院诉讼服务中心", "民事立案与诉讼程序咨询"),
                new Channel("12348 法律服务热线", "免费法律咨询与法律援助指引")
        ));
        return g;
    }

    private static ProcessGuideResponse consumerRightsGuide() {
        ProcessGuideResponse g = new ProcessGuideResponse();
        g.setDomain("CONSUMER_RIGHTS");
        g.setTitle("消费者权益纠纷处理流程");
        g.setDisclaimer(DISCLAIMER);
        g.setSteps(List.of(
                new Step(1, "保存订单/付款/商品/沟通证据",
                        "保存订单信息、付款凭证、商品实物与描述、与经营者的沟通记录等证据。",
                        List.of("订单截图", "付款凭证", "商品描述与实物", "沟通记录")),
                new Step(2, "与经营者协商",
                        "就商品或服务质量问题与经营者直接协商，提出合理诉求。",
                        null),
                new Step(3, "使用平台售后渠道",
                        "通过交易平台售后或投诉渠道提交问题与证据。",
                        null),
                new Step(4, "必要时向消费者组织或市场监管相关渠道反映",
                        "协商与平台处理未果时，可向消费者组织或市场监督管理相关渠道反映。",
                        null),
                new Step(5, "仍无法解决时依法选择后续争议解决途径",
                        "仍无法解决时，依法选择仲裁、诉讼等后续争议解决途径。",
                        null)
        ));
        g.setMaterials(List.of(
                "订单信息与商品/服务页面截图",
                "支付凭证（付款记录、发票等）",
                "商品实物或服务相关证据（照片、视频、检测报告等，如适用）",
                "与经营者的沟通记录（聊天、邮件、电话记录等）",
                "维修、换货等处理材料（如适用）",
                "以上为常见材料类别，并非所有案件都必须全部提供，具体以受理机构要求为准"
        ));
        g.setJurisdiction(List.of(
                "消费纠纷的投诉、调解一般向经营者所在地或消费行为发生地的相关主管部门提出。",
                "消费者权益纠纷属于民事纠纷，进入诉讼程序时一般由被告住所地或者合同履行地人民法院管辖；有约定管辖条款的按约定办理。",
                "小额消费纠纷可优先通过法定的投诉调解渠道处理，不一定需要进入诉讼。",
                "具体案件由哪个机构受理，以法律规定及受理机构认定为准，办理前可通过当地官方渠道核实。"
        ));
        g.setPrecautions(List.of(
                "保存订单、支付与商品描述等证据，避免清理聊天记录导致证据丢失。",
                "对商家宣传内容留存页面截图（如涉及夸大宣传）。",
                "发现问题后尽早与经营者沟通，保留书面沟通记录。",
                "不要伪造消费记录或证据。",
                "涉及人身健康、安全的产品问题，及时停止使用并保存包装与凭证。"
        ));
        g.setFaqs(List.of(
                new Faq("网购商品出现质量问题，怎么维权？",
                        "先与经营者协商退换货并保留聊天记录；协商不成可向经营者所在地或平台所在地的市场监督管理部门投诉，或依法通过其他法定途径解决。"),
                new Faq("12315 是做什么的？",
                        "12315 是市场监管部门受理消费者投诉、举报的服务热线，可对消费纠纷进行登记并转交处理。"),
                new Faq("商家拒不处理，还有哪些途径？",
                        "可向消费者协会等消费者组织申请调解，或向市场监督管理部门投诉；仍无法解决的可依法选择诉讼等争议解决途径。"),
                new Faq("退货要求被拒绝，是否只能起诉？",
                        "不一定。可先通过平台售后、消费者组织调解或行政投诉等渠道处理，诉讼只是法定途径之一，具体根据案件情况选择。")
        ));
        g.setOfficialChannels(List.of(
                new Channel("12315 市场监管投诉举报热线", "消费者投诉举报与消费维权"),
                new Channel("当地消费者协会（消费者权益保护组织）", "消费纠纷调解与咨询"),
                new Channel("12348 法律服务热线", "免费法律咨询与法律援助指引")
        ));
        return g;
    }
}
