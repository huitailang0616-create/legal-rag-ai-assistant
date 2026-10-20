<template>
  <div class="case-analysis">
    <div class="content">
      <h1 class="page-title">案件分析</h1>
      <p class="page-sub">
        结构化填写案件事实，AI 将辅助提取核心诉求、争议焦点和证据信息。AI 提取结果需要人工确认后，才能作为后续咨询和文书生成的事实背景。
      </p>

      <!-- 新建案件分析（① 填写案件） -->
      <el-card v-if="showCreate" class="create-card" shadow="never">
        <template #header>
          <div class="card-head">
            <span class="card-title">新建案件分析</span>
            <span class="stage-tag">① 填写案件</span>
          </div>
        </template>

        <el-form
          ref="createFormRef"
          :model="createForm"
          :rules="formRules"
          label-width="110px"
          label-position="left"
        >
          <div class="form-group">
            <div class="group-title">基本情况</div>
            <el-form-item label="案件标题" prop="title">
              <el-input v-model="createForm.title" maxlength="128" show-word-limit placeholder="例如：未签劳动合同工资纠纷" />
            </el-form-item>
            <el-form-item label="法律领域" prop="domain">
              <el-select v-model="createForm.domain" placeholder="请选择案件领域" style="width:100%">
                <el-option v-for="d in domainOptions" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="对方主体" prop="counterpartyDescription">
              <el-input v-model="createForm.counterpartyDescription" maxlength="500" show-word-limit placeholder="公司 / 房东 / 商家等对方主体信息" />
            </el-form-item>
            <el-form-item label="地点" prop="locationDescription">
              <el-input v-model="createForm.locationDescription" maxlength="500" show-word-limit placeholder="案件发生地 / 工作地 / 房屋所在地 / 消费地点等" />
            </el-form-item>
          </div>

          <div class="form-group">
            <div class="group-title">关键事实</div>
            <el-form-item label="案件基本事实" prop="caseFacts">
              <el-input
                v-model="createForm.caseFacts"
                type="textarea"
                :rows="4"
                maxlength="5000"
                show-word-limit
                placeholder="请描述案件的基本事实经过"
              />
            </el-form-item>
            <el-form-item label="我的诉求" prop="claims">
              <el-input
                v-model="createForm.claims"
                type="textarea"
                :rows="3"
                maxlength="2000"
                show-word-limit
                placeholder="您希望解决的问题或诉求"
              />
            </el-form-item>
            <el-form-item label="关键时间" prop="keyDates">
              <el-input
                v-model="createForm.keyDates"
                type="textarea"
                :rows="2"
                maxlength="2000"
                show-word-limit
                placeholder="例如：入职时间、签约时间、退房时间、购买时间、争议发生时间等"
              />
            </el-form-item>
            <el-form-item label="涉及金额（元）" prop="amount">
              <el-input-number
                v-model="createForm.amount"
                :min="0"
                :max="9999999999999.99"
                :precision="2"
                :controls="false"
                placeholder="如 12000，可不填"
                style="width:100%"
              />
            </el-form-item>
            <el-form-item label="合同情况" prop="contractStatus">
              <el-select v-model="createForm.contractStatus" style="width:100%">
                <el-option v-for="c in contractStatusOptions" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
            </el-form-item>
          </div>

          <div class="form-group">
            <div class="group-title">已有材料</div>
            <el-form-item label="现有证据" prop="evidenceDescription">
              <el-input
                v-model="createForm.evidenceDescription"
                type="textarea"
                :rows="2"
                maxlength="3000"
                show-word-limit
                placeholder="您目前掌握的证据说明，如转账记录、聊天记录、合同等"
              />
            </el-form-item>
          </div>

          <el-form-item>
            <el-button type="primary" :loading="saving" @click="handleCreate">保存案件</el-button>
            <el-button @click="showCreate = false">取消</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 案件记录 -->
      <el-card class="record-card" shadow="never">
        <template #header>
          <div class="card-head">
            <span class="card-title">案件记录</span>
            <el-button type="primary" plain size="small" @click="openCreate">新建案件分析</el-button>
          </div>
        </template>

        <el-empty v-if="!loading && caseList.length === 0" description="还没有案件分析">
          <p class="empty-desc">你可以先填写案件事实，再使用 AI 辅助提取诉求、争议焦点和证据。</p>
          <el-button type="primary" @click="openCreate">新建案件分析</el-button>
        </el-empty>

        <el-table v-else :data="caseList" v-loading="loading">
          <el-table-column prop="title" label="案件标题" min-width="200" show-overflow-tooltip />
          <el-table-column label="领域" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="domainTagType(row.domain)">{{ domainLabel(row.domain) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="确认状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="caseStatus(row).type">{{ caseStatus(row).text }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="handleEdit(row)">查看/编辑</el-button>
              <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 当前案件工作区（查看 / 编辑 / AI 提取 / 确认 / 风险 / 后续行动） -->
    <el-dialog
      v-model="editDialogVisible"
      width="820px"
      top="4vh"
      destroy-on-close
    >
      <template #header>
        <div class="work-header">
          <div class="work-header-left">
            <el-button text size="small" @click="editDialogVisible = false">← 返回案件列表</el-button>
            <span class="work-title">{{ editForm.title }}</span>
          </div>
          <div class="work-header-right">
            <el-tag size="small" :type="domainTagType(editForm.domain)">{{ domainLabel(editForm.domain) }}</el-tag>
            <el-tag size="small" :type="workStatus.type" effect="light">{{ workStatus.text }}</el-tag>
          </div>
        </div>
      </template>

      <!-- 步骤指示：由真实数据状态决定，不是独立业务状态机 -->
      <el-steps :active="stepActive" finish-status="success" class="work-steps">
        <el-step title="填写案件" />
        <el-step title="AI 提取" />
        <el-step title="人工确认" />
        <el-step title="风险分析" />
        <el-step title="后续行动" />
      </el-steps>

      <!-- 案件资料（分组） -->
      <el-collapse v-model="basicActive" class="basic-collapse">
        <el-collapse-item name="basic" title="案件资料（点击展开查看 / 编辑）">
          <el-form ref="editFormRef" :model="editForm" :rules="formRules" label-width="110px" label-position="left">
            <div class="form-group">
              <div class="group-title">基本情况</div>
              <el-form-item label="案件标题" prop="title">
                <el-input v-model="editForm.title" maxlength="128" show-word-limit />
              </el-form-item>
              <el-form-item label="法律领域" prop="domain">
                <el-select v-model="editForm.domain" placeholder="请选择案件领域" style="width:100%">
                  <el-option v-for="d in domainOptions" :key="d.value" :label="d.label" :value="d.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="对方主体" prop="counterpartyDescription">
                <el-input v-model="editForm.counterpartyDescription" maxlength="500" show-word-limit />
              </el-form-item>
              <el-form-item label="地点" prop="locationDescription">
                <el-input v-model="editForm.locationDescription" maxlength="500" show-word-limit />
              </el-form-item>
            </div>

            <div class="form-group">
              <div class="group-title">关键事实</div>
              <el-form-item label="案件基本事实" prop="caseFacts">
                <el-input v-model="editForm.caseFacts" type="textarea" :rows="4" maxlength="5000" show-word-limit />
              </el-form-item>
              <el-form-item label="我的诉求" prop="claims">
                <el-input v-model="editForm.claims" type="textarea" :rows="3" maxlength="2000" show-word-limit />
              </el-form-item>
              <el-form-item label="关键时间" prop="keyDates">
                <el-input v-model="editForm.keyDates" type="textarea" :rows="2" maxlength="2000" show-word-limit />
              </el-form-item>
              <el-form-item label="涉及金额（元）" prop="amount">
                <el-input-number
                  v-model="editForm.amount"
                  :min="0"
                  :max="9999999999999.99"
                  :precision="2"
                  :controls="false"
                  placeholder="如 12000，可不填"
                  style="width:100%"
                />
              </el-form-item>
              <el-form-item label="合同情况" prop="contractStatus">
                <el-select v-model="editForm.contractStatus" style="width:100%">
                  <el-option v-for="c in contractStatusOptions" :key="c.value" :label="c.label" :value="c.value" />
                </el-select>
              </el-form-item>
            </div>

            <div class="form-group">
              <div class="group-title">已有材料</div>
              <el-form-item label="现有证据" prop="evidenceDescription">
                <el-input v-model="editForm.evidenceDescription" type="textarea" :rows="2" maxlength="3000" show-word-limit />
              </el-form-item>
            </div>

            <el-form-item>
              <el-button type="primary" plain :loading="saving" @click="handleSaveEdit">保存案件资料</el-button>
              <span class="form-tip">保存案件资料将清空当前的 AI 提取结果与确认状态。</span>
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </el-collapse>

      <!-- 阶段 ② AI 提取入口（案件已保存，尚无 extraction） -->
      <template v-if="!extraction">
        <div class="section">
          <div class="section-title">AI 提取</div>
          <p class="section-tip">
            AI 将根据你填写的信息提取案件类型、核心诉求、关键事实、争议焦点和证据信息。提取结果需要你人工检查并确认。
          </p>
          <div class="primary-action">
            <el-button
              type="primary"
              size="large"
              :loading="extracting"
              :disabled="extracting"
              @click="handleExtract"
            >AI 提取案件要点</el-button>
            <span v-if="extracting" class="action-hint">正在分析案件信息…</span>
          </div>
        </div>
      </template>

      <!-- 阶段 ③ 人工确认 / 已确认：AI 提取结果（8 字段全部可编辑） -->
      <template v-else>
        <div class="section">
          <div class="section-title">AI 提取结果</div>
          <div class="result-status">
            <el-tag :type="confirmed ? 'success' : 'warning'" effect="light">
              {{ confirmed ? '✓ 已人工确认' : '待人工确认' }}
            </el-tag>
            <span class="status-note">
              {{ confirmed
                ? '以下信息已由你确认，将作为后续咨询或文书生成的事实背景。'
                : '请检查以下内容，如有不准确可直接修改。' }}
            </span>
          </div>

          <!-- 8 字段视觉分组 -->
          <div class="extract-group">
            <div class="group-title">案件摘要</div>
            <el-input v-model="extraction.caseSummary" type="textarea" :rows="2" maxlength="1000" show-word-limit />
          </div>

          <div class="extract-group">
            <div class="group-title">核心诉求</div>
            <div v-for="(c, i) in extraction.claims" :key="i" class="row-line">
              <el-input v-model="extraction.claims[i]" maxlength="200" placeholder="诉求内容" />
              <el-button size="small" type="danger" link @click="removeClaim(i)">删除</el-button>
            </div>
            <el-button size="small" type="primary" link @click="addClaim">+ 添加诉求</el-button>
            <el-empty v-if="!extraction.claims.length" description="未明确表达诉求" :image-size="40" />
          </div>

          <div class="extract-group">
            <div class="group-title">争议焦点</div>
            <el-input v-model="extraction.disputeFocus" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </div>

          <div class="extract-group">
            <div class="group-title">关键时间</div>
            <div v-for="(d, i) in extraction.keyDates" :key="i" class="row-line">
              <el-input v-model="d.event" maxlength="100" placeholder="事件（如：入职）" class="row-col" />
              <el-input v-model="d.dateText" maxlength="100" placeholder="时间（保留原文表达）" class="row-col" />
              <el-button size="small" type="danger" link @click="removeKeyDate(i)">删除</el-button>
            </div>
            <el-button size="small" type="primary" link @click="addKeyDate">+ 添加关键时间</el-button>
            <el-empty v-if="!extraction.keyDates.length" description="未提供关键时间" :image-size="40" />
          </div>

          <div class="extract-group">
            <div class="group-title">已有证据</div>
            <div v-for="(e, i) in extraction.evidenceItems" :key="i" class="row-line">
              <el-input v-model="e.name" maxlength="200" placeholder="证据名称" class="row-col" />
              <el-input v-model="e.description" maxlength="500" placeholder="说明（可空）" class="row-col" />
              <el-button size="small" type="danger" link @click="removeEvidence(i)">删除</el-button>
            </div>
            <el-button size="small" type="primary" link @click="addEvidence">+ 添加证据</el-button>
            <el-empty v-if="!extraction.evidenceItems.length" description="未提供证据信息" :image-size="40" />
          </div>

          <div class="extract-group">
            <div class="group-title">对方主体与地点</div>
            <el-input v-model="extraction.counterparty.description" maxlength="500" placeholder="对方主体信息（未提供可留空）" class="sub-input" />
            <el-input v-model="extraction.location.description" maxlength="500" placeholder="案件发生地（未提供可留空）" class="sub-input" />
          </div>

          <div class="extract-group">
            <div class="group-title">仍需补充的信息</div>
            <div v-for="(m, i) in extraction.missingInformation" :key="i" class="row-line">
              <el-input v-model="extraction.missingInformation[i]" maxlength="200" placeholder="缺失的信息项" />
              <el-button size="small" type="danger" link @click="removeMissing(i)">删除</el-button>
            </div>
            <el-button size="small" type="primary" link @click="addMissing">+ 添加缺失信息</el-button>
            <el-empty v-if="!extraction.missingInformation.length" description="未列出额外信息缺口" :image-size="40" />
          </div>

          <!-- 确认动作：未确认时突出；已确认时改为"保存修改"（同一确认端点，忠实保持现有编辑语义） -->
          <div class="confirm-row">
            <template v-if="!confirmed">
              <el-button
                type="primary"
                size="large"
                :loading="confirming"
                :disabled="confirming"
                @click="handleConfirmExtraction"
              >确认以上案件信息</el-button>
              <span class="confirm-note">确认后，这些信息可以用于后续法律咨询和法律文书生成。</span>
            </template>
            <template v-else>
              <el-button
                type="primary"
                plain
                :loading="confirming"
                :disabled="confirming"
                @click="handleConfirmExtraction"
              >保存修改</el-button>
              <span class="confirm-note">修改后需重新保存确认，将作为后续咨询的事实背景。</span>
            </template>
          </div>

          <!-- 次要操作：重新 AI 提取（已确认时先 warning）、开始风险分析（保持现有语义：不要求已确认） -->
          <div class="secondary-actions">
            <el-button
              type="warning"
              plain
              size="small"
              :loading="extracting"
              :disabled="extracting"
              @click="handleExtract"
            >重新 AI 提取</el-button>
            <el-button
              type="primary"
              plain
              size="small"
              :loading="riskAnalyzing"
              :disabled="riskAnalyzing"
              @click="handleRiskAnalysis"
            >开始风险分析</el-button>
          </div>
        </div>
      </template>

      <!-- 阶段 ④ 风险分析（risk 为 null 时不渲染；确认后 risk 会被清空，属合法状态） -->
      <template v-if="risk">
        <div class="section">
          <div class="section-title">风险分析</div>
          <div class="risk-sub-title">基础规则风险</div>
          <p class="risk-tip">根据已提取的案情信息，对时间、证据、对方主体、地点四类信息完整度进行确定性规则分析。</p>
          <div class="risk-cards">
            <el-card v-for="item in riskItems" :key="item.label" shadow="never" class="risk-card">
              <div class="risk-card-header">
                <span class="risk-card-name">
                  <el-icon :size="16"><component :is="item.icon" /></el-icon>
                  {{ item.label }}
                </span>
                <el-tag size="small" :type="riskTagType(item.risk.level)">{{ item.risk.level }}</el-tag>
              </div>
              <p class="risk-reason">{{ item.risk.reason }}</p>
              <p v-if="item.risk.informationNeeded" class="risk-need">需要补充：{{ item.risk.informationNeeded }}</p>
            </el-card>
          </div>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="以上仅表示四项基础风险要素目前已有信息，不代表案件事实已经完整"
          />

          <div class="risk-sub-title">仍建议补充的信息</div>
          <div v-if="missingItems.length" class="tag-list">
            <el-tag v-for="(m, i) in missingItems" :key="i" type="warning" effect="plain">{{ m }}</el-tag>
          </div>
          <el-alert
            v-else
            type="info"
            :closable="false"
            show-icon
            title="当前 AI 提取结果未列出额外信息缺口。"
          />

          <div class="risk-sub-title">处理建议</div>
          <el-input v-model="analysisText" type="textarea" :rows="9" maxlength="10000" show-word-limit />
          <div class="analysis-edit-footer">
            <el-button
              size="small"
              type="primary"
              :loading="analysisSaving"
              :disabled="analysisSaving"
              @click="handleSaveAnalysis"
            >保存处理建议</el-button>
          </div>

          <el-alert
            class="disclaimer"
            type="info"
            :closable="false"
            show-icon
            title="本分析基于用户提供的信息及系统规则生成，仅供法律信息参考，不替代律师正式法律意见。"
          />
        </div>
      </template>

      <!-- 阶段 ⑤ 后续行动 -->
      <div class="section">
        <div class="section-title">下一步可以做什么</div>
        <div class="next-actions">
          <el-tooltip :disabled="confirmed" content="请先确认 AI 提取结果" placement="top">
            <span>
              <el-button
                type="primary"
                size="large"
                :disabled="!confirmed"
                @click="goConsult"
              >基于此案件继续咨询</el-button>
            </span>
          </el-tooltip>
          <el-tooltip :disabled="confirmed" content="请先确认 AI 提取结果" placement="top">
            <span>
              <el-button
                type="primary"
                plain
                size="large"
                :disabled="!confirmed"
                @click="goDocument"
              >基于此案件生成文书</el-button>
            </span>
          </el-tooltip>
          <span class="next-note">
            {{ confirmed
              ? '基于已确认的案件事实进入法律咨询或生成法律文书。'
              : '确认 AI 提取结果后，可基于此案件继续咨询或生成文书。' }}
          </span>
        </div>
      </div>

      <template #footer>
        <el-button @click="editDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Document, Location, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import request from '../utils/request'

interface CaseAnalysisItem {
  id: number
  title: string
  domain: string
  caseFacts: string
  claims: string
  keyDates: string
  evidenceDescription: string
  counterpartyDescription: string
  locationDescription: string
  amount: number | null
  contractStatus: string
  analysisStatus: string
  extraction: CaseFactExtraction | null
  extractionConfirmed: number
  risk: CaseRiskResult | null
  analysisResult: string | null
  createdAt: string
  updatedAt: string
}

/** 4B 固定 JSON 提取结构（与后端 CaseFactExtraction 对应） */
interface CaseFactExtraction {
  caseSummary: string
  disputeFocus: string
  claims: string[]
  keyDates: { event: string; dateText: string }[]
  evidenceItems: { name: string; description: string }[]
  counterparty: { description: string }
  location: { description: string }
  missingInformation: string[]
}

/** 4C 风险结果（与后端 CaseRiskResult 对应）：5 根字段，level 为 HIGH/MEDIUM/LOW */
interface CaseRiskResult {
  overallLevel: string
  timeRisk: RiskItem
  evidenceRisk: RiskItem
  counterpartyRisk: RiskItem
  locationRisk: RiskItem
}

interface RiskItem {
  level: string
  ruleCode: string
  reason: string
  informationNeeded: string
}

const domainOptions = [
  { label: '劳动争议', value: 'LABOR_DISPUTE' },
  { label: '房屋租赁', value: 'HOUSE_RENTAL' },
  { label: '消费者权益', value: 'CONSUMER_RIGHTS' }
]

const contractStatusOptions = [
  { label: '已签订书面合同', value: 'SIGNED' },
  { label: '口头约定', value: 'ORAL' },
  { label: '未签订合同', value: 'UNSIGNED' },
  { label: '未提供或不清楚', value: 'UNKNOWN' }
]

const domainLabels: Record<string, string> = {
  LABOR_DISPUTE: '劳动争议',
  HOUSE_RENTAL: '房屋租赁',
  CONSUMER_RIGHTS: '消费者权益'
}

function domainLabel(d: string) {
  return domainLabels[d] || d || '未知'
}

function domainTagType(d: string): 'success' | 'warning' | 'danger' | 'info' {
  if (d === 'LABOR_DISPUTE') return 'success'
  if (d === 'HOUSE_RENTAL') return 'warning'
  if (d === 'CONSUMER_RIGHTS') return 'danger'
  return 'info'
}

/** 确认状态推导：待 AI 提取 / 待人工确认 / 已人工确认（0/1 不直接展示） */
function caseStatus(row: { extraction: CaseFactExtraction | null; extractionConfirmed: number }) {
  if (row.extraction && row.extractionConfirmed === 1) return { text: '已人工确认', type: 'success' as const }
  if (row.extraction) return { text: '待人工确认', type: 'warning' as const }
  return { text: '待 AI 提取', type: 'info' as const }
}

/** 4C 风险等级 → tag 颜色：HIGH 红 / MEDIUM 黄 / LOW 绿 */
function riskTagType(level: string): 'success' | 'warning' | 'danger' | 'info' {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  if (level === 'LOW') return 'success'
  return 'info'
}

function formatTime(t: string | null) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 19)
}

