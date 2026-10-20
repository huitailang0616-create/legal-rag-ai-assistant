package com.ailegal.service.impl;

import com.ailegal.dto.ProcessGuideResponse;
import com.ailegal.dto.ProcessGuideResponse.Channel;
import com.ailegal.dto.ProcessGuideResponse.Faq;
import com.ailegal.dto.ProcessGuideResponse.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Final-Requirement-Gaps-D 聚焦回归（模块5A 流程指引）：
 * 三领域统一六分区（steps/materials/jurisdiction/precautions/faqs/officialChannels），
 * FAQ≥3、官方渠道≥2、无空值/空串；免责声明含任务书固定文案。
 * 固定文本 Java 常量，无 DB/无 LLM，纯静态数据断言。
 */
class ProcessGuideServiceImplTest {

    private static final ProcessGuideServiceImpl SERVICE = new ProcessGuideServiceImpl();
    private static final List<ProcessGuideResponse> GUIDES = SERVICE.listAll();

    private static final String[] EXPECTED_DOMAINS = {
            "LABOR_DISPUTE", "HOUSE_RENTAL", "CONSUMER_RIGHTS"
    };
    private static final String DISCLAIMER_MARK =
            "流程指引为一般性法律信息，具体办理要求可能因地区、案件情况及主管机关要求不同而有所差异";

    @Test
    @DisplayName("GapsD-结构：恰好三个正式领域，六分区齐全且无空值/空串")
    void allDomains_haveSixSections() {
        assertEquals(3, GUIDES.size());
        for (int i = 0; i < GUIDES.size(); i++) {
            ProcessGuideResponse g = GUIDES.get(i);
            assertEquals(EXPECTED_DOMAINS[i], g.getDomain(), "domain 顺序：" + EXPECTED_DOMAINS[i]);
            assertFalse(g.getTitle() == null || g.getTitle().isBlank(), "title");
            assertTrue(g.getSteps() != null && g.getSteps().size() >= 5, "steps 数");
            for (Step s : g.getSteps()) {
                assertTrue(s.getOrder() >= 1 && s.getName() != null && !s.getName().isBlank(), "step 名称");
                assertTrue(s.getDescription() != null && !s.getDescription().isBlank(), "step 说明");
            }
            assertNotEmpty(g.getMaterials(), "materials");
            assertNotEmpty(g.getJurisdiction(), "jurisdiction");
            assertNotEmpty(g.getPrecautions(), "precautions");
            assertNotNull(g.getFaqs(), "faqs");
            assertNotNull(g.getOfficialChannels(), "officialChannels");
            for (Faq f : g.getFaqs()) {
                assertFalse(f.getQuestion() == null || f.getQuestion().isBlank(), "FAQ question");
                assertFalse(f.getAnswer() == null || f.getAnswer().isBlank(), "FAQ answer");
            }
            for (Channel c : g.getOfficialChannels()) {
                assertFalse(c.getName() == null || c.getName().isBlank(), "channel name");
                assertFalse(c.getNote() == null || c.getNote().isBlank(), "channel note");
            }
            assertTrue(g.getDisclaimer() != null && g.getDisclaimer().contains(DISCLAIMER_MARK),
                    "免责声明含任务书固定文案");
        }
    }

    @Test
    @DisplayName("GapsD-数量：每领域 FAQ≥3、官方渠道≥2、材料/管辖/注意事项非空")
    void allDomains_meetMinimumCounts() {
        for (ProcessGuideResponse g : GUIDES) {
            assertTrue(g.getFaqs().size() >= 3, g.getDomain() + " FAQ 数≥3，实际 " + g.getFaqs().size());
            assertTrue(g.getOfficialChannels().size() >= 2,
                    g.getDomain() + " 渠道数≥2，实际 " + g.getOfficialChannels().size());
            assertTrue(g.getMaterials().size() >= 5, g.getDomain() + " materials 数");
            assertTrue(g.getJurisdiction().size() >= 3, g.getDomain() + " jurisdiction 数");
            assertTrue(g.getPrecautions().size() >= 3, g.getDomain() + " precautions 数");
            // 官方渠道名称必须为稳定官方渠道（全国统一热线或机构类别名），不包含疑似第三方导流
            for (Channel c : g.getOfficialChannels()) {
                assertFalse(c.getName().contains("http"), "渠道不含 URL");
            }
        }
    }

    private static void assertNotEmpty(List<String> list, String label) {
        assertNotNull(list, label + " 非空");
        assertFalse(list.isEmpty(), label + " 非空");
        for (String s : list) {
            assertFalse(s == null || s.isBlank(), label + " 无空串");
        }
    }
}
