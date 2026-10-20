<template>
  <div class="legal-writing">
    <div class="content">
      <h1 class="page-title">法律文书与流程指引</h1>
      <p class="page-sub">
        根据已确认的案件事实生成法律文书；也可以查看劳动争议、房屋租赁、消费者权益三大领域的固定办事流程指引。
      </p>
      <el-tabs v-model="activeTab">
        <!-- 文书辅助 -->
        <el-tab-pane label="文书辅助" name="documents">
          <el-card class="main-card">
            <template #header>
              <h3 style="margin:0">新建文书草稿</h3>
            </template>

            <el-form
              ref="createFormRef"
              :model="createForm"
              :rules="formRules"
              label-width="110px"
              label-position="left"
            >
              <el-form-item label="文书标题" prop="title">
                <el-input v-model="createForm.title" maxlength="128" show-word-limit placeholder="例如：未签劳动合同仲裁申请书" />
              </el-form-item>

              <el-form-item label="文书类型" prop="documentType">
                <el-select v-model="createForm.documentType" placeholder="请选择文书类型" style="width:100%">
                  <el-option v-for="t in documentTypeOptionsFor(createForm.domain)" :key="t.value" :label="t.label" :value="t.value" />
                </el-select>
              </el-form-item>

              <el-form-item label="所属领域" prop="domain">
                <el-select v-model="createForm.domain" placeholder="请选择所属领域" style="width:100%">
                  <el-option v-for="d in domainOptions" :key="d.value" :label="d.label" :value="d.value" />
                </el-select>
              </el-form-item>

              <el-form-item label="关联案件" prop="caseAnalysisId">
                <el-select
                  v-model="createForm.caseAnalysisId"
                  placeholder="可选：关联本人案件"
                  style="width:100%"
                  clearable
                >
                  <el-option v-for="c in myCases" :key="c.id" :label="c.title" :value="c.id" />
                </el-select>
              </el-form-item>

              <el-form-item>
                <el-button type="primary" :loading="saving" @click="handleCreate">保存文书草稿</el-button>
              </el-form-item>
            </el-form>

            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="保存草稿后可打开文书点击【智能生成文书】：系统将根据关联案件资料草拟正文（需先关联案件，且文书类型与领域匹配），正文可编辑并支持导出 DOCX。"
            />
          </el-card>

          <!-- 我的文书 -->
          <el-card class="main-card">
            <template #header>
              <h3 style="margin:0">我的文书</h3>
            </template>

            <el-table :data="draftList" v-loading="loading" empty-text="暂无文书草稿，请先在上方新建">
              <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
              <el-table-column label="类型" width="140">
                <template #default="{ row }">
                  <el-tag size="small" :type="documentTypeTagType(row.documentType)">{{ documentTypeLabel(row.documentType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="领域" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="domainTagType(row.domain)">{{ domainLabel(row.domain) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="生成状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{ generationStatusLabel(row.generationStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="关联案件" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ row.caseTitle || '未关联' }}</template>
              </el-table-column>
              <el-table-column label="更新时间" width="170">
                <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" link @click="handleEdit(row)">查看/编辑</el-button>
                  <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-tab-pane>

        <!-- 流程指引 -->
        <el-tab-pane label="流程指引" name="guides">
          <el-card class="main-card">
            <template #header>
              <h3 style="margin:0">固定办事流程指引</h3>
            </template>

            <el-form label-width="110px" label-position="left">
              <el-form-item label="选择领域">
                <el-select v-model="selectedGuideDomain" placeholder="请选择领域" style="width:100%">
                  <el-option v-for="g in guides" :key="g.domain" :label="g.title" :value="g.domain" />
                </el-select>
              </el-form-item>
            </el-form>

            <el-alert
              type="warning"
              :closable="false"
              show-icon
              :title="selectedGuide?.disclaimer || '具体材料、受理条件及流程以实际受理机构最新要求为准。'"
            />

            <template v-if="selectedGuide">
              <h3 class="guide-title">{{ selectedGuide.title }}</h3>

              <h4 class="guide-block-title">办理步骤</h4>
              <el-steps direction="vertical" :active="selectedGuide.steps.length">
                <el-step
                  v-for="s in selectedGuide.steps"
                  :key="s.order"
                  :title="`${s.order}. ${s.name}`"
                >
                  <template #description>
                    <p class="step-desc">{{ s.description }}</p>
                    <div v-if="s.materials && s.materials.length" class="step-materials">
                      <el-tag v-for="(m, i) in s.materials" :key="i" size="small" effect="plain" type="primary">{{ m }}</el-tag>
                    </div>
                  </template>
                </el-step>
              </el-steps>

              <h4 class="guide-block-title">所需材料</h4>
              <ul class="guide-list">
                <li v-for="(m, i) in selectedGuide.materials" :key="i">{{ m }}</li>
              </ul>

              <h4 class="guide-block-title">管辖原则</h4>
              <ul class="guide-list">
                <li v-for="(j, i) in selectedGuide.jurisdiction" :key="i">{{ j }}</li>
              </ul>

              <h4 class="guide-block-title">注意事项</h4>
              <ul class="guide-list">
                <li v-for="(p, i) in selectedGuide.precautions" :key="i">{{ p }}</li>
              </ul>

              <h4 class="guide-block-title">常见问题</h4>
              <el-collapse class="guide-faq">
                <el-collapse-item v-for="(f, i) in selectedGuide.faqs" :key="i" :title="f.question">
                  <p class="faq-answer">{{ f.answer }}</p>
                </el-collapse-item>
              </el-collapse>

              <h4 class="guide-block-title">官方查询渠道</h4>
              <ul class="guide-list">
                <li v-for="(c, i) in selectedGuide.officialChannels" :key="i">
                  <span class="channel-name">{{ c.name }}</span>
                  <span class="channel-note">{{ c.note }}</span>
                </li>
              </ul>
            </template>
            <el-empty v-else description="请选择领域查看固定流程" :image-size="60" />
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 查看/编辑弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      title="查看/编辑文书草稿"
      width="620px"
      destroy-on-close
    >
      <el-form ref="editFormRef" :model="editForm" :rules="formRules" label-width="110px" label-position="left">
        <el-form-item label="文书标题" prop="title">
          <el-input v-model="editForm.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="文书类型" prop="documentType">
          <el-select v-model="editForm.documentType" placeholder="请选择文书类型" style="width:100%">
            <el-option v-for="t in documentTypeOptionsFor(editForm.domain)" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属领域" prop="domain">
          <el-select v-model="editForm.domain" placeholder="请选择所属领域" style="width:100%">
            <el-option v-for="d in domainOptions" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联案件" prop="caseAnalysisId">
          <el-select
            v-model="editForm.caseAnalysisId"
            placeholder="可选：关联本人案件"
            style="width:100%"
            clearable
          >
            <el-option v-for="c in myCases" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="editForm.generationStatus === 'GENERATED'"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
        title="修改标题/类型/领域/关联案件并保存后，已生成正文将被清空，需重新生成。"
      />

      <el-divider />
      <div class="content-section">
        <div class="content-header">
          <span class="content-title">文书正文</span>
          <el-tag size="small" :type="editForm.generationStatus === 'GENERATED' ? 'success' : 'info'">
            {{ generationStatusLabel(editForm.generationStatus) }}
          </el-tag>
        </div>

        <!-- 已生成：可编辑正文 / 重新生成（确认框）/ 导出 DOCX -->
        <template v-if="editForm.generationStatus === 'GENERATED'">
          <el-input
            v-model="editContent"
            type="textarea"
            :rows="14"
            maxlength="100000"
            show-word-limit
            placeholder="文书正文，可直接编辑后点击【保存正文】"
          />
          <div class="content-actions">
            <el-button type="primary" :loading="savingContent" @click="handleSaveContent">保存正文</el-button>
            <el-button type="warning" :loading="exporting" @click="handleExport">导出 DOCX</el-button>
            <el-button :loading="generating" @click="handleRegenerate">重新生成</el-button>
          </div>
        </template>

        <!-- 未生成：生成入口 + 提示 -->
        <template v-else>
          <el-empty description="尚未生成正文" :image-size="60" />
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="点击【智能生成文书】后，系统将根据关联案件资料草拟文书正文；未关联案件或文书类型与领域不匹配时将提示原因。"
          />
          <div class="content-actions">
            <el-button
              type="primary"
              :loading="generating"
              :disabled="!editForm.caseAnalysisId"
              @click="handleGenerate"
            >
              {{ editForm.caseAnalysisId ? '智能生成文书' : '请先关联案件' }}
            </el-button>
          </div>
        </template>
      </div>

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '../utils/request'

interface DocumentDraftItem {
  id: number
  title: string
  documentType: string
  domain: string
  caseAnalysisId: number | null
  caseTitle: string | null
  generationStatus: string
  content: string | null
  createdAt: string
  updatedAt: string
}

interface CaseAnalysisLite {
  id: number
  title: string
  domain: string
  extractionConfirmed: number
}

interface ProcessStep {
  order: number
  name: string
  description: string
  materials: string[] | null
}

interface ProcessFaq {
  question: string
  answer: string
}

interface ProcessChannel {
  name: string
  note: string
}

interface ProcessGuide {
  domain: string
  title: string
  steps: ProcessStep[]
  materials: string[]
  jurisdiction: string[]
  precautions: string[]
  faqs: ProcessFaq[]
  officialChannels: ProcessChannel[]
  disclaimer: string
}

const documentTypeOptions = [
  { label: '劳动仲裁申请书', value: 'LABOR_ARBITRATION_APPLICATION' },
  { label: '民事起诉状', value: 'CIVIL_COMPLAINT' },
  { label: '催告函', value: 'DEMAND_LETTER' }
]

/**
 * 领域 → 可用文书类型（镜像后端 DocumentTemplateServiceImpl 兼容表，不改后端）：
 * 劳动争议 → 劳动仲裁申请书 + 催告函；房屋租赁 / 消费者权益 → 民事起诉状 + 催告函
 */
const domainDocumentTypes: Record<string, string[]> = {
  LABOR_DISPUTE: ['LABOR_ARBITRATION_APPLICATION', 'DEMAND_LETTER'],
  HOUSE_RENTAL: ['CIVIL_COMPLAINT', 'DEMAND_LETTER'],
  CONSUMER_RIGHTS: ['CIVIL_COMPLAINT', 'DEMAND_LETTER']
}

/** 按已选领域收缩文书类型下拉；未选领域时展示全部 */
function documentTypeOptionsFor(domain: string) {
  const allowed = domainDocumentTypes[domain]
  if (!allowed || !allowed.length) return documentTypeOptions
  return documentTypeOptions.filter(t => allowed.includes(t.value))
}

const documentTypeLabels: Record<string, string> = {
  LABOR_ARBITRATION_APPLICATION: '劳动仲裁申请书',
  CIVIL_COMPLAINT: '民事起诉状',
  DEMAND_LETTER: '催告函'
}

function documentTypeLabel(t: string) {
  return documentTypeLabels[t] || t || '未知'
}

function documentTypeTagType(t: string): 'success' | 'warning' | 'danger' | 'info' {
  if (t === 'LABOR_ARBITRATION_APPLICATION') return 'success'
  if (t === 'CIVIL_COMPLAINT') return 'warning'
  if (t === 'DEMAND_LETTER') return 'danger'
  return 'info'
}

const domainOptions = [
  { label: '劳动争议', value: 'LABOR_DISPUTE' },
  { label: '房屋租赁', value: 'HOUSE_RENTAL' },
  { label: '消费者权益', value: 'CONSUMER_RIGHTS' }
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

function generationStatusLabel(s: string) {
  return s === 'GENERATED' ? '已生成' : '未生成'
}

function formatTime(t: string | null) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 19)
}

const emptyForm = () => ({
  title: '',
  documentType: '',
  domain: '',
  caseAnalysisId: null as number | null
})

const formRules: FormRules = {
  title: [{ required: true, message: '请输入文书标题', trigger: 'blur' }],
  documentType: [{ required: true, message: '请选择文书类型', trigger: 'change' }],
  domain: [{ required: true, message: '请选择所属领域', trigger: 'change' }]
}

const activeTab = ref('documents')
const createFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()
const createForm = reactive(emptyForm())
const editForm = reactive<DocumentDraftItem>({
  id: 0, title: '', documentType: '', domain: '', caseAnalysisId: null,
  caseTitle: null, generationStatus: '', content: null, createdAt: '', updatedAt: ''
})

const draftList = ref<DocumentDraftItem[]>([])
const myCases = ref<CaseAnalysisLite[]>([])
const loading = ref(false)
const saving = ref(false)
const editDialogVisible = ref(false)
// 5B：正文编辑副本 / 生成 / 保存正文 / 导出 状态
const editContent = ref<string | null>(null)
const generating = ref(false)
const savingContent = ref(false)
const exporting = ref(false)

const guides = ref<ProcessGuide[]>([])
const selectedGuideDomain = ref('')

const selectedGuide = computed(() => guides.value.find(g => g.domain === selectedGuideDomain.value) || null)

onMounted(async () => {
  loadList()
  loadMyCases()
  loadGuides()
})

async function loadList() {
  loading.value = true
  try {
    const res = await request.get('/document-drafts')
    draftList.value = res.data.data || []
  } catch {
    // 401 由拦截器处理；其余错误保留页面可重试
  } finally {
    loading.value = false
  }
}

/** 关联案件下拉：仅加载当前用户未删除案件（复用模块4 只读列表接口，保留 domain / extractionConfirmed 供深链校验） */
async function loadMyCases() {
  try {
    const res = await request.get('/case-analysis')
    myCases.value = (res.data.data || []).map((c: CaseAnalysisLite) => ({
      id: c.id,
      title: c.title,
      domain: c.domain,
      extractionConfirmed: c.extractionConfirmed
    }))
    applyDeepLink()
  } catch {
    // 401 由拦截器处理；失败时下拉为空，用户仍可不关联案件创建
  }
}

/**
 * Case→Document 深链：/legal-writing?caseAnalysisId=<id>
 * URL 只携带 id，案件本人性由列表接口（仅返回当前用户案件）保证；
 * 仅 extractionConfirmed==1 的案件可自动关联（后端生成不强制确认，但深链语义要求已确认事实背景）。
 * 匹配失败（不存在 / 非本人 / 未确认）→ 友好提示 + 不选中，用户可手动选择或取消。
 */
const route = useRoute()

function applyDeepLink() {
  const raw = route.query.caseAnalysisId
  const id = raw !== undefined ? Number(raw) : NaN
  if (!Number.isInteger(id) || id <= 0) return
  const matched = myCases.value.find(c => c.id === id)
  if (matched && matched.extractionConfirmed === 1) {
    createForm.caseAnalysisId = matched.id
    createForm.domain = matched.domain
    createForm.documentType = ''
    ElMessage.success(`已自动关联案件「${matched.title}」，请选择文书类型`)
  } else if (matched) {
    ElMessage.warning('该案件尚未完成人工确认，无法自动关联生成文书。请先在案件分析中确认 AI 提取结果。')
  } else {
    ElMessage.warning('未找到可关联的已确认案件（不存在或非本人案件），已取消自动关联。')
  }
}

/** 领域变化时清空不兼容的文书类型，避免生成时 400 类型与领域不匹配 */
watch(
  () => createForm.domain,
  d => {
    if (d && !domainDocumentTypes[d]?.includes(createForm.documentType)) createForm.documentType = ''
  }
)
watch(
  () => editForm.domain,
  d => {
    if (d && !domainDocumentTypes[d]?.includes(editForm.documentType)) editForm.documentType = ''
  }
)

/** 流程指引：固定内容只读 API，一次返回三个领域 */
async function loadGuides() {
  try {
    const res = await request.get('/process-guides')
    guides.value = res.data.data || []
    if (guides.value.length && !selectedGuideDomain.value) {
      selectedGuideDomain.value = guides.value[0].domain
    }
  } catch {
    // 401 由拦截器处理；失败时流程指引 Tab 为空态
  }
}

async function handleCreate() {
  if (!createFormRef.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await request.post('/document-drafts', { ...createForm })
    ElMessage.success('保存成功')
    Object.assign(createForm, emptyForm())
    createFormRef.value.resetFields()
    await loadList()
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    saving.value = false
  }
}

async function handleEdit(row: DocumentDraftItem) {
  try {
    const res = await request.get(`/document-drafts/${row.id}`)
    const data = res.data.data as DocumentDraftItem
    Object.assign(editForm, data)
    editContent.value = data.content
    editDialogVisible.value = true
  } catch {
    // 404 等错误由拦截器提示
  }
}

// ---- 5B：智能生成 / 重新生成 / 正文编辑 / DOCX 导出 ----

/** 智能生成文书正文（生成前核对）：取消 → 不调用生成；确认 → 才调用 */
async function handleGenerate() {
  try {
    await ElMessageBox.confirm(
      '生成前请核对案件基本事实、金额、合同情况及人工确认的案件分析信息。AI 生成内容不会替代用户核对。',
      '生成前核对',
      {
        type: 'warning',
        confirmButtonText: '我已核对，继续生成',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  await doGenerate()
}

/** 重新生成：覆盖警告确认（独立于生成前核对，不重复弹出）后走同一生成核心 */
async function handleRegenerate() {
  try {
    await ElMessageBox.confirm('重新生成将覆盖当前已编辑正文，是否继续？', '重新生成确认', {
      type: 'warning',
      confirmButtonText: '继续生成',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await doGenerate()
}

/** 生成核心：耗时较长（LLM 草拟 + 模板组装），覆盖 axios 默认 10s 超时 */
async function doGenerate() {
  if (!editForm.id) return
  generating.value = true
  try {
    const res = await request.post(`/document-drafts/${editForm.id}/generate`, null, { timeout: 120000 })
    const data = res.data.data as DocumentDraftItem
    Object.assign(editForm, data)
    editContent.value = data.content
    ElMessage.success('文书生成成功')
    await loadList()
  } catch {
    // 错误提示由拦截器统一弹出（未关联案件/类型不匹配/生成失败均为此路径）
  } finally {
    generating.value = false
  }
}

/** 保存编辑后的正文（PUT /content）：仅已生成文书可保存，只更新正文 */
async function handleSaveContent() {
  if (!editForm.id) return
  if (editContent.value === null || editContent.value.trim() === '') {
    ElMessage.warning('正文内容不能为空')
    return
  }
  savingContent.value = true
  try {
    const res = await request.put(`/document-drafts/${editForm.id}/content`, { content: editContent.value })
    const data = res.data.data as DocumentDraftItem
    Object.assign(editForm, data)
    editContent.value = data.content
    ElMessage.success('正文保存成功')
    await loadList()
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    savingContent.value = false
  }
}

/**
 * 导出 DOCX：导出前核对确认（取消不下载）。页面正文有未保存修改时优先提示先保存，
 * 不静默导出数据库旧正文；用户仍坚持导出则以已保存正文为准。
 * 下载必须 blob（window.open 无法携带 JWT Authorization 头，会被 401）。
 */
async function handleExport() {
  if (!editForm.id) return
  const unsaved = editContent.value !== editForm.content
  const message = unsaved
    ? '页面正文有尚未保存的修改，导出的 DOCX 将以数据库已保存正文为准（不含页面未保存内容）。请先点击【保存正文】；如坚持导出，将导出已保存正文。是否继续？'
    : '请确认当前页面正文已完成核对并保存。导出的 DOCX 将以当前已保存正文为准。'
  try {
    await ElMessageBox.confirm(message, unsaved ? '未保存正文提示' : '导出前核对', {
      type: 'warning',
      confirmButtonText: unsaved ? '仍以已保存正文导出' : '确认导出',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  exporting.value = true
  try {
    const res = await request.get(`/document-drafts/${editForm.id}/export`, {
      responseType: 'blob',
      timeout: 60000
    })
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${editForm.title || '文书'}.docx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('DOCX 导出成功')
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    exporting.value = false
  }
}

async function handleSaveEdit() {
  if (!editFormRef.value) return
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await request.put(`/document-drafts/${editForm.id}`, {
      title: editForm.title,
      documentType: editForm.documentType,
      domain: editForm.domain,
      caseAnalysisId: editForm.caseAnalysisId
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

async function handleDelete(row: DocumentDraftItem) {
  try {
    await ElMessageBox.confirm(`确定删除文书「${row.title}」吗？删除后不再显示，仍可恢复数据。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await request.delete(`/document-drafts/${row.id}`)
    ElMessage.success('删除成功')
    await loadList()
  } catch {
    // 错误提示由拦截器统一弹出
  }
}
</script>

<style scoped>
.legal-writing {
  min-height: 100vh;
  background: #f5f7fa;
}
.content {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 20px;
}
.page-sub {
  margin: -12px 0 4px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.main-card {
  margin-bottom: 20px;
}
.guide-title {
  margin: 20px 0 8px;
  color: #303133;
}
.step-desc {
  margin: 4px 0 0;
  color: #606266;
  line-height: 1.6;
}
.step-materials {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.guide-block-title {
  margin: 24px 0 10px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
  color: #303133;
  font-size: 15px;
}
.guide-list {
  margin: 0 0 4px;
  padding-left: 22px;
  color: #606266;
  line-height: 1.9;
}
.guide-faq {
  border-top: none;
}
.faq-answer {
  margin: 0;
  color: #606266;
  line-height: 1.8;
}
.channel-name {
  color: #303133;
  font-weight: 500;
  margin-right: 10px;
}
.channel-note {
  color: #909399;
}
.content-section {
  margin-top: 4px;
}
.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.content-title {
  font-weight: 600;
  color: #303133;
}
.content-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.content-text {
  white-space: pre-wrap;
  color: #303133;
  line-height: 1.8;
}
</style>
