<template>
  <div class="legal-manage">
    <h1 class="page-title">法律知识库管理</h1>
    <p class="page-sub">
      维护法规元数据、条款内容与检索向量；支持新增法规、TXT 批量导入与向量构建。失效或删除前请谨慎确认。
    </p>
    <el-tabs v-model="activeTab" type="border-card">
      <!-- Tab 1: Document List -->
      <el-tab-pane label="法规列表" name="list">
        <div class="filter-bar">
          <el-select v-model="filterDomain" placeholder="领域" clearable style="width:160px">
            <el-option label="劳动争议" value="LABOR_DISPUTE" />
            <el-option label="房屋租赁" value="HOUSE_RENTAL" />
            <el-option label="消费者权益" value="CONSUMER_RIGHTS" />
          </el-select>
          <el-select v-model="filterStatus" placeholder="状态" clearable style="width:120px;margin-left:10px">
            <el-option label="有效" :value="1" />
            <el-option label="失效" :value="0" />
          </el-select>
          <el-button type="primary" style="margin-left:10px" @click="fetchDocuments">查询</el-button>
          <el-button type="warning" style="margin-left:10px" :loading="buildingVectors" @click="handleBatchBuild">
            构建待处理向量
          </el-button>
        </div>
        <el-table :data="documents" style="margin-top:15px" v-loading="loadingDocs">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="documentName" label="法规名称" min-width="200" />
          <el-table-column label="领域" width="140">
            <template #default="{ row }">{{ domainLabel(row.domain) }}</template>
          </el-table-column>
          <el-table-column prop="documentType" label="类型" width="100" />
          <el-table-column prop="articleCount" label="条款数" width="80" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.validStatus === 1 ? 'success' : 'danger'" size="small">
                {{ row.validStatus === 1 ? '有效' : '失效' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="向量状态" width="100">
            <template #default="{ row }">
              <el-tag :type="vectorTagType(row.vectorStatus)" size="small">
                {{ vectorLabel(row.vectorStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="生效日期" width="120">
            <template #default="{ row }">{{ row.effectiveDate || '-' }}</template>
          </el-table-column>
          <el-table-column label="来源" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.source || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="320" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="viewArticles(row)">条款</el-button>
              <el-button size="small" type="primary" @click="editDocument(row)">编辑</el-button>
              <el-button size="small" :type="row.validStatus===1?'warning':'success'"
                @click="toggleDocStatus(row)">
                {{ row.validStatus===1 ? '失效' : '有效' }}
              </el-button>
              <el-button size="small" type="danger" @click="deleteDocument(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Tab 2: Add/Edit Document -->
      <el-tab-pane :label="editingDoc ? '编辑法规' : '新增法规'" name="edit">
        <el-form :model="docForm" label-width="100px" style="max-width:600px">
          <el-form-item label="法规名称" required>
            <el-input v-model="docForm.documentName" placeholder="请输入法规名称" />
          </el-form-item>
          <el-form-item label="领域" required>
            <el-select v-model="docForm.domain" placeholder="请选择领域">
              <el-option label="劳动争议" value="LABOR_DISPUTE" />
              <el-option label="房屋租赁" value="HOUSE_RENTAL" />
              <el-option label="消费者权益" value="CONSUMER_RIGHTS" />
            </el-select>
          </el-form-item>
          <el-form-item label="类型" required>
            <el-select v-model="docForm.documentType" placeholder="请选择类型">
              <el-option label="法律" value="LAW" />
              <el-option label="司法解释" value="JUDICIAL_INTERPRETATION" />
              <el-option label="行政法规" value="ADMINISTRATIVE_REGULATION" />
              <el-option label="案例" value="CASE" />
            </el-select>
          </el-form-item>
          <el-form-item label="生效日期">
            <el-date-picker v-model="docForm.effectiveDate" type="date" placeholder="选择日期"
              value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item label="来源说明">
            <el-input v-model="docForm.source" placeholder="如：全国人大常委会" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingDoc" @click="saveDocument">
              {{ editingDoc ? '保存修改' : '创建法规' }}
            </el-button>
            <el-button v-if="editingDoc" @click="cancelEdit">取消编辑</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Tab 3: TXT Upload -->
      <el-tab-pane label="TXT 导入" name="import">
        <el-form label-width="100px" style="max-width:600px">
          <el-form-item label="法规名称" required>
            <el-input v-model="importForm.documentName" placeholder="请输入法规名称" />
          </el-form-item>
          <el-form-item label="领域" required>
            <el-select v-model="importForm.domain" placeholder="请选择领域">
              <el-option label="劳动争议" value="LABOR_DISPUTE" />
              <el-option label="房屋租赁" value="HOUSE_RENTAL" />
              <el-option label="消费者权益" value="CONSUMER_RIGHTS" />
            </el-select>
          </el-form-item>
          <el-form-item label="类型" required>
            <el-select v-model="importForm.documentType" placeholder="请选择类型">
              <el-option label="法律" value="LAW" />
              <el-option label="司法解释" value="JUDICIAL_INTERPRETATION" />
              <el-option label="行政法规" value="ADMINISTRATIVE_REGULATION" />
              <el-option label="案例" value="CASE" />
            </el-select>
          </el-form-item>
          <el-form-item label="生效日期">
            <el-date-picker v-model="importForm.effectiveDate" type="date" placeholder="选择日期"
              value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item label="来源说明">
            <el-input v-model="importForm.source" placeholder="如：全国人大常委会" />
          </el-form-item>
          <el-form-item label="TXT 文件">
            <el-upload :auto-upload="false" accept=".txt" :limit="1"
              :on-change="handleFileChange" :file-list="fileList">
              <el-button type="primary">选择 TXT 文件</el-button>
              <template #tip>
                <div class="el-upload__tip">仅支持 UTF-8 编码的 .txt 文件，按"第X条"标记自动切分</div>
              </template>
            </el-upload>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="importing" @click="doImport" :disabled="!selectedFile">
              开始导入
            </el-button>
          </el-form-item>
        </el-form>
        <div v-if="importResult" style="margin-top:15px">
          <el-alert :title="importResult.message" :type="importResult.success ? 'success' : 'error'"
            :closable="false" show-icon />
          <p v-if="importResult.success" style="margin-top:10px">
            切分条款数：{{ importResult.articleCount }}
          </p>
        </div>
      </el-tab-pane>

      <!-- Tab 4: Article Viewer -->
      <el-tab-pane label="条款查看" name="articles" v-if="selectedDocument">
        <div style="margin-bottom:15px">
          <strong>{{ selectedDocument.documentName }}</strong>
          <span style="margin-left:10px;color:#999">共 {{ selectedDocument.articleCount }} 条条款</span>
          <el-button size="small" type="primary" style="margin-left:15px" @click="showAddArticle=true">
            新增条款
          </el-button>
        </div>
        <div v-if="showAddArticle" style="margin-bottom:15px;padding:10px;background:#f5f7fa">
          <el-input v-model="newArticle.number" placeholder="条款编号" style="width:150px;margin-right:10px" />
          <el-input v-model="newArticle.content" placeholder="条款正文" style="width:400px;margin-right:10px" />
          <el-button type="primary" size="small" @click="addArticle">确认添加</el-button>
          <el-button size="small" @click="showAddArticle=false">取消</el-button>
        </div>
        <el-table :data="articles" v-loading="loadingArticles">
          <el-table-column prop="articleNumber" label="编号" width="120" />
          <el-table-column prop="content" label="正文" min-width="300" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.validStatus===1?'success':'danger'" size="small">
                {{ row.validStatus===1?'有效':'失效' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="向量" width="80">
            <template #default="{ row }">
              <el-tag :type="vectorTagType(row.vectorStatus)" size="small">
                {{ vectorLabel(row.vectorStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260">
            <template #default="{ row }">
              <el-button size="small" @click="editArticle(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteArticle(row)">删除</el-button>
              <el-button size="small" type="warning"
                v-if="row.vectorStatus==='FAILED'||row.vectorStatus==='NONE'"
                @click="rebuildArticleVector(row)">重向量化</el-button>
            </template>
          </el-table-column>
        </el-table>
        <!-- Article edit dialog -->
        <el-dialog v-model="editArticleVisible" title="编辑条款" width="500px">
          <el-form label-width="80px">
            <el-form-item label="编号">
              <el-input v-model="editArticleForm.articleNumber" />
            </el-form-item>
            <el-form-item label="正文">
              <el-input v-model="editArticleForm.content" type="textarea" :rows="4" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="editArticleVisible=false">取消</el-button>
            <el-button type="primary" @click="saveArticle">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const activeTab = ref('list')

// Document list
const documents = ref<any[]>([])
const loadingDocs = ref(false)
const filterDomain = ref('')
const filterStatus = ref<number | null>(null)

// Edit form
const editingDoc = ref<any>(null)
const savingDoc = ref(false)
const docForm = reactive({
  documentName: '', domain: '', documentType: '', effectiveDate: '', source: ''
})

// Import
const importing = ref(false)
const selectedFile = ref<File | null>(null)
const fileList = ref<any[]>([])
const importResult = ref<any>(null)
const importForm = reactive({
  documentName: '', domain: '', documentType: '', effectiveDate: '', source: ''
})

// Articles
const selectedDocument = ref<any>(null)
const articles = ref<any[]>([])
const loadingArticles = ref(false)
const showAddArticle = ref(false)
const newArticle = reactive({ number: '', content: '' })
const editArticleVisible = ref(false)
const editArticleForm = reactive({ id: 0, articleNumber: '', content: '' })

onMounted(() => { fetchDocuments() })

function domainLabel(d: string) {
  const m: Record<string, string> = { LABOR_DISPUTE: '劳动争议', HOUSE_RENTAL: '房屋租赁', CONSUMER_RIGHTS: '消费者权益' }
  return m[d] || d
}

async function fetchDocuments() {
  loadingDocs.value = true
  try {
    const params: any = {}
    if (filterDomain.value) params.domain = filterDomain.value
    if (filterStatus.value !== null && filterStatus.value !== undefined && filterStatus.value !== '') params.validStatus = filterStatus.value
    const res = await request.get('/admin/legal/documents', { params })
    documents.value = res.data.data
  } finally { loadingDocs.value = false }
}

function editDocument(row: any) {
  editingDoc.value = row
  docForm.documentName = row.documentName
  docForm.domain = row.domain
  docForm.documentType = row.documentType
  docForm.effectiveDate = row.effectiveDate || ''
  docForm.source = row.source || ''
  activeTab.value = 'edit'
}

function cancelEdit() {
  editingDoc.value = null
  docForm.documentName = ''
  docForm.domain = ''
  docForm.documentType = ''
  docForm.effectiveDate = ''
  docForm.source = ''
  activeTab.value = 'list'
}

async function saveDocument() {
  savingDoc.value = true
  try {
    if (editingDoc.value) {
      await request.put(`/admin/legal/documents/${editingDoc.value.id}`, docForm)
      ElMessage.success('修改成功')
    } else {
      await request.post('/admin/legal/documents', docForm)
      ElMessage.success('创建成功')
    }
    cancelEdit()
    fetchDocuments()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally { savingDoc.value = false }
}

async function toggleDocStatus(row: any) {
  const newStatus = row.validStatus === 1 ? 0 : 1
  const action = newStatus === 0 ? '失效' : '生效'
  try {
    await ElMessageBox.confirm(
      `确定将「${row.documentName}」${action}吗？${newStatus === 0 ? '失效后该法规将不再参与检索与引用。' : ''}`,
      `${action}确认`,
      { confirmButtonText: `确认${action}`, cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await request.put(`/admin/legal/documents/${row.id}/status`, { status: newStatus })
    ElMessage.success(`${action}成功`)
    fetchDocuments()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function deleteDocument(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.documentName}」及其全部条款？`, '确认删除', { type: 'warning' })
    await request.delete(`/admin/legal/documents/${row.id}`)
    ElMessage.success('删除成功')
    if (selectedDocument.value?.id === row.id) { selectedDocument.value = null; articles.value = [] }
    fetchDocuments()
  } catch { /* cancelled */ }
}

function handleFileChange(file: any) {
  selectedFile.value = file.raw
  fileList.value = [file]
}

async function doImport() {
  if (!selectedFile.value) return
  try {
    await ElMessageBox.confirm(
      `确定导入「${importForm.documentName}」吗？将按"第X条"标记批量创建条款，导入成功后可在法规列表中查看并构建向量。`,
      '导入确认',
      { confirmButtonText: '确认导入', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }
  importing.value = true
  importResult.value = null
  try {
    const fd = new FormData()
    fd.append('file', selectedFile.value)
    fd.append('documentName', importForm.documentName)
    fd.append('domain', importForm.domain)
    fd.append('documentType', importForm.documentType)
    if (importForm.effectiveDate) fd.append('effectiveDate', importForm.effectiveDate)
    if (importForm.source) fd.append('source', importForm.source)
    const res = await request.post('/admin/legal/documents/import', fd)
    importResult.value = res.data.data
    if (res.data.data.success) {
      ElMessage.success('导入成功')
      fetchDocuments()
    }
  } catch (e: any) {
    importResult.value = { success: false, message: e?.response?.data?.message || '导入失败' }
  } finally { importing.value = false }
}

async function viewArticles(row: any) {
  selectedDocument.value = row
  loadingArticles.value = true
  showAddArticle.value = false
  try {
    const res = await request.get(`/admin/legal/documents/${row.id}/articles`)
    articles.value = res.data.data
  } finally { loadingArticles.value = false }
  activeTab.value = 'articles'
}

async function addArticle() {
  try {
    await request.post(`/admin/legal/documents/${selectedDocument.value.id}/articles`, {
      articleNumber: newArticle.number, content: newArticle.content
    })
    ElMessage.success('条款添加成功')
    newArticle.number = ''
    newArticle.content = ''
    showAddArticle.value = false
    viewArticles(selectedDocument.value)
    fetchDocuments()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

function editArticle(row: any) {
  editArticleForm.id = row.id
  editArticleForm.articleNumber = row.articleNumber
  editArticleForm.content = row.content
  editArticleVisible.value = true
}

async function saveArticle() {
  try {
    await request.put(`/admin/legal/articles/${editArticleForm.id}`, {
      articleNumber: editArticleForm.articleNumber, content: editArticleForm.content
    })
    ElMessage.success('修改成功')
    editArticleVisible.value = false
    viewArticles(selectedDocument.value)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function deleteArticle(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除条款「${row.articleNumber}」？`, '确认删除', { type: 'warning' })
    await request.delete(`/admin/legal/articles/${row.id}`)
    ElMessage.success('删除成功')
    viewArticles(selectedDocument.value)
    fetchDocuments()
  } catch { /* cancelled */ }
}

// Vector helpers
function vectorLabel(s: string) {
  const m: Record<string, string> = { NONE: '未构建', PENDING: '处理中', COMPLETED: '已完成', FAILED: '失败' }
  return m[s] || s
}
function vectorTagType(s: string) {
  const m: Record<string, string> = { NONE: 'info', PENDING: 'warning', COMPLETED: 'success', FAILED: 'danger' }
  return m[s] || 'info'
}

const buildingVectors = ref(false)
async function handleBatchBuild() {
  try {
    await ElMessageBox.confirm('将为所有"未构建/失败"的条款批量调用向量服务构建检索向量，可能需要一定时间。是否继续？', '批量构建向量确认', {
      confirmButtonText: '开始构建',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户取消
  }
  buildingVectors.value = true
  try {
    const res = await request.post('/admin/legal/vectors/batch-build')
    const d = res.data.data
    ElMessage.success(`构建完成：共 ${d.total} 条，成功 ${d.completed} 条，失败 ${d.failed} 条`)
    fetchDocuments()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '构建失败')
  } finally { buildingVectors.value = false }
}

async function rebuildArticleVector(row: any) {
  try {
    const res = await request.post(`/admin/legal/vectors/articles/${row.id}/rebuild`)
    ElMessage.success('重新向量化完成: ' + res.data.data)
    viewArticles(selectedDocument.value)
    fetchDocuments()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '重新向量化失败')
  }
}
</script>

<style scoped>
.legal-manage { min-height:100vh; background:#f5f7fa; }
.legal-manage .page-title { margin-bottom: 4px; }
.page-sub {
  margin: -12px 0 16px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.filter-bar { margin-top:10px; }
</style>
