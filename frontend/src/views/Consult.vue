<template>
  <div class="consult">
    <div class="content">
      <h1 class="page-title">法律咨询</h1>
      <p class="page-sub">
        请尽量描述事实、时间、金额、合同及已有证据。系统目前聚焦劳动争议、房屋租赁和消费者权益。
      </p>

      <!-- Final-Requirement-Gaps-G: 关联案件只读卡片（紧凑展示，不铺满 JSON） -->
      <el-card v-if="linkedCase" class="linked-case-card" shadow="never">
        <template #header>
          <div class="linked-case-header">
            <span class="linked-case-title">当前关联案件</span>
            <el-button size="small" text type="primary" :disabled="loading" @click="clearLink">取消关联</el-button>
          </div>
        </template>
        <div class="linked-case-body">
          <div class="linked-line">
            <span class="linked-label">案件标题</span>
            <span class="linked-value">{{ linkedCase.title }}</span>
          </div>
          <div class="linked-line">
            <span class="linked-label">领域</span>
            <el-tag size="small" :type="domainTagType(linkedCase.domain)">{{ domainLabel(linkedCase.domain) }}</el-tag>
            <el-tag size="small" type="success" style="margin-left:6px">已人工确认</el-tag>
          </div>
          <div class="linked-line">
            <span class="linked-label">案件摘要</span>
            <span class="linked-value">{{ linkedCase.caseSummary }}</span>
          </div>
          <div class="linked-line">
            <span class="linked-label">核心诉求</span>
            <span class="linked-value">{{ linkedCase.claims.join('；') || '未明确表达' }}</span>
          </div>
          <div class="linked-line">
            <span class="linked-label">已有证据</span>
            <span class="linked-value">{{ linkedCase.evidenceNames.join('、') || '未提供' }}</span>
          </div>
        </div>
        <p class="linked-note">以下内容作为已确认的事实背景使用，法律依据仍来自法规检索。</p>
      </el-card>
      <el-alert
        v-if="linkError"
        type="warning"
        show-icon
        :closable="false"
        class="result-block"
        :title="linkError"
      />

      <!-- 输入区 -->
      <el-card class="input-card" shadow="never">
        <div class="input-label">向 AI 描述你的法律问题</div>
        <el-input
          v-model="question"
          type="textarea"
          :rows="5"
          maxlength="500"
          show-word-limit
          :disabled="loading"
          :placeholder="inputPlaceholder"
        />
        <div class="examples">
          <span class="examples-label">不知道怎么问？试试这些示例</span>
          <div class="example-list">
            <el-tag
              v-for="ex in examples"
              :key="ex.text"
              class="example-chip"
              effect="plain"
              type="info"
              @click="fillExample(ex.text)"
            >{{ ex.label }}：{{ ex.text }}</el-tag>
          </div>
        </div>
        <div class="actions">
          <el-button type="primary" size="large" :loading="loading" :disabled="loading" @click="submit">
            开始咨询
          </el-button>
          <span v-if="loading" class="loading-hint">正在理解问题并检索相关法规…</span>
        </div>
      </el-card>

      <!-- 单一错误呈现：一次失败只出现这一个页面级错误区（401 由全局 interceptor 处理） -->
      <el-alert
        v-if="serverError"
        type="error"
        show-icon
        class="result-block"
        title="咨询暂时未完成"
        :description="serverErrorMessage"
        @close="serverError = false"
      />

      <!-- 最近一次咨询结果（新咨询提交时清空，成功后替换） -->
      <template v-if="result">
        <div class="q-card">
          <span class="q-label">你的问题</span>
          <span class="q-text">{{ submittedQuestion }}</span>
        </div>

        <!-- 业务拒绝 / 依据不足（UNSUPPORTED_DOMAIN / NON_LEGAL_INTENT / KNOWLEDGE_GAP） -->
        <template v-if="result.evidenceInsufficient">
          <el-alert
            type="warning"
            show-icon
            class="result-block"
            :title="reasonTitle(result.relevanceReason)"
            :description="reasonLabel(result.relevanceReason)"
          />
          <el-card class="result-block answer-card" shadow="never">
            <template #header><h4 style="margin:0">咨询说明</h4></template>
            <p class="answer-text">{{ result.answer }}</p>
          </el-card>
          <el-empty description="暂无可靠法律依据" :image-size="80" />
        </template>

        <!-- 正常回答（PASS） -->
        <template v-else>
          <el-card class="result-block" shadow="never">
            <template #header>
              <div class="answer-header">
                <h4 style="margin:0">AI 法律信息</h4>
                <div class="answer-header-right">
                  <span class="answer-status">已基于检索到的法规生成</span>
                  <el-tag size="small" :type="domainTagType(result.detectedDomain)">
                    {{ domainLabel(result.detectedDomain) }}
                  </el-tag>
                </div>
              </div>
            </template>
            <p class="answer-text">{{ result.answer }}</p>
          </el-card>

          <el-card class="result-block" shadow="never">
            <template #header>
              <h4 style="margin:0">法律依据</h4>
            </template>
            <el-collapse v-if="result.citations && result.citations.length > 0">
              <el-collapse-item
                v-for="(c, idx) in result.citations"
                :key="c.articleId"
                :name="idx"
              >
                <template #title>
                  <span class="citation-title">
                    {{ c.documentName }} · {{ c.articleNumber }}
                  </span>
                </template>
                <p class="snippet">正文摘要：{{ c.contentSnippet }}</p>
                <p v-if="c.source" class="source">
                  来源：{{ c.source }}
                  <a
                    v-if="isHttpUrl(c.source)"
                    :href="c.source"
                    target="_blank"
                    rel="noopener noreferrer"
                  >查看官方来源</a>
                </p>
              </el-collapse-item>
            </el-collapse>
            <el-empty v-else description="暂无可靠法律依据" :image-size="80" />
          </el-card>
        </template>
      </template>

      <p class="disclaimer">
        AI 生成内容仅供法律信息参考，不构成正式法律意见。具体案件请结合完整事实并咨询专业法律人士。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../utils/request'

