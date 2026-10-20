<template>
  <div class="evaluation-manage">
    <h1 class="page-title">系统评测与运行管理</h1>
    <p class="page-sub">
      运行固定评测试题（Direct/基础检索/增强检索）、查看运行批次与咨询运行记录、进行人工评分。
    </p>
    <el-tabs v-model="activeTab" type="border-card" @tab-click="handleTabClick">
      <!-- Tab 1: 固定评测试题 -->
      <el-tab-pane label="固定评测试题" name="questions">
        <div class="filter-bar">
          <el-select v-model="filterDomain" placeholder="领域" clearable style="width:180px" @change="fetchQuestions">
            <el-option label="劳动争议" value="LABOR_DISPUTE" />
            <el-option label="房屋租赁" value="HOUSE_RENTAL" />
            <el-option label="消费者权益" value="CONSUMER_RIGHTS" />
          </el-select>
          <el-button type="primary" style="margin-left:10px" @click="fetchQuestions">查询</el-button>
        </div>
        <el-table :data="questions" style="margin-top:15px" v-loading="loadingQuestions">
          <el-table-column prop="questionCode" label="编号" width="90" />
          <el-table-column label="领域" width="130">
            <template #default="{ row }">{{ domainLabel(row.domain) }}</template>
          </el-table-column>
          <el-table-column label="问题" min-width="360">
            <template #default="{ row }">
              <div>{{ row.questionText }}</div>
              <div v-if="parseKeywords(row.expectedEvidenceKeywords).length > 0" style="margin-top:4px">
                <el-tag
                  v-for="(k, i) in parseKeywords(row.expectedEvidenceKeywords)"
                  :key="i" size="small" type="info" style="margin-right:4px"
                >{{ k }}</el-tag>
                <span style="font-size:11px;color:#c0c4cc">辅助关键词（不参与正式 Hit@5）</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="参考法条（人工标注）" min-width="220">
            <template #default="{ row }">
              <span v-if="formatReferences(row.referenceArticlesJson)">{{ formatReferences(row.referenceArticlesJson) }}</span>
              <span v-else style="color:#c0c4cc">未标注</span>
            </template>
          </el-table-column>
          <el-table-column label="启用状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
                {{ row.enabled === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Tab 2: 评测执行（6B） -->
      <el-tab-pane label="评测执行" name="execution">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          style="margin-top:10px"
          title="Reference Article Hit@5：判断最终权威 Top5 是否命中人工预先标注的参考法条；expected_evidence_keywords（辅助关键词）不参与正式 Hit@5。"
        />
        <!-- 批次控制 -->
        <el-card shadow="never" style="margin-top:10px">
          <div class="exec-actions">
            <el-input v-model="runName" placeholder="批次名称（可选，默认自动生成）" style="width:260px" :disabled="executing" />
            <el-button type="primary" :loading="executing" :disabled="executing" @click="startRun">
              开始完整评测
            </el-button>
            <el-button :disabled="executing" @click="resumeRun">继续评测</el-button>
            <el-button :disabled="!currentRunId" @click="exportCsv">导出 CSV</el-button>
            <el-button :disabled="!currentRunId" @click="refreshAll">刷新</el-button>
          </div>
          <div v-if="currentRunId" class="run-info" style="margin-top:12px">
            <el-tag size="small" style="margin-right:8px">批次 #{{ currentRunId }}</el-tag>
            <el-tag size="small" :type="runStatusTagType(runStatus)" style="margin-right:8px">{{ runStatusLabel(runStatus) }}</el-tag>
            <span style="font-size:13px;color:#606266">
              共 {{ progress.total }} 条：待执行 {{ progress.pending }} / 执行中 {{ progress.running }} /
              成功 {{ progress.success }} / 拒绝 {{ progress.rejected }} / 失败 {{ progress.failed }}
            </span>
            <el-progress
              v-if="progress.total > 0"
              :percentage="Math.round((progress.total - progress.pending - progress.running) * 100 / progress.total)"
              style="width:420px;margin-left:16px"
              :status="runStatus === 'COMPLETED' ? 'success' : undefined"
            />
          </div>
          <el-alert
            v-if="executing"
            type="info"
            :closable="false"
            style="margin-top:10px"
            title="正在自动执行剩余评测（真实 LLM/检索调用，每条间隔 8 秒）…… 页面刷新后可点击「继续评测」恢复进度。"
          />
        </el-card>

        <!-- 图表 -->
        <el-row :gutter="16" style="margin-top:16px">
          <el-col :span="8">
            <el-card shadow="never" :header="avgTimeTitle">
              <div ref="chartAvgTime" class="chart-box"></div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" :header="hitRateTitle">
              <div ref="chartHitRate" class="chart-box"></div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" header="人工评分均值（1低5高，幻觉维度 1=无明显幻觉 → 5=严重幻觉）">
              <div ref="chartScore" class="chart-box"></div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 结果表 -->
        <el-card shadow="never" style="margin-top:16px">
          <div class="filter-bar">
            <el-select v-model="filterResDomain" placeholder="领域" clearable style="width:150px">
              <el-option label="劳动争议" value="LABOR_DISPUTE" />
              <el-option label="房屋租赁" value="HOUSE_RENTAL" />
              <el-option label="消费者权益" value="CONSUMER_RIGHTS" />
            </el-select>
            <el-select v-model="filterResMode" placeholder="方案" clearable style="width:150px;margin-left:10px">
              <el-option label="直接问答" value="DIRECT_LLM" />
              <el-option label="基础检索" value="BASIC_RAG" />
              <el-option label="增强检索" value="ENHANCED_RAG" />
            </el-select>
            <el-select v-model="filterResStatus" placeholder="执行状态" clearable style="width:150px;margin-left:10px">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="拒绝" value="REJECTED" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-button type="primary" style="margin-left:10px" @click="fetchResults">查询</el-button>
          </div>
          <el-table :data="filteredResults" style="margin-top:15px" v-loading="loadingResults" :max-height="560">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="questionCode" label="题号" width="80" />
            <el-table-column label="领域" width="110">
              <template #default="{ row }">{{ domainLabel(row.domain) }}</template>
            </el-table-column>
            <el-table-column label="方案" width="110">
              <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="resStatusTagType(row.executionStatus)" size="small">{{ resStatusLabel(row.executionStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="错误码" width="170">
              <template #default="{ row }">
                <span v-if="row.errorCode" :title="row.errorCode">{{ resErrorLabel(row.errorCode) }}</span>
                <span v-else style="color:#c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column label="命中口径" width="140">
              <template #default="{ row }">{{ metricVersionLabel(row.metricVersion) }}</template>
            </el-table-column>
            <el-table-column label="检索命中" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.retrievalHit === 1" type="success" size="small">命中</el-tag>
                <el-tag v-else-if="row.retrievalHit === 0" type="info" size="small">未命中</el-tag>
                <span v-else style="color:#c0c4cc">不适用</span>
              </template>
            </el-table-column>
            <el-table-column label="耗时(ms)" width="100">
              <template #default="{ row }">{{ row.responseTimeMs ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="人工评分" width="200">
              <template #default="{ row }">
                <span v-if="row.answerRelevanceScore" style="font-size:12px">
                  相关{{ row.answerRelevanceScore }} / 法律{{ row.legalCorrectnessScore ?? '-' }} /
                  引用{{ row.citationCompletenessScore ?? '-' }} / 幻觉{{ row.hallucinationScore ?? '-' }}
                </span>
                <span v-else style="color:#c0c4cc;font-size:12px">未评分</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row)">查看/评分</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loadingResults && filteredResults.length === 0" description="暂无结果，请先创建评测批次" />
        </el-card>
      </el-tab-pane>

      <!-- Tab 3: 评测批次 -->
      <el-tab-pane label="评测批次" name="runs">
        <el-table :data="runs" style="margin-top:15px" v-loading="loadingRuns">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="批次名称" min-width="200" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="runStatusTagType(row.status)" size="small">{{ runStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="命中口径" width="150">
            <template #default="{ row }">{{ metricVersionLabel(row.metricVersion) }}</template>
          </el-table-column>
          <el-table-column prop="startedAt" label="开始时间" width="180" />
          <el-table-column prop="finishedAt" label="结束时间" width="180" />
        </el-table>
        <el-empty v-if="!loadingRuns && runs.length === 0" description="暂无评测记录" style="margin-top:20px" />
      </el-tab-pane>

      <!-- Tab 4: 运行日志 -->
      <el-tab-pane label="运行日志" name="logs">
        <div class="filter-bar">
          <el-date-picker
            v-model="filterTimeRange"
            type="datetimerange"
            range-separator="~"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width:360px"
          />
          <el-input v-model="filterOperation" placeholder="操作类型" clearable style="width:180px;margin-left:10px" />
          <el-input v-model="filterUsername" placeholder="用户名" clearable style="width:150px;margin-left:10px" />
          <el-button type="primary" style="margin-left:10px" @click="fetchLogs">查询</el-button>
          <el-button style="margin-left:10px" @click="resetLogs">重置</el-button>
        </div>
        <el-table :data="logs" style="margin-top:15px" v-loading="loadingLogs">
          <el-table-column prop="createdAt" label="时间" width="180" />
          <el-table-column label="用户" width="150">
            <template #default="{ row }">{{ row.username || row.userId }}</template>
          </el-table-column>
          <el-table-column prop="operation" label="操作类型" width="180" />
          <el-table-column prop="target" label="目标" min-width="180" show-overflow-tooltip />
          <el-table-column prop="result" label="结果" min-width="200" show-overflow-tooltip />
        </el-table>
        <el-pagination
          v-if="logTotal > 0"
          style="margin-top:15px;justify-content:flex-end"
          layout="total, prev, pager, next"
          :total="logTotal"
          :page-size="logPageSize"
          :current-page="logPage"
          @current-change="onLogPageChange"
        />
      </el-tab-pane>

      <!-- Tab 5: 咨询运行记录（Final-Requirement-Gaps-B） -->
      <el-tab-pane label="咨询运行记录" name="runtime">
        <div class="filter-bar">
          <el-date-picker
            v-model="runtimeTimeRange"
            type="datetimerange"
            range-separator="~"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width:360px"
          />
          <el-select v-model="runtimeDomain" placeholder="领域" clearable style="width:150px;margin-left:10px">
            <el-option label="劳动争议" value="LABOR_DISPUTE" />
            <el-option label="房屋租赁" value="HOUSE_RENTAL" />
            <el-option label="消费者权益" value="CONSUMER_RIGHTS" />
            <el-option label="未识别" value="UNKNOWN" />
          </el-select>
          <el-select v-model="runtimeStatus" placeholder="执行状态" clearable style="width:150px;margin-left:10px">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="正常拒绝" value="REJECTED" />
            <el-option label="执行失败" value="FAILED" />
          </el-select>
          <el-input v-model="runtimeUsername" placeholder="用户名" clearable style="width:150px;margin-left:10px" />
          <el-button type="primary" style="margin-left:10px" @click="fetchRuntimeRecords">查询</el-button>
          <el-button style="margin-left:10px" @click="resetRuntime">重置</el-button>
        </div>
        <el-table :data="runtimeRecords" style="margin-top:15px" v-loading="loadingRuntime">
          <el-table-column prop="createdAt" label="时间" width="180" />
          <el-table-column label="用户" width="130">
            <template #default="{ row }">{{ row.username || row.userId }}</template>
          </el-table-column>
          <el-table-column prop="questionSummary" label="问题摘要" min-width="260" show-overflow-tooltip />
          <el-table-column label="领域" width="110">
            <template #default="{ row }">{{ domainLabel(row.domain) }}</template>
          </el-table-column>
          <el-table-column label="执行路径" width="130">
            <template #default="{ row }">{{ retrievalModeLabel(row.retrievalMode) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="runtimeStatusTagType(row.executionStatus)" size="small">{{ runtimeStatusLabel(row.executionStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时(ms)" width="90">
            <template #default="{ row }">{{ row.responseTimeMs ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="错误码" width="170">
            <template #default="{ row }">
              <span v-if="row.errorCode" :title="row.errorCode">{{ runtimeErrorLabel(row.errorCode) }}</span>
              <span v-else style="color:#c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openRuntimeDetail(row)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loadingRuntime && runtimeRecords.length === 0" description="暂无咨询运行记录" style="margin-top:20px" />
        <el-pagination
          v-if="runtimeTotal > 0"
          style="margin-top:15px;justify-content:flex-end"
          layout="total, prev, pager, next"
          :total="runtimeTotal"
          :page-size="runtimePageSize"
          :current-page="runtimePage"
          @current-change="onRuntimePageChange"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 结果详情 / 评分弹窗 -->
    <el-dialog v-model="detailVisible" title="结果详情与人工评分" width="760px" top="4vh">
      <template v-if="currentResult">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="题号">{{ currentResult.questionCode }}</el-descriptions-item>
          <el-descriptions-item label="领域">{{ domainLabel(currentResult.domain) }}</el-descriptions-item>
          <el-descriptions-item label="方案">{{ modeLabel(currentResult.mode) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ resStatusLabel(currentResult.executionStatus) }}</el-descriptions-item>
          <el-descriptions-item label="错误码">
            <span v-if="currentResult.errorCode" :title="currentResult.errorCode">{{ resErrorLabel(currentResult.errorCode) }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ currentResult.responseTimeMs ?? '-' }} ms</el-descriptions-item>
        </el-descriptions>
        <div class="detail-block">
          <div class="detail-label">题目</div>
          <div class="detail-text">{{ currentResult.question }}</div>
        </div>
        <div class="detail-block">
          <div class="detail-label">参考法条（人工标注）</div>
          <div class="detail-text">{{ formatReferences(currentResult.referenceArticlesJson) || '-' }}</div>
        </div>
        <div class="detail-block">
          <div class="detail-label">命中指标（{{ metricVersionLabel(currentResult.metricVersion) }}）</div>
          <div class="detail-text">
            <template v-if="currentResult.metricVersion === 'REFERENCE_ARTICLE_TOP5_V2'">
              <template v-if="currentResult.retrievalHit === 1">
                <el-tag type="success" size="small">命中</el-tag>
                <span style="margin-left:6px;color:#67c23a">参考法条出现在最终权威 Top5 中</span>
              </template>
              <template v-else-if="currentResult.retrievalHit === 0">
                <el-tag type="info" size="small">未命中</el-tag>
                <span style="margin-left:6px">参考法条不在最终权威 Top5 中</span>
              </template>
              <template v-else>
                <span style="color:#c0c4cc">不适用（DIRECT 模式，或检索完成前拒绝/失败）</span>
                <span v-if="currentResult.errorCode === 'REFERENCE_ARTICLE_NOT_FOUND'" style="margin-left:6px;color:#e6a23c">
                  （参考法条解析失败：有效知识库中未找到对应条款）
                </span>
                <span v-else-if="currentResult.errorCode === 'REFERENCE_ARTICLE_AMBIGUOUS'" style="margin-left:6px;color:#e6a23c">
                  （参考法条解析失败：存在多条匹配，标注需人工修正）
                </span>
              </template>
            </template>
            <template v-else>
              <span style="color:#c0c4cc">历史指标：关键词命中（Legacy），不参与参考法条 Hit@5</span>
            </template>
          </div>
        </div>
        <div class="detail-block">
          <div class="detail-label">最终权威 Top5 命中文档 ID（retrieved_article_ids）</div>
          <div class="detail-text">{{ currentResult.retrievedArticleIds || '-' }}</div>
        </div>
        <div class="detail-block">
          <div class="detail-label">引用（backend-generated）</div>
          <pre v-if="hasCitations(currentResult.citationsJson)" class="detail-pre">{{ formatJson(currentResult.citationsJson) }}</pre>
          <div v-else class="detail-text">无引用</div>
        </div>
        <div class="detail-block">
          <div class="detail-label">回答 / 拒绝原因 / 错误信息</div>
          <pre class="detail-pre answer-pre">{{ currentResult.answer || '-' }}</pre>
        </div>
        <el-divider content-position="left">人工评分（1~5；幻觉 1=无明显幻觉 → 5=严重幻觉）</el-divider>
        <div class="score-form">
          <div class="score-item">
            <span>答案相关性</span>
            <el-rate v-model="scoreForm.answerRelevanceScore" :max="5" allow-half clearable />
          </div>
          <div class="score-item">
            <span>法律正确性</span>
            <el-rate v-model="scoreForm.legalCorrectnessScore" :max="5" allow-half clearable />
          </div>
          <div class="score-item">
            <span>引用完整性</span>
            <el-rate v-model="scoreForm.citationCompletenessScore" :max="5" allow-half clearable />
          </div>
          <div class="score-item">
            <span>幻觉程度</span>
            <el-rate v-model="scoreForm.hallucinationScore" :max="5" allow-half clearable />
            <span class="score-hint">分数越低越好（1=无明显幻觉 → 5=严重幻觉）</span>
          </div>
          <div class="score-item comment">
            <span>备注</span>
            <el-input v-model="scoreForm.manualComment" maxlength="500" show-word-limit type="textarea" :rows="2" style="flex:1" />
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" :loading="savingScore" @click="saveScore">保存评分</el-button>
      </template>
    </el-dialog>

    <!-- 咨询运行记录详情（Final-Requirement-Gaps-B）：FAILED 无 answer/retrieval 也须正常展示 -->
    <el-dialog v-model="runtimeDetailVisible" title="咨询运行记录详情" width="820px" top="4vh">
      <template v-if="currentRuntimeDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="用户">{{ currentRuntimeDetail.username || currentRuntimeDetail.userId }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ currentRuntimeDetail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="领域">{{ domainLabel(currentRuntimeDetail.domain) }}</el-descriptions-item>
          <el-descriptions-item label="执行路径">{{ retrievalModeLabel(currentRuntimeDetail.retrievalMode) }}</el-descriptions-item>
          <el-descriptions-item label="执行状态">
            <el-tag :type="runtimeStatusTagType(currentRuntimeDetail.executionStatus)" size="small">
              {{ runtimeStatusLabel(currentRuntimeDetail.executionStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="响应时间">{{ currentRuntimeDetail.responseTimeMs ?? '-' }} ms</el-descriptions-item>
          <el-descriptions-item label="错误码">{{ currentRuntimeDetail.errorCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="拒绝/降级原因">
            {{ currentRuntimeDetail.relevanceReason || '-' }}
            <span v-if="currentRuntimeDetail.fallbackReason" class="fallback-reason">（{{ currentRuntimeDetail.fallbackReason }}）</span>
          </el-descriptions-item>
        </el-descriptions>
        <div class="detail-block">
          <div class="detail-label">【用户问题】</div>
          <div class="detail-text">{{ currentRuntimeDetail.question }}</div>
        </div>
        <div class="detail-block">
          <div class="detail-label">【Top5 检索条款】</div>
          <div v-if="!currentRuntimeDetail.top5Articles || currentRuntimeDetail.top5Articles.length === 0" class="detail-text">
            未执行检索（NO_RETRIEVAL 或检索前失败）
          </div>
          <div v-for="(a, idx) in currentRuntimeDetail.top5Articles" :key="a.articleId" class="article-item">
            <div class="article-head">
              <el-tag size="small" type="info">{{ idx + 1 }}</el-tag>
              <template v-if="a.missing">
                <span style="margin-left:6px">条款已不存在或已失效（articleId={{ a.articleId }}）</span>
              </template>
              <template v-else>
                <span style="font-weight:600;margin-left:6px">{{ a.documentName }}</span>
                <span style="color:#909399;margin-left:8px">{{ a.articleNumber }}</span>
                <span style="color:#909399;margin-left:8px">articleId={{ a.articleId }}</span>
              </template>
            </div>
            <div v-if="!a.missing" class="detail-text article-content">{{ a.content }}</div>
          </div>
        </div>
        <div class="detail-block">
          <div class="detail-label">【最终引用】</div>
          <div v-if="!currentRuntimeDetail.citations || currentRuntimeDetail.citations.length === 0" class="detail-text">无引用</div>
          <div v-for="(c, idx) in currentRuntimeDetail.citations" :key="idx" class="article-item">
            <div class="article-head">
              <span style="font-weight:600">{{ c.documentName }}</span>
              <span style="color:#909399;margin-left:8px">{{ c.articleNumber }}</span>
              <span style="color:#909399;margin-left:8px">{{ c.source }}</span>
            </div>
            <div v-if="c.contentSnippet" class="detail-text article-content">{{ c.contentSnippet }}</div>
          </div>
        </div>
        <div class="detail-block">
          <div class="detail-label">【模型回答】</div>
          <pre class="detail-pre answer-pre">{{ currentRuntimeDetail.answer || '（无回答：执行失败或未生成回答）' }}</pre>
        </div>
        <div class="detail-block">
          <div class="detail-label">【错误信息】</div>
          <div class="detail-text">
            <span v-if="currentRuntimeDetail.errorCode">
              {{ runtimeErrorLabel(currentRuntimeDetail.errorCode) }}（{{ currentRuntimeDetail.errorCode }}）
            </span>
            <span v-else>无错误</span>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="runtimeDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts } from 'echarts/core'

echarts.use([BarChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer])

const activeTab = ref('questions')

// ---- 固定评测试题 ----
const questions = ref<any[]>([])
const loadingQuestions = ref(false)
const filterDomain = ref('')

// ---- 评测执行（6B） ----
const runName = ref('')
const executing = ref(false)
const currentRunId = ref<number | null>(null)
const runStatus = ref('')
const progress = ref({ total: 0, pending: 0, running: 0, success: 0, rejected: 0, failed: 0 })
const results = ref<any[]>([])
const loadingResults = ref(false)
const summary = ref<any>(null)

const filterResDomain = ref('')
const filterResMode = ref('')
const filterResStatus = ref('')

/** 命中率图卡标题：随当前 run 口径切换 + 有效样本数 N（仅 BASIC/ENHANCED 且 retrieval_hit 非空） */
const hitRateTitle = computed(() => {
  let n = 0
  const modes = summary.value?.modes as any[] | undefined
  if (modes) {
    for (const m of modes) {
      if (m.mode !== 'DIRECT_LLM') n += m.retrievalEvaluatedCount ?? 0
    }
  }
  const metric = results.value[0]?.metricVersion
  if (metric === 'LEGACY_KEYWORD_V1') return `关键词命中率（Legacy，有效样本 N=${n}）`
  return `参考法条命中率@5（有效样本 N=${n}）`
})

const chartAvgTime = ref<HTMLElement>()
const chartHitRate = ref<HTMLElement>()
const chartScore = ref<HTMLElement>()
let chartAvgTimeIns: ECharts | null = null
let chartHitRateIns: ECharts | null = null
let chartScoreIns: ECharts | null = null

// ---- 评分弹窗 ----
const detailVisible = ref(false)
const currentResult = ref<any>(null)
const savingScore = ref(false)
const scoreForm = ref({
  answerRelevanceScore: 0,
  legalCorrectnessScore: 0,
  citationCompletenessScore: 0,
  hallucinationScore: 0,
  manualComment: '',
})

// ---- 评测批次 ----
const runs = ref<any[]>([])
const loadingRuns = ref(false)

// ---- 运行日志 ----
const logs = ref<any[]>([])
const loadingLogs = ref(false)
const filterOperation = ref('')
const filterUsername = ref('')
const filterTimeRange = ref<string[] | null>(null)
const logPage = ref(1)
const logPageSize = ref(20)
const logTotal = ref(0)

// ---- 咨询运行记录（Final-Requirement-Gaps-B） ----
const runtimeRecords = ref<any[]>([])
const loadingRuntime = ref(false)
const runtimeTimeRange = ref<string[] | null>(null)
const runtimeDomain = ref('')
const runtimeStatus = ref('')
const runtimeUsername = ref('')
const runtimePage = ref(1)
const runtimePageSize = ref(20)
const runtimeTotal = ref(0)
const runtimeDetailVisible = ref(false)
const currentRuntimeDetail = ref<any>(null)

onMounted(() => {
  fetchQuestions()
  fetchRuns()
  window.addEventListener('resize', onWindowResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onWindowResize)
  chartAvgTimeIns?.dispose()
  chartHitRateIns?.dispose()
  chartScoreIns?.dispose()
})

function handleTabClick(tab: any) {
  if (tab.paneName === 'logs') fetchLogs()
  if (tab.paneName === 'runtime') fetchRuntimeRecords()
  if (tab.paneName === 'execution') {
    nextTick(() => renderCharts())
    refreshAll()
  }
}

// ---------------- 评测执行 ----------------

async function startRun() {
  if (executing.value) return
  const name = runName.value.trim() || ('评测批次-' + new Date().toLocaleString())
  const res = await request.post('/admin/evaluations/runs', { name })
  currentRunId.value = res.data.data.id
  runStatus.value = res.data.data.status
  ElMessage.success(`批次 #${currentRunId.value} 已创建，共 90 条结果，开始执行`)
  await runLoop()
}

async function resumeRun() {
  if (executing.value) return
  await fetchRuns()
  if (runs.value.length === 0) {
    ElMessage.warning('暂无评测批次')
    return
  }
  const latest = runs.value[0]
  const detail = await request.get(`/admin/evaluations/runs/${latest.id}`)
  const d = detail.data.data
  currentRunId.value = latest.id
  runStatus.value = d.run.status
  progress.value = {
    total: d.total, pending: d.pending, running: d.running,
    success: d.success, rejected: d.rejected, failed: d.failed,
  }
  if (d.pending + d.running === 0) {
    ElMessage.info('该批次已全部完成，无需继续')
    await fetchResults()
    await fetchSummary()
  } else {
    ElMessage.success(`恢复批次 #${latest.id}，剩余 ${d.pending + d.running} 条待执行`)
    await runLoop()
  }
}

async function runLoop() {
  if (executing.value) return
  if (!currentRunId.value) return
  executing.value = true
  try {
    // 每次推进一条，间隔 8 秒；execute-next 覆盖全局 10s 超时
    while (true) {
      const res = await request.post(
        `/admin/evaluations/runs/${currentRunId.value}/execute-next`,
        {},
        { timeout: 180000 },
      )
      const d = res.data.data
      await refreshAll()
      if (!d.executedResultId || d.pendingCount === 0 || d.runStatus === 'COMPLETED') {
        ElMessage.success(`批次 #${currentRunId.value} 评测执行完成`)
        break
      }
      await sleep(8000)
    }
  } catch (e: any) {
    ElMessage.error('执行推进失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    if (e?.response?.data?.code === 409) {
      ElMessage.warning('存在进行中的评测批次（活跃批次限制）')
    }
    executing.value = false
    await refreshAll()
  }
  executing.value = false
  await fetchRuns()
}

async function refreshAll() {
  await refreshProgress()
  await Promise.all([fetchResults(), fetchSummary(), fetchRuns()])
  nextTick(() => renderCharts())
}

async function refreshProgress() {
  if (!currentRunId.value) return
  const res = await request.get(`/admin/evaluations/runs/${currentRunId.value}`)
  const d = res.data.data
  runStatus.value = d.run.status
  progress.value = {
    total: d.total, pending: d.pending, running: d.running,
    success: d.success, rejected: d.rejected, failed: d.failed,
  }
}

async function fetchResults() {
  if (!currentRunId.value) return
  loadingResults.value = true
  try {
    const res = await request.get(`/admin/evaluations/runs/${currentRunId.value}/results`)
    results.value = res.data.data
  } finally { loadingResults.value = false }
}

async function fetchSummary() {
  if (!currentRunId.value) return
  const res = await request.get(`/admin/evaluations/runs/${currentRunId.value}/summary`)
  summary.value = res.data.data
}

async function exportCsv() {
  if (!currentRunId.value) return
  const res = await request.get(`/admin/evaluations/runs/${currentRunId.value}/export.csv`, {
    responseType: 'blob',
    timeout: 60000,
  })
  const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `evaluation_run_${currentRunId.value}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('CSV 已导出')
}

const filteredResults = computed(() => {
  let list = results.value
  if (filterResDomain.value) list = list.filter((r) => r.domain === filterResDomain.value)
  if (filterResMode.value) list = list.filter((r) => r.mode === filterResMode.value)
  if (filterResStatus.value) list = list.filter((r) => r.executionStatus === filterResStatus.value)
  return list
})

// ---------------- 图表 ----------------

function renderCharts() {
  if (!summary.value) return
  const modes = summary.value.modes as any[]
  if (modes.length === 0) return

  // 图1：平均响应时间
  if (chartAvgTime.value) {
    chartAvgTimeIns?.dispose()
    chartAvgTimeIns = echarts.init(chartAvgTime.value)
    chartAvgTimeIns.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 60, right: 20, top: 30, bottom: 40 },
      xAxis: { type: 'category', data: modes.map((m) => modeLabel(m.mode)), axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', name: 'ms' },
      series: [{
        type: 'bar',
        data: modes.map((m) => m.avgResponseTimeMs ?? 0),
        itemStyle: { color: '#409eff' },
        barMaxWidth: 48,
      }],
    })
  }

  // 图2：检索命中率（仅 BASIC/ENHANCED，DIRECT 恒为 NULL）
  if (chartHitRate.value) {
    chartHitRateIns?.dispose()
    chartHitRateIns = echarts.init(chartHitRate.value)
    const rag = modes.filter((m) => m.mode !== 'DIRECT_LLM')
    chartHitRateIns.setOption({
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          const m = rag[params[0]?.dataIndex]
          if (!m) return ''
          const rate = m.retrievalHitRate === null || m.retrievalHitRate === undefined
            ? '暂无数据' : (m.retrievalHitRate * 100).toFixed(1) + '%'
          return `${modeLabel(m.mode)}<br/>命中率：${rate}<br/>命中 ${m.retrievalHitCount} / 判定 ${m.retrievalEvaluatedCount}`
        },
      },
      grid: { left: 60, right: 20, top: 30, bottom: 40 },
      xAxis: { type: 'category', data: rag.map((m) => modeLabel(m.mode)), axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', name: '命中率', max: 1, axisLabel: { formatter: (v: number) => (v * 100).toFixed(0) + '%' } },
      series: [{
        type: 'bar',
        data: rag.map((m) => m.retrievalHitRate ?? 0),
        itemStyle: { color: '#67c23a' },
        barMaxWidth: 48,
      }],
    })
  }

  // 图3：人工评分均值（多方案系列；幻觉维度方向相反已标注）
  if (chartScore.value) {
    chartScoreIns?.dispose()
    chartScoreIns = echarts.init(chartScore.value)
    const dims = ['相关度', '法律正确性', '引用完整性', '幻觉']
    chartScoreIns.setOption({
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          let s = dims[params[0]?.dataIndex] || ''
          for (const p of params) {
            const m = modes[p.seriesIndex]
            const sample = [m.answerRelevanceSampleCount, m.legalCorrectnessSampleCount,
              m.citationCompletenessSampleCount, m.hallucinationSampleCount][p.dataIndex]
            s += `<br/>${p.marker}${p.seriesName}：${p.value ?? '-'}（样本 ${sample}）`
          }
          return s
        },
      },
      legend: { top: 0 },
      grid: { left: 40, right: 20, top: 30, bottom: 40 },
      xAxis: { type: 'category', data: dims, axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', max: 5, min: 0 },
      series: modes.map((m) => ({
        name: modeLabel(m.mode),
        type: 'bar',
        data: [m.avgAnswerRelevance, m.avgLegalCorrectness, m.avgCitationCompleteness, m.avgHallucination]
          .map((v) => (v === null || v === undefined ? null : Number(v.toFixed(2)))),
        barMaxWidth: 20,
      })),
    })
  }
}

function onWindowResize() {
  chartAvgTimeIns?.resize()
  chartHitRateIns?.resize()
  chartScoreIns?.resize()
}

// ---------------- 评分 ----------------

function openDetail(row: any) {
  currentResult.value = row
  scoreForm.value = {
    answerRelevanceScore: row.answerRelevanceScore ?? 0,
    legalCorrectnessScore: row.legalCorrectnessScore ?? 0,
    citationCompletenessScore: row.citationCompletenessScore ?? 0,
    hallucinationScore: row.hallucinationScore ?? 0,
    manualComment: row.manualComment ?? '',
  }
  detailVisible.value = true
}

async function saveScore() {
  if (!currentResult.value) return
  savingScore.value = true
  try {
    const payload: any = { manualComment: scoreForm.value.manualComment || null }
    if (scoreForm.value.answerRelevanceScore) payload.answerRelevanceScore = scoreForm.value.answerRelevanceScore
    if (scoreForm.value.legalCorrectnessScore) payload.legalCorrectnessScore = scoreForm.value.legalCorrectnessScore
    if (scoreForm.value.citationCompletenessScore) payload.citationCompletenessScore = scoreForm.value.citationCompletenessScore
    if (scoreForm.value.hallucinationScore) payload.hallucinationScore = scoreForm.value.hallucinationScore
    await request.put(`/admin/evaluations/results/${currentResult.value.id}/score`, payload)
    ElMessage.success('评分已保存')
    detailVisible.value = false
    await Promise.all([fetchResults(), fetchSummary()])
    nextTick(() => renderCharts())
  } finally { savingScore.value = false }
}

function formatJson(s: string | null) {
  if (!s || s === '[]' || s === 'null') return s || '[]'
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

/** 辅助关键词 JSON -> string[]（兼容 null/损坏 JSON） */
function parseKeywords(json: string | null | undefined): string[] {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr.map(String) : []
  } catch {
    return []
  }
}

/** 参考法条 JSON -> 《法律名称》第X条；《法律名称》第Y条 */
function formatReferences(json: string | null | undefined): string {
  if (!json) return ''
  try {
    const arr = JSON.parse(json)
    if (!Array.isArray(arr)) return ''
    return arr
      .filter((r: any) => r && r.documentName && r.articleNumber)
      .map((r: any) => `《${r.documentName}》${r.articleNumber}`)
      .join('；')
  } catch {
    return ''
  }
}

/** 命中口径可读文本 */
function metricVersionLabel(s: string | null | undefined): string {
  const m: Record<string, string> = {
    REFERENCE_ARTICLE_TOP5_V2: '参考法条Hit@5',
    LEGACY_KEYWORD_V1: '关键词命中(Legacy)',
  }
  return m[s || ''] || s || '-'
}

/** 评测结果错误码可读化：参考法条解析错误 + 运行期错误（与咨询运行记录同全集） */
function resErrorLabel(s: string) {
  const m: Record<string, string> = {
    REFERENCE_ARTICLE_NOT_FOUND: '参考法条解析失败（未找到对应条款）',
    REFERENCE_ARTICLE_AMBIGUOUS: '参考法条解析失败（多条匹配待修正）',
  }
  return m[s] || runtimeErrorLabel(s)
}

/** 引用 JSON 是否有真实内容（null / 空串 / [] / 'null' 均视为无引用） */
function hasCitations(s: string | null | undefined): boolean {
  if (!s) return false
  if (s === 'null' || s === '[]') return false
  return true
}

/** 平均响应时间图卡标题：总执行样本数（各模式 total 合计） */
const avgTimeTitle = computed(() => {
  let n = 0
  const modes = summary.value?.modes as any[] | undefined
  if (modes) for (const m of modes) n += m.total ?? 0
  return `平均响应时间 (ms)（执行样本 N=${n}）`
})

// ---------------- 通用 ----------------

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function domainLabel(d: string) {
  const m: Record<string, string> = { LABOR_DISPUTE: '劳动争议', HOUSE_RENTAL: '房屋租赁', CONSUMER_RIGHTS: '消费者权益', UNKNOWN: '未识别' }
  return m[d] || d
}

function modeLabel(s: string) {
  const m: Record<string, string> = { DIRECT_LLM: '直接问答', BASIC_RAG: '基础检索', ENHANCED_RAG: '增强检索' }
  return m[s] || s
}

function resStatusLabel(s: string) {
  const m: Record<string, string> = { SUCCESS: '成功', REJECTED: '拒绝', FAILED: '失败', PENDING: '待执行', RUNNING: '执行中' }
  return m[s] || s
}
function resStatusTagType(s: string) {
  const m: Record<string, string> = { SUCCESS: 'success', REJECTED: 'warning', FAILED: 'danger', PENDING: 'info', RUNNING: 'warning' }
  return m[s] || 'info'
}

function runStatusLabel(s: string) {
  const m: Record<string, string> = { PENDING: '待执行', RUNNING: '执行中', COMPLETED: '已完成', FAILED: '失败' }
  return m[s] || s
}
function runStatusTagType(s: string) {
  const m: Record<string, string> = { PENDING: 'info', RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger' }
  return m[s] || 'info'
}

async function fetchQuestions() {
  loadingQuestions.value = true
  try {
    const params: any = {}
    if (filterDomain.value) params.domain = filterDomain.value
    const res = await request.get('/admin/evaluations/questions', { params })
    questions.value = res.data.data
  } finally { loadingQuestions.value = false }
}

async function fetchRuns() {
  loadingRuns.value = true
  try {
    const res = await request.get('/admin/evaluations/runs')
    runs.value = res.data.data
    // 刷新/首次进入页面时自动选中最新批次，否则进度/图表/CSV 无数据
    if (!currentRunId.value && runs.value.length > 0) {
      const latest = runs.value[0]
      currentRunId.value = latest.id
      runStatus.value = latest.status
      await refreshProgress()
    }
  } finally { loadingRuns.value = false }
}

async function fetchLogs() {
  loadingLogs.value = true
  try {
    const params: any = { page: logPage.value, size: logPageSize.value }
    if (filterTimeRange.value && filterTimeRange.value.length === 2) {
      params.startTime = filterTimeRange.value[0]
      params.endTime = filterTimeRange.value[1]
    }
    if (filterOperation.value) params.operation = filterOperation.value
    if (filterUsername.value) params.username = filterUsername.value
    const res = await request.get('/admin/logs', { params })
    const data = res.data.data
    logs.value = data.records
    logTotal.value = data.total
  } finally { loadingLogs.value = false }
}

function onLogPageChange(p: number) {
  logPage.value = p
  fetchLogs()
}

function resetLogs() {
  filterOperation.value = ''
  filterUsername.value = ''
  filterTimeRange.value = null
  logPage.value = 1
  fetchLogs()
}

// ---------------- 咨询运行记录 ----------------

/** 运行记录状态中文化（与评测结果的"拒绝"区分：任务书要求"正常拒绝"） */
function runtimeStatusLabel(s: string) {
  const m: Record<string, string> = { SUCCESS: '成功', REJECTED: '正常拒绝', FAILED: '执行失败' }
  return m[s] || s
}
function runtimeStatusTagType(s: string) {
  const m: Record<string, string> = { SUCCESS: 'success', REJECTED: 'warning', FAILED: 'danger' }
  return m[s] || 'info'
}

/** 执行路径中文化：增强检索 | 基础检索降级 | 未执行检索 */
function retrievalModeLabel(s: string) {
  const m: Record<string, string> = { ENHANCED_RAG: '增强检索', BASIC_FALLBACK: '基础检索降级', NO_RETRIEVAL: '未执行检索' }
  return m[s] || s
}

/** 错误码友好提示（与 mapError 短码一一对应，仅供参考展示） */
function runtimeErrorLabel(s: string) {
  const m: Record<string, string> = {
    HISTORY_SAVE_ERROR: '历史保存失败', QU_ERROR: '查询理解失败', EMBEDDING_ERROR: '向量化失败',
    QDRANT_ERROR: '向量检索失败', RETRIEVAL_DB_ERROR: '知识库查询失败', RATE_LIMIT: '模型服务限流',
    TIMEOUT: '调用超时', PROVIDER_ERROR: '模型服务错误', INTERNAL_ERROR: '内部错误',
  }
  return m[s] || s
}

async function fetchRuntimeRecords() {
  loadingRuntime.value = true
  try {
    const params: any = { page: runtimePage.value, size: runtimePageSize.value }
    if (runtimeTimeRange.value && runtimeTimeRange.value.length === 2) {
      params.startTime = runtimeTimeRange.value[0]
      params.endTime = runtimeTimeRange.value[1]
    }
    if (runtimeDomain.value) params.domain = runtimeDomain.value
    if (runtimeStatus.value) params.executionStatus = runtimeStatus.value
    if (runtimeUsername.value) params.username = runtimeUsername.value
    const res = await request.get('/admin/consult-runtime-records', { params })
    const data = res.data.data
    runtimeRecords.value = data.records
    runtimeTotal.value = data.total
  } finally { loadingRuntime.value = false }
}

function onRuntimePageChange(p: number) {
  runtimePage.value = p
  fetchRuntimeRecords()
}

function resetRuntime() {
  runtimeTimeRange.value = null
  runtimeDomain.value = ''
  runtimeStatus.value = ''
  runtimeUsername.value = ''
  runtimePage.value = 1
  fetchRuntimeRecords()
}

async function openRuntimeDetail(row: any) {
  const res = await request.get(`/admin/consult-runtime-records/${row.id}`)
  currentRuntimeDetail.value = res.data.data
  runtimeDetailVisible.value = true
}
</script>

<style scoped>
.evaluation-manage { min-height: 100vh; background: #f5f7fa; }
.evaluation-manage .page-title { margin-bottom: 4px; }
.page-sub {
  margin: -12px 0 16px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.filter-bar { margin-top: 10px; }
.chart-box { height: 280px; }
.exec-actions { display: flex; align-items: center; gap: 10px; }
.run-info { display: flex; align-items: center; flex-wrap: wrap; }
.detail-block { margin-top: 10px; }
.detail-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.detail-text { font-size: 13px; line-height: 1.6; }
.detail-pre {
  background: #f5f7fa; border-radius: 4px; padding: 10px;
  font-size: 12px; line-height: 1.6; white-space: pre-wrap; word-break: break-all;
  max-height: 200px; overflow-y: auto; margin: 0;
}
.answer-pre { max-height: 260px; }
.article-item { margin-bottom: 8px; }
.article-head { display: flex; align-items: center; flex-wrap: wrap; font-size: 13px; }
.article-content {
  margin-top: 4px; padding: 8px; background: #f5f7fa; border-radius: 4px;
  max-height: 120px; overflow-y: auto;
}
.fallback-reason { color: #e6a23c; font-size: 12px; }
.score-form { display: flex; flex-direction: column; gap: 10px; }
.score-item { display: flex; align-items: center; gap: 12px; }
.score-item > span { width: 90px; font-size: 13px; color: #606266; }
.score-item .score-hint { width: auto; font-size: 12px; color: #e6a23c; }
.score-item.comment { align-items: flex-start; }
</style>