const emptyForm = () => ({
  title: '',
  domain: '',
  caseFacts: '',
  claims: '',
  keyDates: '',
  evidenceDescription: '',
  counterpartyDescription: '',
  locationDescription: '',
  amount: null,
  contractStatus: 'UNKNOWN'
})

const formRules: FormRules = {
  title: [{ required: true, message: '请输入案件标题', trigger: 'blur' }],
  domain: [{ required: true, message: '请选择案件领域', trigger: 'change' }],
  caseFacts: [{ required: true, message: '请输入案件基本事实', trigger: 'blur' }]
}

const createFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()
const createForm = reactive(emptyForm())
const editForm = reactive<CaseAnalysisItem>({ id: 0, analysisStatus: '', extraction: null, extractionConfirmed: 0, risk: null, analysisResult: null, createdAt: '', updatedAt: '', ...emptyForm() })

const caseList = ref<CaseAnalysisItem[]>([])
const loading = ref(false)
const saving = ref(false)
const showCreate = ref(false)
const editDialogVisible = ref(false)
const extracting = ref(false)
const extraction = computed(() => editForm.extraction)
const confirming = ref(false)
const confirmed = computed(() => editForm.extractionConfirmed === 1)
/** 案件资料折叠：未提取时展开（填写阶段），有提取结果后收起 */
const basicActive = ref<string[]>([])

