package com.ailegal.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 新建案件分析草稿请求。仅包含用户可编辑字段；
 * analysisStatus / extractionJson / riskResultJson / analysisResult
 * 由服务端控制，不接受客户端传入。
 */
public class CaseAnalysisCreateRequest {

    @NotBlank(message = "案件标题不能为空")
    @Size(max = 128, message = "案件标题长度不能超过128字符")
    private String title;

    @NotBlank(message = "案件领域不能为空")
    @Pattern(regexp = "^(LABOR_DISPUTE|HOUSE_RENTAL|CONSUMER_RIGHTS)$",
            message = "案件领域只能是劳动争议、房屋租赁或消费者权益")
    private String domain;

    @NotBlank(message = "案件基本事实不能为空")
    @Size(max = 5000, message = "案件基本事实长度不能超过5000字符")
    private String caseFacts;

    @Size(max = 2000, message = "诉求长度不能超过2000字符")
    private String claims;

    @Size(max = 2000, message = "关键时间长度不能超过2000字符")
    private String keyDates;

    @Size(max = 3000, message = "现有证据说明长度不能超过3000字符")
    private String evidenceDescription;

    @Size(max = 500, message = "对方主体信息长度不能超过500字符")
    private String counterpartyDescription;

    @Size(max = 500, message = "地点信息长度不能超过500字符")
    private String locationDescription;

    /** 涉及金额（元），可空；对应 amount decimal(15,2)（13位整数+2位小数） */
    @Digits(integer = 13, fraction = 2, message = "涉及金额格式不正确（最多13位整数、2位小数）")
    private BigDecimal amount;

    /** 合同情况，可空（null 归一化为 UNKNOWN）；对应 contract_status 枚举 */
    @Pattern(regexp = "^(SIGNED|ORAL|UNSIGNED|UNKNOWN)$",
            message = "合同情况只能是已签书面合同、口头约定、未签合同或不清楚")
    private String contractStatus;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getCaseFacts() { return caseFacts; }
    public void setCaseFacts(String caseFacts) { this.caseFacts = caseFacts; }
    public String getClaims() { return claims; }
    public void setClaims(String claims) { this.claims = claims; }
    public String getKeyDates() { return keyDates; }
    public void setKeyDates(String keyDates) { this.keyDates = keyDates; }
    public String getEvidenceDescription() { return evidenceDescription; }
    public void setEvidenceDescription(String evidenceDescription) { this.evidenceDescription = evidenceDescription; }
    public String getCounterpartyDescription() { return counterpartyDescription; }
    public void setCounterpartyDescription(String counterpartyDescription) { this.counterpartyDescription = counterpartyDescription; }
    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String locationDescription) { this.locationDescription = locationDescription; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getContractStatus() { return contractStatus; }
    public void setContractStatus(String contractStatus) { this.contractStatus = contractStatus; }
}