interface Citation {
  articleId: number
  documentId: number
  documentName: string
  articleNumber: string
  source: string
  contentSnippet: string
}

interface ConsultData {
  answer: string
  detectedDomain: string
  evidenceInsufficient: boolean
  relevanceReason: string
  citations: Citation[]
}

/** Final-Requirement-Gaps-G: 关联案件只读卡片数据（案件详情 API 的可读摘要，不铺满 JSON） */
interface LinkedCase {
  title: string
  domain: string
  caseSummary: string
  claims: string[]
  evidenceNames: string[]
}

const question = ref('')
const loading = ref(false)
const result = ref<ConsultData | null>(null)
/** 最近一次成功提交的问题原文（结果区"你的问题"展示，不依赖响应字段） */
const submittedQuestion = ref('')
const serverError = ref(false)
/** 单一错误区描述：优先展示后端真实友好错误，否则通用文案 */
const serverErrorMessage = ref('咨询服务暂时不可用，请稍后重试。')

const route = useRoute()
const router = useRouter()
const linkedCaseId = ref<number | null>(null)
const linkedCase = ref<LinkedCase | null>(null)
const linkError = ref('')

/** 示例问题：点击只填入输入框，不自动发送 */
const examples = [
  { label: '劳动争议', text: '公司拖欠工资，我应该如何维权？' },
  { label: '房屋租赁', text: '退租后房东一直不退押金，我该怎么办？' },
  { label: '消费者权益', text: '买到有质量问题的商品，商家拒绝退款怎么办？' },
]

function fillExample(text: string) {
  question.value = text
}

/**
 * 关联案件场景的问题占位符：用户输入框仍只填写"这次具体想咨询什么"，
 * 长案件背景放在卡片中，不塞进 textarea。
 */