/** 步骤指示（真实数据驱动）：无提取→AI 提取 / 未确认→人工确认 / 已确认未分析→风险分析 / 已分析→后续行动 */
const stepActive = computed(() => {
  if (!editForm.extraction) return 1
  if (editForm.extractionConfirmed !== 1) return 2
  if (editForm.analysisStatus !== 'ANALYZED') return 3
  return 4
})

const workStatus = computed(() => caseStatus(editForm))

/** 仍建议补充的信息：来自提取结果的 missingInformation（去空白项后展示） */
const missingItems = computed(() => {
  const list = extraction.value?.missingInformation || []
  return list.filter((s: string) => s && s.trim() !== '')
})

// ---- 4B 人工编辑辅助：数组字段简单增删行 ----
function addClaim() {
  editForm.extraction?.claims.push('')
}
function removeClaim(i: number) {
  editForm.extraction?.claims.splice(i, 1)
}
function addKeyDate() {
  editForm.extraction?.keyDates.push({ event: '', dateText: '' })
}
function removeKeyDate(i: number) {
  editForm.extraction?.keyDates.splice(i, 1)
}
function addEvidence() {
  editForm.extraction?.evidenceItems.push({ name: '', description: '' })
}
function removeEvidence(i: number) {
  editForm.extraction?.evidenceItems.splice(i, 1)
}
function addMissing() {
  editForm.extraction?.missingInformation.push('')
}
function removeMissing(i: number) {
  editForm.extraction?.missingInformation.splice(i, 1)
}

