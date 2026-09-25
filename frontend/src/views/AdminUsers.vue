<template>
  <div class="admin-users">
    <el-card>
      <template #header>
        <div class="header">
          <b><el-icon style="vertical-align: -2px"><User /></el-icon> 用户管理</b>
          <el-tag type="danger" size="small">管理员专属</el-tag>
        </div>
      </template>

      <div class="list-scroll">
        <el-table v-loading="loading" :data="list" stripe style="width: 100%">
          <el-table-column label="用户名" width="140">
            <template #default="{ row }">
              <span class="username">{{ row.username }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="nickname" label="昵称" width="140" />
          <el-table-column label="角色" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.role === 'ADMIN' ? 'danger' : 'info'">
                {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" width="140">
            <template #default="{ row }">{{ row.phone || '—' }}</template>
          </el-table-column>
          <el-table-column prop="email" label="邮箱" min-width="160">
            <template #default="{ row }">{{ row.email || '—' }}</template>
          </el-table-column>
          <el-table-column label="发布数" width="90" align="center">
            <template #default="{ row }">{{ row.postCount }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 0 ? 'success' : 'info'">
                {{ row.status === 0 ? '正常' : '已注销' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="注册时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="row.role !== 'ADMIN'">
                <el-button v-if="row.status === 0" size="small" type="warning" link @click="disable(row)">注销</el-button>
                <el-button v-else size="small" type="success" link @click="enable(row)">恢复</el-button>
                <el-button size="small" type="danger" link @click="remove(row)">删除</el-button>
              </template>
              <el-tag v-else size="small" type="info">管理员</el-tag>
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api'
import { fmtTime } from '../utils'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await adminApi.users({ page: page.value, size: size.value })
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

function disable(row) {
  ElMessageBox.confirm(`确定注销用户「${row.username}」吗？注销后该账号将无法登录。`, '注销用户', { type: 'warning' })
    .then(async () => {
      await adminApi.disableUser(row.id)
      ElMessage.success('已注销该用户')
      load()
    })
    .catch(() => {})
}

function enable(row) {
  ElMessageBox.confirm(`确定恢复用户「${row.username}」吗？恢复后可正常登录。`, '恢复用户', { type: 'warning' })
    .then(async () => {
      await adminApi.enableUser(row.id)
      ElMessage.success('已恢复该用户')
      load()
    })
    .catch(() => {})
}

function remove(row) {
  ElMessageBox.confirm(
    `确定删除用户「${row.username}」吗？将同时清理其发布的帖子、匹配与认领记录，不可恢复。`,
    '删除用户',
    { type: 'error' }
  )
    .then(async () => {
      await adminApi.deleteUser(row.id)
      ElMessage.success('已删除该用户')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.admin-users {
  height: 100%;
}
.admin-users :deep(.el-card) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.admin-users :deep(.el-card__body) {
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
.username {
  font-weight: 600;
  color: #303133;
}
</style>
