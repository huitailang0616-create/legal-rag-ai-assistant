<template>
  <div class="consultation-history">
    <div class="content">
      <div class="page-head">
        <div>
          <h1 class="page-title">咨询历史</h1>
          <p class="page-sub">查看你过去的法律咨询、回答和引用依据。</p>
        </div>
        <el-button type="primary" @click="$router.push('/consult')">开始新的法律咨询</el-button>
      </div>

      <el-card shadow="never">
        <el-empty v-if="!loading && total === 0" description="还没有咨询记录">
          <p class="empty-desc">完成一次法律咨询后，你可以在这里查看问题、回答和法律依据。</p>
          <el-button type="primary" @click="$router.push('/consult')">开始法律咨询</el-button>
        </el-empty>

        <template v-else>
          <el-table :data="records" v-loading="loading">
            <el-table-column prop="question" label="问题" min-width="260" show-overflow-tooltip />
            <el-table-column label="领域" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="domainTagType(row.domain)">{{ domainLabel(row.domain) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="140">
              <template #default="{ row }">
                <el-tag size="small" :type="row.evidenceInsufficient ? 'warning' : 'success'">
                  {{ statusLabel(row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="openDetail(row.id)">查看详情</el-button>
                <el-button size="small" type="danger" link @click="confirmDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-if="total > pageSize"
            class="pager"
            layout="prev, pager, next, total"
            :total="total"
            :page-size="pageSize"
            :current-page="page"
            @current-change="onPageChange"
          />
        </template>
      </el-card>

      <p class="disclaimer">
        咨询历史为个人隐私数据，仅您本人可见。AI 生成内容仅供法律信息参考，不构成正式法律意见。
      </p>
    </div>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="咨询详情" width="720px" top="6vh">
      <div v-if="detail" class="detail-block">
        <div class="detail-label">咨询问题</div>
        <div class="detail-text">{{ detail.question }}</div>

        <div class="detail-label">咨询信息</div>
        <div class="detail-text">
          <el-tag size="small" :type="domainTagType(detail.domain)" style="margin-right:8px">
            {{ domainLabel(detail.domain) }}
          </el-tag>
          <el-tag size="small" :type="detail.evidenceInsufficient ? 'warning' : 'success'">
            {{ statusLabel(detail) }}
          </el-tag>
        </div>

        <template v-if="detail.evidenceInsufficient">
          <el-alert
            type="warning"
            show-icon
            :title="reasonTitle(detail.relevanceReason)"
            :description="reasonLabel(detail.relevanceReason)"
            class="detail-block"
          />
        </template>

        <div class="detail-label">AI 法律信息</div>
        <div class="detail-text answer">{{ detail.answer }}</div>

        <template v-if="!detail.evidenceInsufficient">
          <div class="detail-label">法律依据</div>
          <el-collapse v-if="detail.citations && detail.citations.length > 0">
            <el-collapse-item
              v-for="(c, idx) in detail.citations"
              :key="c.articleId + '-' + idx"
              :name="idx"
            >
              <template #title>
                <span class="citation-title">{{ c.documentName }} · {{ c.articleNumber }}</span>
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
          <el-empty v-else description="暂无可靠法律依据" :image-size="60" />
        </template>

        <div class="detail-label">咨询时间</div>
        <div class="detail-text">{{ formatTime(detail.createdAt) }}</div>

        <p class="disclaimer">
          AI 生成内容仅供法律信息参考，不构成正式法律意见。具体案件请结合完整事实并咨询专业法律人士。
        </p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

interface Citation {
  articleId: number
  documentId: number
  documentName: string
  articleNumber: string
  source: string
  contentSnippet: string
}

interface HistoryItem {
  id: number
  question: string
  domain: string
  evidenceInsufficient: boolean
  relevanceReason: string
  createdAt: string
}

interface HistoryDetail extends HistoryItem {
  answer: string
  citations: Citation[]
}

const records = ref<HistoryItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const detailVisible = ref(false)
const detail = ref<HistoryDetail | null>(null)

const domainLabels: Record<string, string> = {
  LABOR_DISPUTE: '劳动争议',
  HOUSE_RENTAL: '房屋租赁',
  CONSUMER_RIGHTS: '消费者权益',
  UNKNOWN: '未识别领域'
}

const reasonLabels: Record<string, string> = {
  NON_LEGAL_INTENT: '当前问题似乎不是法律咨询问题。本系统主要提供劳动争议、房屋租赁和消费者权益领域的法律信息咨询。',
  UNSUPPORTED_DOMAIN: '该问题属于法律咨询，但目前超出本系统支持的劳动争议、房屋租赁和消费者权益范围。',
  KNOWLEDGE_GAP: '系统已识别您的法律问题，但当前知识库暂未检索到足够直接的法律依据。'
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

function statusLabel(row: { evidenceInsufficient: boolean; relevanceReason: string }) {
  if (row.evidenceInsufficient) {
    if (row.relevanceReason === 'NON_LEGAL_INTENT') return '非法律问题'
    if (row.relevanceReason === 'UNSUPPORTED_DOMAIN') return '超出支持领域'
    return '依据不足'
  }
  return '已答复'
}

function reasonTitle(r: string) {
  if (r === 'NON_LEGAL_INTENT') return '当前问题似乎不是法律咨询问题'
  if (r === 'UNSUPPORTED_DOMAIN') return '该问题超出当前系统支持的法律领域'
  return '当前知识库未检索到足够直接的法律依据'
}

function reasonLabel(r: string) {
  return reasonLabels[r] || '当前知识库中缺少足够直接的相关法律依据'
}

function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 19)
}

function isHttpUrl(s: string) {
  return /^https?:\/\//i.test(s)
}

async function fetchList() {
  loading.value = true
  try {
    const res = await request.get('/consultations', { params: { page: page.value, size: pageSize.value } })
    const data = res.data.data
    records.value = data.records
    total.value = data.total
  } catch {
    // 错误提示由全局 interceptor 统一弹出（401 由 interceptor 清 token 跳登录），页面不叠加重复提示
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  fetchList()
}

async function openDetail(id: number) {
  try {
    const res = await request.get(`/consultations/${id}`)
    detail.value = res.data.data as HistoryDetail
    detailVisible.value = true
  } catch {
    // 错误提示由全局 interceptor 统一弹出，页面不叠加重复提示
  }
}

async function confirmDelete(row: HistoryItem) {
  try {
    await ElMessageBox.confirm('删除后该记录将不再显示，确定删除这条咨询记录吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await request.delete(`/consultations/${row.id}`)
    ElMessage.success('已删除')
    if (records.value.length === 1 && page.value > 1) page.value -= 1
    await fetchList()
  } catch {
    // 删除失败提示由全局 interceptor 统一弹出，页面不叠加重复提示
  }
}

onMounted(fetchList)
</script>

<style scoped>
.consultation-history {
  min-height: 100vh;
  background: #f5f7fa;
}
.content {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 20px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-head .page-title {
  margin-bottom: 0;
}
.page-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #606266;
}
.empty-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: #909399;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.detail-label {
  font-size: 12px;
  color: #909399;
  margin: 12px 0 4px;
}
.detail-text {
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
}
.detail-text.answer {
  white-space: pre-wrap;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 10px;
}
.detail-block {
  margin-top: 4px;
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
.disclaimer {
  text-align: center;
  font-size: 0.8rem;
  color: #909399;
}
</style>