const inputPlaceholder = computed(() =>
  linkedCase.value
    ? '例如：基于这个案件，我现在最应该先做什么？'
    : '请描述发生了什么、关键时间、涉及金额、合同情况和已有证据。例如：公司拖欠我两个月工资，我有工资流水和聊天记录，应该怎么办？'
)

/**
 * Final-Requirement-Gaps-G: 从 URL 读取 caseAnalysisId（URL 只允许 caseId），
 * 调用本人案件详情 API 加载只读卡片。任何失效（不存在/已删除/非本人/未确认）
 * 都不崩溃：友好提示并取消关联，用户仍可使用普通法律咨询。
 */
async function loadLinkedCase() {
  const raw = route.query.caseAnalysisId
  if (!raw) return
  const id = Number(raw)
  if (!Number.isInteger(id) || id <= 0) {
    linkError.value = '关联案件参数无效，已取消关联，可继续使用普通法律咨询。'
    return
  }
  linkedCaseId.value = id
  try {
    const res = await request.get(`/case-analysis/${id}`)
    const data = res.data.data
    if (!data || data.extractionConfirmed !== 1 || !data.extraction) {
      linkError.value = '该案件尚未确认 AI 提取结果，无法基于此案件咨询，已取消关联。'
      linkedCaseId.value = null
      return
    }
    linkedCase.value = {
      title: data.title,
      domain: data.domain,
      caseSummary: data.extraction.caseSummary,
      claims: (data.extraction.claims || []).map((c: string) => c),
      evidenceNames: (data.extraction.evidenceItems || [])
        .map((e: { name: string }) => e.name)
        .filter((n: string) => n)
    }
  } catch {
    linkError.value = '关联案件不存在或无权访问，已取消关联，可继续使用普通法律咨询。'
    linkedCaseId.value = null
  }
}

/** 取消关联：回到普通咨询（提交时不再携带 caseAnalysisId） */
function clearLink() {
  linkedCaseId.value = null
  linkedCase.value = null
  linkError.value = ''
  router.replace({ path: '/consult' })
}

onMounted(loadLinkedCase)

const domainLabels: Record<string, string> = {
  LABOR_DISPUTE: '劳动争议',
  HOUSE_RENTAL: '房屋租赁',
  CONSUMER_RIGHTS: '消费者权益',
  UNKNOWN: '未识别领域'
}

const reasonLabels: Record<string, string> = {
  NON_LEGAL_INTENT: '当前问题似乎不是法律咨询问题。本系统主要提供劳动争议、房屋租赁和消费者权益领域的法律信息咨询。',
  UNSUPPORTED_DOMAIN: '该问题属于法律咨询，但目前超出本系统支持的劳动争议、房屋租赁和消费者权益范围。',
  KNOWLEDGE_GAP: '系统已识别您的法律问题，但当前知识库暂未检索到足够直接的法律依据。',
  UNKNOWN_DOMAIN: '当前问题不属于系统已覆盖的法律领域',
  LOW_RELEVANCE: '当前知识库中缺少足够直接的相关法律依据',
  NO_RESULT: '当前未检索到可用法律依据'
}

function reasonTitle(r: string) {
  if (r === 'NON_LEGAL_INTENT') return '当前问题似乎不是法律咨询问题'
  if (r === 'UNSUPPORTED_DOMAIN') return '该问题超出当前系统支持的法律领域'
  return '当前知识库未检索到足够直接的法律依据'
}

function domainLabel(d: string) {
  return domainLabels[d] || d || '未识别领域'
}

function domainTagType(d: string): 'success' | 'warning' | 'danger' | 'info' {
  if (d === 'LABOR_DISPUTE') return 'success'
  if (d === 'HOUSE_RENTAL') return 'warning'
  if (d === 'CONSUMER_RIGHTS') return 'danger'
  return 'info'
}

function reasonLabel(r: string) {
  return reasonLabels[r] || '当前知识库中缺少足够直接的相关法律依据'
}