// ---- 4C 风险分析 ----
const riskAnalyzing = ref(false)
const analysisSaving = ref(false)
const analysisText = ref('')
const risk = computed(() => (editForm.analysisStatus === 'ANALYZED' ? editForm.risk : null))

const riskItems = computed(() => {
  if (!risk.value) return []
  return [
    { label: '关键时间', icon: Clock, risk: risk.value.timeRisk },
    { label: '现有证据', icon: Document, risk: risk.value.evidenceRisk },
    { label: '对方主体', icon: User, risk: risk.value.counterpartyRisk },
    { label: '地点', icon: Location, risk: risk.value.locationRisk }
  ]
})

/**
 * 4B→4C 人工确认保存提取结果：把当前可编辑表单内容作为确认版本提交。
 * 后端走同一严格校验契约（结构/长度/数量），校验失败由拦截器弹出错误且不落库。
 * 已确认后再次点击 = 保存修改后重新确认（同一端点，后端置 confirmed=1）。
 */
async function handleConfirmExtraction() {
  if (confirming.value || !editForm.id || !editForm.extraction) return
  if (!editForm.extraction.caseSummary.trim() || !editForm.extraction.disputeFocus.trim()) {
    ElMessage.warning('案件摘要与争议焦点不能为空')
    return
  }
  confirming.value = true
  try {
    const res = await request.put(`/case-analysis/${editForm.id}/extraction`, {
      extraction: editForm.extraction,
      domain: editForm.domain
    })
    const data = res.data.data as CaseAnalysisItem
    Object.assign(editForm, data)
    analysisText.value = data.analysisResult || ''
    ElMessage.success('案件信息已确认保存')
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    confirming.value = false
  }
}

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await request.get('/case-analysis')
    caseList.value = res.data.data || []
  } catch {
    // 401 由拦截器处理；其余错误保留页面可重试
  } finally {
    loading.value = false
  }
}

