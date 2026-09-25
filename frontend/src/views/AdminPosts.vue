<template>
  <div class="admin-posts">
    <el-card>
      <template #header>
        <div class="header">
          <b><el-icon style="vertical-align: -2px"><Files /></el-icon> 帖子管理</b>
          <el-tag type="danger" size="small">管理员专属</el-tag>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="admin-tabs">
        <!-- ========== 待审核 Tab ========== -->
        <el-tab-pane label="待审核" name="pending">
          <div class="filter-row">
            <el-radio-group v-model="pendingQuery.type" @change="loadPending(1)">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="LOST">失物</el-radio-button>
              <el-radio-button value="FOUND">招领</el-radio-button>
            </el-radio-group>
            <el-input v-model="pendingQuery.keyword" placeholder="搜索物品名称" clearable style="width: 200px" @keyup.enter="loadPending(1)">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" @click="loadPending(1)">搜索</el-button>
          </div>

          <div class="list-scroll">
            <el-empty v-if="!pendingLoading && pendingList.length === 0" description="暂无待审核帖子" />
            <el-table v-loading="pendingLoading" :data="pendingList" stripe style="width: 100%">
              <el-table-column label="类型" width="70">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.type === 'LOST' ? 'warning' : 'success'">
                    {{ row.type === 'LOST' ? '失物' : '招领' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="title" label="物品" min-width="130" show-overflow-tooltip />
              <el-table-column prop="category" label="类别" width="100" />
              <el-table-column prop="location" label="地点" min-width="100" show-overflow-tooltip />
              <el-table-column prop="ownerName" label="发布者" width="90" />
              <el-table-column label="AI 初审核" width="120">
                <template #default="{ row }">
                  <el-tooltip v-if="row.aiAuditResult" :content="row.aiAuditNote || ''" placement="top">
                    <el-tag size="small" :type="aiAuditType(row.aiAuditResult)" effect="plain">
                      {{ aiAuditText(row.aiAuditResult) }}
                    </el-tag>
                  </el-tooltip>
                  <span v-else class="muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="提交时间" width="170">
                <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" link @click="$router.push('/post/' + row.id)">详情</el-button>
                  <el-button size="small" type="success" link @click="approve(row)">通过</el-button>
                  <el-button size="small" type="danger" link @click="reject(row)">拒绝</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="pager">
            <el-pagination
              v-model:current-page="pendingQuery.page"
              :page-size="pendingQuery.size"
              :total="pendingTotal"
              layout="total, prev, pager, next"
              @current-change="loadPending()"
            />
          </div>
        </el-tab-pane>

        <!-- ========== 帖子管理 Tab ========== -->
        <el-tab-pane label="帖子管理" name="posts">
          <div class="filter-row">
            <el-radio-group v-model="query.type" @change="load(1)">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="LOST">失物</el-radio-button>
              <el-radio-button value="FOUND">招领</el-radio-button>
            </el-radio-group>
            <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="load(1)">
              <el-option label="寻找中" :value="0" />
              <el-option label="已认领归档" :value="1" />
              <el-option label="已下架" :value="2" />
              <el-option label="待审核" :value="3" />
              <el-option label="审核未通过" :value="4" />
            </el-select>
            <el-input v-model="query.keyword" placeholder="搜索物品名称" clearable style="width: 200px" @keyup.enter="load(1)">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" @click="load(1)">搜索</el-button>
          </div>

          <div class="list-scroll">
            <el-table v-loading="loading" :data="list" stripe style="width: 100%">
              <el-table-column label="类型" width="80">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.type === 'LOST' ? 'warning' : 'success'">
                    {{ row.type === 'LOST' ? '失物' : '招领' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="title" label="物品" min-width="140" show-overflow-tooltip />
              <el-table-column prop="category" label="类别" width="110" />
              <el-table-column prop="location" label="地点" min-width="110" show-overflow-tooltip />
              <el-table-column prop="ownerName" label="发布者" width="100" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 0 ? 'success' : (row.status === 1 ? 'warning' : (row.status === 3 ? 'warning' : (row.status === 4 ? 'danger' : 'info')))">{{ statusText(row.status, row.type) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="发布时间" width="170">
                <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" link @click="$router.push('/post/' + row.id)">详情</el-button>
                  <el-button v-if="row.status === 0" size="small" type="warning" link @click="archive(row)">下架</el-button>
                  <el-button v-if="row.status === 2" size="small" type="success" link @click="relist(row)">重新上架</el-button>
                  <el-button size="small" type="danger" link @click="remove(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="pager">
            <el-pagination
              v-model:current-page="query.page"
              :page-size="query.size"
              :total="total"
              layout="total, prev, pager, next"
              @current-change="load()"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api'
import { fmtTime, statusText, aiAuditText, aiAuditType } from '../utils'

const activeTab = ref('pending')

// ===== 待审核队列 =====
const pendingList = ref([])
const pendingTotal = ref(0)
const pendingLoading = ref(false)
const pendingQuery = reactive({ type: '', keyword: '', page: 1, size: 10 })

// ===== 帖子管理 =====
const list = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ type: '', status: 0, keyword: '', page: 1, size: 10 })

async function loadPending() {
  pendingLoading.value = true
  try {
    const data = await adminApi.pendingPosts({
      type: pendingQuery.type || undefined,
      keyword: pendingQuery.keyword || undefined,
      page: pendingQuery.page,
      size: pendingQuery.size
    })
    pendingList.value = data.records || []
    pendingTotal.value = data.total || 0
  } catch (e) {
    /* ignore */
  } finally {
    pendingLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await adminApi.posts({
      type: query.type || undefined,
      status: query.status ?? undefined,
      keyword: query.keyword || undefined,
      page: query.page,
      size: query.size
    })
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

function approve(row) {
  ElMessageBox.confirm(`确定通过「${row.title}」的审核吗？通过后将上线信息广场并自动匹配。`, '审核通过', { type: 'success' })
    .then(async () => {
      await adminApi.approvePost(row.id)
      ElMessage.success('已通过')
      loadPending()
      window.dispatchEvent(new Event('pending-changed'))
    })
    .catch(() => {})
}

function reject(row) {
  ElMessageBox.prompt(`请输入「${row.title}」未通过审核的原因（可空）`, '审核拒绝', {
    type: 'warning',
    inputPlaceholder: '如：信息不实 / 图片模糊 / 涉嫌广告',
    confirmButtonText: '拒绝',
    cancelButtonText: '取消'
  })
    .then(async ({ value }) => {
      await adminApi.rejectPost(row.id, value || '')
      ElMessage.success('已拒绝')
      loadPending()
      window.dispatchEvent(new Event('pending-changed'))
    })
    .catch(() => {})
}

function archive(row) {
  ElMessageBox.confirm(`确定下架「${row.title}」吗？下架后不再在广场展示。`, '下架帖子', { type: 'warning' })
    .then(async () => {
      await adminApi.archivePost(row.id)
      ElMessage.success('已下架')
      load()
    })
    .catch(() => {})
}

function relist(row) {
  ElMessageBox.confirm(`确定将「${row.title}」重新上架到信息广场吗？上架后将重新参与智能匹配。`, '重新上架', { type: 'info' })
    .then(async () => {
      await adminApi.relistPost(row.id)
      ElMessage.success('已重新上架')
      load()
    })
    .catch(() => {})
}

function remove(row) {
  ElMessageBox.confirm(`确定删除「${row.title}」吗？将同时清理其关联的匹配与认领记录，不可恢复。`, '删除帖子', { type: 'error' })
    .then(async () => {
      await adminApi.deletePost(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

watch(activeTab, (v) => {
  if (v === 'pending') loadPending()
  else load()
})

onMounted(loadPending)
</script>

<style scoped>
.admin-posts {
  height: 100%;
}
.admin-posts :deep(.el-card) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.admin-posts :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.admin-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.admin-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
}
.admin-tabs :deep(.el-tab-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.filter-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
  flex-shrink: 0;
  padding-bottom: 12px;
}
.list-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.list-scroll :deep(.el-table__header-wrapper) {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #fff;
}
.pager {
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  padding: 14px 0 4px;
}
.muted {
  color: #c0c4cc;
}
</style>