function isHttpUrl(s: string) {
  return /^https?:\/\//i.test(s)
}

async function submit() {
  const q = question.value.trim()
  if (!q) return
  if (loading.value) return

  // 新一次咨询开始：清空上一次结果与错误，避免旧结果与本次失败混淆
  loading.value = true
  serverError.value = false
  result.value = null
  submittedQuestion.value = ''

  try {
    // LLM 生成耗时可能超过全局 10s 超时，覆盖为 90s（后端 read timeout 60s + 缓冲）
    // 关联案件时仅追加 caseAnalysisId（案件事实由服务端按 userId 重新读取，客户端不上传案件文本）
    const body: Record<string, unknown> = { question: q }
    if (linkedCaseId.value != null) {
      body.caseAnalysisId = linkedCaseId.value
    }
    // 单一错误呈现：非 401 的响应不触发全局 interceptor toast，
    // 由页面统一呈现（见 serverError alert）；401 仍抛错交给 interceptor 清 token 跳登录。
    const res = await request.post('/consult', body, {
      timeout: 90000,
      validateStatus: (s: number) => s !== 401
    })
    const payload = res.data
    if (payload && payload.code === 200 && payload.data) {
      result.value = payload.data as ConsultData
      submittedQuestion.value = q
    } else {
      // 业务/技术失败（含后端友好 message），页面单点呈现
      serverErrorMessage.value = payload?.message || '咨询服务暂时不可用，请稍后重试。'
      serverError.value = true
    }
  } catch (e) {
    // 401 handled by global interceptor（清 token + 跳登录），页面不再叠加提示
    if ((e as { response?: { status?: number } })?.response?.status === 401) return
    // 网络异常 / 超时等：无后端 message，使用通用文案，页面保持可重试
    serverErrorMessage.value = '咨询服务暂时不可用，请稍后重试。'
    serverError.value = true
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.consult {
  min-height: 100vh;
  background: #f5f7fa;
}
.content {
  max-width: 820px;
  margin: 0 auto;
  padding: 32px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-sub {
  margin: -8px 0 4px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.input-label {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}
.examples {
  margin-top: 12px;
}
.examples-label {
  font-size: 13px;
  color: #909399;
}
.example-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.example-chip {
  cursor: pointer;
  font-size: 13px;
  max-width: 100%;
}
.loading-hint {
  margin-left: 12px;
  font-size: 13px;
  color: #909399;
}
.actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
.result-block {
  margin-top: 20px;
}
.q-card {
  display: flex;
  align-items: baseline;
  gap: 10px;
  background: #ecf5ff;
  border-radius: 6px;
  padding: 12px 16px;
  margin-top: 20px;
}
.q-label {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
}
.q-text {
  font-size: 14px;
  color: #303133;
  line-height: 1.6;
  word-break: break-word;
}
.answer-card {
  margin-top: 16px;
}
.answer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.answer-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.answer-status {
  font-size: 12px;
  color: #909399;
}
.answer-text {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #303133;
}
.citation-title {
  font-weight: 500;
  color: #303133;
}
.snippet {
  color: #606266;
  line-height: 1.7;
}
.source {
  color: #909399;
  font-size: 0.85rem;
}
.source a {
  margin-left: 8px;
  color: #409eff;
}
.linked-case-card {
  margin-top: 16px;
}
.linked-case-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.linked-case-title {
  font-weight: 600;
  color: #409eff;
}
.linked-case-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.linked-line {
  display: flex;
  gap: 8px;
  font-size: 13px;
  line-height: 1.6;
}
.linked-label {
  flex-shrink: 0;
  width: 64px;
  color: #909399;
}
.linked-value {
  color: #303133;
  white-space: pre-wrap;
}
.linked-note {
  margin: 12px 0 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.disclaimer {
  text-align: center;
  font-size: 0.8rem;
  color: #909399;
}
</style>
