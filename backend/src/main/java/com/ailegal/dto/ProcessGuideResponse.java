package com.ailegal.dto;

import java.util.List;

/**
 * 固定办事流程指引响应（模块5A）。
 * 内容为 Java 固定结构，非 DB、非 CMS；按 domain 返回三个正式领域的固定流程。
 * 六个固定分区：steps（办理步骤）/ materials（所需材料）/ jurisdiction（管辖原则）/
 * precautions（注意事项）/ faqs（常见问题）/ officialChannels（官方查询渠道）。
 * 内容保守、面向普通用户：不写胜诉概率、确定金额、未经验证的固定时限、具体办理机关
 * 名称、电话或 URL；管辖只给原则不给个案认定；FAQ 不做个案法律结论。
 */
public class ProcessGuideResponse {

    /** 领域：LABOR_DISPUTE / HOUSE_RENTAL / CONSUMER_RIGHTS */
    private String domain;
    /** 流程标题（中文） */
    private String title;
    /** 固定步骤列表 */
    private List<Step> steps;
    /** 所需材料（常见材料类别，非全部必需） */
    private List<String> materials;
    /** 管辖原则（保守原则说明，不替具体案件认定） */
    private List<String> jurisdiction;
    /** 注意事项（实用提醒） */
    private List<String> precautions;
    /** 常见问题（固定 FAQ） */
    private List<Faq> faqs;
    /** 官方查询渠道（稳定官方渠道名称与用途，不含未经确认的第三方链接） */
    private List<Channel> officialChannels;
    /** 统一提示：具体材料、受理条件及流程以实际受理机构最新要求为准 */
    private String disclaimer;

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<Step> getSteps() { return steps; }
    public void setSteps(List<Step> steps) { this.steps = steps; }
    public List<String> getMaterials() { return materials; }
    public void setMaterials(List<String> materials) { this.materials = materials; }
    public List<String> getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(List<String> jurisdiction) { this.jurisdiction = jurisdiction; }
    public List<String> getPrecautions() { return precautions; }
    public void setPrecautions(List<String> precautions) { this.precautions = precautions; }
    public List<Faq> getFaqs() { return faqs; }
    public void setFaqs(List<Faq> faqs) { this.faqs = faqs; }
    public List<Channel> getOfficialChannels() { return officialChannels; }
    public void setOfficialChannels(List<Channel> officialChannels) { this.officialChannels = officialChannels; }
    public String getDisclaimer() { return disclaimer; }
    public void setDisclaimer(String disclaimer) { this.disclaimer = disclaimer; }

    /** 常见问题：question + answer，回答简洁通用、不做个案结论 */
    public static class Faq {
        private String question;
        private String answer;

        public Faq() {}

        public Faq(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
    }

    /** 官方查询渠道：稳定官方渠道名称 + 主要用途说明（不要求 URL） */
    public static class Channel {
        private String name;
        private String note;

        public Channel() {}

        public Channel(String name, String note) {
            this.name = name;
            this.note = note;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public static class Step {
        /** 步骤序号，从 1 开始 */
        private int order;
        /** 步骤名称 */
        private String name;
        /** 步骤说明（通用、保守描述） */
        private String description;
        /** 可选：该步骤常见材料提示 */
        private List<String> materials;

        public Step() {}

        public Step(int order, String name, String description, List<String> materials) {
            this.order = order;
            this.name = name;
            this.description = description;
            this.materials = materials;
        }

        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getMaterials() { return materials; }
        public void setMaterials(List<String> materials) { this.materials = materials; }
    }
}