function openCreate() {
  showCreate.value = true
  nextTick(() => {
    document.querySelector('.create-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

async function handleCreate() {
  if (!createFormRef.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await request.post('/case-analysis', { ...createForm })
    ElMessage.success('保存成功')
    Object.assign(createForm, emptyForm())
    createFormRef.value.resetFields()
    showCreate.value = false
    await loadList()
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    saving.value = false
  }
}

async function handleEdit(row: CaseAnalysisItem) {
  try {
    const res = await request.get(`/case-analysis/${row.id}`)
    const data = res.data.data as CaseAnalysisItem
    Object.assign(editForm, data)
    analysisText.value = data.analysisResult || ''
    basicActive.value = data.extraction ? [] : ['basic']
    editDialogVisible.value = true
  } catch {
    // 404 等错误由拦截器提示
  }
}

/**
 * 4B 智能提取案情：loading + 防重复点击；按请求覆盖 axios 超时（后端 LLM 最长约 60s，
 * 全局 10s 会提前报错且不中断后端执行）。成功后刷新详情并结构化展示；失败由拦截器
 * 弹出具体原因（如"智能提取失败:提取服务繁忙(429)"），不做本地兜底。
 * 覆盖保护：已人工确认状态下再次提取必须显式确认，取消则不调用 DeepSeek（数据不变）。
 * 提取成功后后端将 extractionConfirmed 置回 0，前端以响应数据为准刷新。
 */
async function handleExtract() {
  if (extracting.value || !editForm.id) return
  if (editForm.extractionConfirmed === 1) {
    try {
      await ElMessageBox.confirm('当前 AI 提取结果已经人工确认。重新提取将覆盖当前确认内容并回到待确认状态，是否继续？', '重新 AI 提取', {
        type: 'warning',
        confirmButtonText: '继续提取',
        cancelButtonText: '取消'
      })
    } catch {
      return // 取消：不调用 DeepSeek，当前确认结果保持不变
    }
  }
  extracting.value = true
  try {
    const res = await request.post(`/case-analysis/${editForm.id}/extract`, null, { timeout: 75000 })
    const data = res.data.data as CaseAnalysisItem
    Object.assign(editForm, data)
    analysisText.value = data.analysisResult || ''
    ElMessage.success('案情提取成功')
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    extracting.value = false
  }
}

/**
 * 4C 开始风险分析：loading + 防重复；纯 Java 规则秒级返回（无需覆盖 axios 超时）。
 * 若已存在风险且处理建议有未保存编辑，先确认（重新分析将覆盖当前已编辑的处理建议）。
 */
async function handleRiskAnalysis() {
  if (riskAnalyzing.value || !editForm.id) return
  if (risk.value && analysisText.value !== (editForm.analysisResult || '')) {
    try {
      await ElMessageBox.confirm('重新分析将覆盖当前已编辑的处理建议，是否继续？', '重新风险分析', {
        type: 'warning',
        confirmButtonText: '继续',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
  }
  riskAnalyzing.value = true
  try {
    const res = await request.post(`/case-analysis/${editForm.id}/risk-analysis`, null)
    const data = res.data.data as CaseAnalysisItem
    Object.assign(editForm, data)
    analysisText.value = data.analysisResult || ''
    ElMessage.success('风险分析完成')
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    riskAnalyzing.value = false
  }
}

/**
 * 4C 保存处理建议：仅当风险分析完成（ANALYZED）时按钮可用；空文本本地拦截。
 * 后端只更新 analysis_result + updated_at，风险 JSON 不可由本请求篡改。
 */
async function handleSaveAnalysis() {
  if (analysisSaving.value || !editForm.id) return
  if (!analysisText.value.trim()) {
    ElMessage.warning('处理建议不能为空')
    return
  }
  analysisSaving.value = true
  try {
    const res = await request.put(`/case-analysis/${editForm.id}/analysis-result`, {
      analysisResult: analysisText.value
    })
    const data = res.data.data as CaseAnalysisItem
    Object.assign(editForm, data)
    analysisText.value = data.analysisResult || ''
    ElMessage.success('保存成功')
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    analysisSaving.value = false
  }
}

async function handleSaveEdit() {
  if (!editFormRef.value) return
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  // 守卫：保存案件资料会清空当前智能提取结果（含人工确认状态），存在提取结果时必须显式确认
  if (editForm.extraction) {
    try {
      await ElMessageBox.confirm('保存案件资料将清空当前 AI 提取结果与人工确认状态，是否继续？', '保存确认', {
        type: 'warning',
        confirmButtonText: '继续保存',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
  }
  saving.value = true
  try {
    await request.put(`/case-analysis/${editForm.id}`, {
      title: editForm.title,
      domain: editForm.domain,
      caseFacts: editForm.caseFacts,
      claims: editForm.claims,
      keyDates: editForm.keyDates,
      evidenceDescription: editForm.evidenceDescription,
      counterpartyDescription: editForm.counterpartyDescription,
      locationDescription: editForm.locationDescription,
      amount: editForm.amount,
      contractStatus: editForm.contractStatus
    })
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    await loadList()
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    saving.value = false
  }
}

/**
 * Final-Requirement-Gaps-G: 进入法律咨询并关联本案件。
 * URL 只携带 caseAnalysisId（不携带任何案件内容），
 * 案件事实由 Consult 页服务端校验后读取；提取结果未确认时按钮禁用。
 */
const router = useRouter()
function goConsult() {
  if (!editForm.id) return
  router.push({ path: '/consult', query: { caseAnalysisId: editForm.id } })
}

/**
 * Final-Requirement-Gaps-G: 进入法律文书页并关联本案件。
 * URL 只携带 caseAnalysisId，本人性/存在性/已确认校验由文书页深链逻辑完成；
 * 提取结果未确认时按钮禁用（与继续咨询同门禁）。
 */
function goDocument() {
  if (!editForm.id) return
  router.push({ path: '/legal-writing', query: { caseAnalysisId: editForm.id } })
}

async function handleDelete(row: CaseAnalysisItem) {
  try {
    await ElMessageBox.confirm(`确定删除案件「${row.title}」吗？删除后不再显示，仍可恢复数据。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await request.delete(`/case-analysis/${row.id}`)
    ElMessage.success('删除成功')
    await loadList()
  } catch {
    // 错误提示由拦截器统一弹出
  }
}
</script>

<style scoped>
.case-analysis {
  min-height: 100vh;
  background: #f5f7fa;
}
.content {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.page-sub {
  margin: -12px 0 4px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-weight: 600;
  color: #303133;
}
.stage-tag {
  font-size: 12px;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 4px;
  padding: 2px 10px;
}
.empty-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: #909399;
}
.form-group {
  margin-bottom: 8px;
}
.group-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 10px 0 12px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
  line-height: 1.4;
}
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
.work-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding-right: 28px;
}
.work-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.work-title {
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.work-header-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.work-steps {
  margin: 4px 0 20px;
}
.basic-collapse {
  margin-bottom: 8px;
  border: none;
}
.section {
  margin-top: 16px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}
.section-tip {
  margin: 0 0 12px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.primary-action {
  display: flex;
  align-items: center;
  gap: 12px;
}
.action-hint {
  font-size: 13px;
  color: #909399;
}
.result-status {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.status-note {
  font-size: 13px;
  color: #606266;
}
.extract-group {
  margin-bottom: 16px;
}
.extract-group .sub-input {
  margin-top: 8px;
}
.confirm-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0 8px;
  flex-wrap: wrap;
}
.confirm-note {
  font-size: 12px;
  color: #909399;
}
.secondary-actions {
  display: flex;
  gap: 8px;
}
.risk-sub-title {
  margin: 16px 0 8px;
  font-weight: 600;
  color: #303133;
}
.risk-tip {
  margin: 0 0 12px;
  font-size: 12px;
  color: #909399;
}
.risk-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}
.risk-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.risk-card-name {
  display: flex;
  align-items: center;
  gap: 6px;
}
.risk-reason {
  margin: 8px 0 0;
  font-size: 13px;
  color: #303133;
}
.risk-need {
  margin: 4px 0 0;
  font-size: 12px;
  color: #e6a23c;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 4px;
}
.analysis-edit-footer {
  margin-top: 8px;
  text-align: right;
}
.disclaimer {
  margin: 16px 0 4px;
}
.next-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.next-note {
  font-size: 13px;
  color: #909399;
}
.row-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.row-line .el-input {
  flex: 1;
}
.row-line .row-col {
  flex: 1;
}
</style>
