<template>
  <div class="claims-page">
    <el-card>
      <template #header>
        <div class="header">
          <b><el-icon style="vertical-align: -2px"><DocumentChecked /></el-icon> 认领管理</b>
          <el-tag type="info" size="small">失主申请认领 → 拾主确认 → 双方信息自动下架</el-tag>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="claims-tabs" @tab-change="onTabChange">
        <el-tab-pane label="待我处理" name="handle" />
        <el-tab-pane label="我申请的" name="applied" />
        <el-tab-pane v-if="userStore.isAdmin" label="全部认领记录" name="all" />
      </el-tabs>

      <!-- 管理员查看全系统记录时的状态筛选 -->
      <div v-if="activeTab === 'all'" class="filter-row">
        <el-select v-model="allStatus" placeholder="全部状态" clearable style="width: 160px" @change="onStatusChange">
          <el-option label="待确认" value="PENDING" />
          <el-option label="已确认" value="CONFIRMED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
      </div>

      <div class="list-scroll">
        <el-empty v-if="!loading && list.length === 0" :description="emptyText" />

        <el-table v-loading="loading" :data="list" stripe style="width: 100%">
          <el-table-column label="失物信息" min-width="180">
            <template #default="{ row }">
              <el-link type="primary" @click="$router.push('/claim/' + row.id)">{{ row.postTitle }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="申请人" width="110">
            <template #default="{ row }">{{ row.claimantName }}</template>
          </el-table-column>
          <el-table-column v-if="activeTab !== 'handle'" label="拾主" width="110">
            <template #default="{ row }">{{ row.finderName || '—' }}</template>
          </el-table-column>
          <el-table-column label="认领说明" min-width="180">
            <template #default="{ row }">{{ row.message || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ claimStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column v-if="activeTab === 'handle'" label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING'">
                <el-button size="small" type="success" @click="confirm(row)">确认认领</el-button>
                <el-button size="small" type="danger" plain @click="reject(row)">拒绝</el-button>
              </template>
              <el-tag v-else size="small" type="info">已处理</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { claimApi, adminApi } from '../api'
import { useUserStore } from '../store/user'
import { fmtTime, claimStatusText } from '../utils'

const userStore = useUserStore()
const activeTab = ref('handle')
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const allStatus = ref('')

const emptyText = computed(() => {
  if (activeTab.value === 'handle') return '暂无需你处理的认领申请'
  if (activeTab.value === 'applied') return '你还没有发起过认领申请'
  return '暂无认领记录'
})

async function load() {
  loading.value = true
  try {
    let data
    if (activeTab.value === 'all') {
      data = await adminApi.claims({ page: page.value, size: size.value, status: allStatus.value || undefined })
    } else {
      data = await claimApi.my({ page: page.value, size: size.value, scope: activeTab.value })
    }
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  page.value = 1
  allStatus.value = ''
  load()
}

function onStatusChange() {
  page.value = 1
  load()
}

function statusType(s) {
  const map = { PENDING: 'warning', CONFIRMED: 'success', REJECTED: 'danger' }
  return map[s] || 'info'
}

function confirm(row) {
  ElMessageBox.confirm(`确认「${row.claimantName}」认领《${row.postTitle}》吗？确认后双方信息将自动下架。`, '确认认领', {
    type: 'warning'
  })
    .then(async () => {
      await claimApi.confirm(row.id)
      ElMessage.success('认领成功，相关帖子已自动下架')
      load()
    })
    .catch(() => {})
}

function reject(row) {
  ElMessageBox.confirm('确定拒绝该认领申请吗？', '拒绝认领', { type: 'warning' })
    .then(async () => {
      await claimApi.reject(row.id)
      ElMessage.success('已拒绝该认领申请')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.claims-page {
  height: 100%;
}
.claims-page :deep(.el-card) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.claims-page :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.claims-tabs {
  flex-shrink: 0;
}
.filter-row {
  flex-shrink: 0;
  padding: 0 0 12px;
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
.strong {
  font-weight: 600;
}
</style>
