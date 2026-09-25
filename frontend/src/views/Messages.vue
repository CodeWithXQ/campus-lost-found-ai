<template>
  <div class="messages-page">
    <el-card>
      <template #header>
        <div class="header">
          <b><el-icon style="vertical-align: -2px"><Bell /></el-icon> 消息通知</b>
          <div class="header-actions">
            <el-tag :type="sseConnected ? 'success' : 'info'" size="small">
              {{ sseConnected ? '实时推送已连接' : '轮询模式' }}
            </el-tag>
            <el-button size="small" @click="markAll">全部已读</el-button>
          </div>
        </div>
      </template>

      <div class="list-scroll">
        <el-empty v-if="!loading && list.length === 0" description="暂无消息" />

        <div v-loading="loading" class="notice-list">
          <div
            v-for="n in list"
            :key="n.id"
            class="notice-item"
            :class="{ unread: n.isRead === 0 }"
            @click="read(n)"
          >
            <div class="notice-icon">
              <el-icon :size="20" :color="iconColor(n.type)">
                <component :is="iconName(n.type)" />
              </el-icon>
            </div>
            <div class="notice-body">
              <div class="notice-title">
                <b>{{ n.title }}</b>
                <el-tag v-if="n.isRead === 0" size="small" type="danger">未读</el-tag>
              </div>
              <div class="notice-content">{{ n.content }}</div>
              <div class="notice-time">{{ fmtTime(n.createTime) }}</div>
            </div>
          </div>
        </div>
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
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { notifyApi } from '../api'
import { fmtTime } from '../utils'

const router = useRouter()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const sseConnected = ref(false)

let es = null
let timer = null

async function load() {
  loading.value = true
  try {
    const data = await notifyApi.list({ page: page.value, size: size.value, type: 'CLAIM,CONTACT,SUBSCRIBE,AUDIT' })
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

function iconName(type) {
  const map = { MATCH: 'MagicStick', CLAIM: 'ChatDotRound', CONTACT: 'Message', SYSTEM: 'Bell', SUBSCRIBE: 'Bell', AUDIT: 'DocumentChecked' }
  return map[type] || 'Bell'
}

function iconColor(type) {
  const map = { MATCH: '#409EFF', CLAIM: '#E6A23C', CONTACT: '#67C23A', SYSTEM: '#909399', SUBSCRIBE: '#8E44AD', AUDIT: '#67C23A' }
  return map[type] || '#909399'
}

async function read(n) {
  if (n.isRead === 0) {
    await notifyApi.read(n.id)
    n.isRead = 1
    // 通知顶部导航红点即时同步刷新
    window.dispatchEvent(new Event('unread-changed'))
  }
  if (n.type === 'CLAIM' && n.relatedId) {
    router.push('/claim/' + n.relatedId)
  } else if (n.type === 'CONTACT' && n.relatedId) {
    router.push({ path: '/post/' + n.relatedId, query: n.extraId ? { myPostId: n.extraId } : {} })
  } else if (n.type === 'SUBSCRIBE' && n.relatedId) {
    router.push('/post/' + n.relatedId)
  }
}

function markAll() {
  notifyApi.readAll().then(() => {
    ElMessage.success('已全部标记为已读')
    load()
    window.dispatchEvent(new Event('unread-changed'))
  })
}

function connectSse() {
  const token = localStorage.getItem('token')
  if (!token) return
  try {
    es = new EventSource(`/api/notification/stream?token=${encodeURIComponent(token)}`)
    es.addEventListener('notification', () => {
      load()
    })
    es.onopen = () => {
      sseConnected.value = true
    }
    es.onerror = () => {
      sseConnected.value = false
      // EventSource 自动重连
    }
  } catch (e) {
    /* 降级为轮询 */
  }
}

onMounted(() => {
  load()
  connectSse()
  // 轮询兜底（SSE 断开时仍能收到消息）
  timer = setInterval(load, 15000)
})

onUnmounted(() => {
  if (es) es.close()
  clearInterval(timer)
})
</script>

<style scoped>
.messages-page {
  height: 100%;
}
.messages-page :deep(.el-card) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.messages-page :deep(.el-card__body) {
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
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.list-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.notice-list {
  display: flex;
  flex-direction: column;
  min-height: 80px;
}
.notice-item {
  display: flex;
  gap: 12px;
  padding: 14px 10px;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
  transition: background 0.2s;
}
.notice-item:hover {
  background: #f8fafc;
}
.notice-item.unread {
  background: #f0f7ff;
}
.notice-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.notice-body {
  flex: 1;
}
.notice-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #303133;
}
.notice-content {
  margin-top: 4px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.notice-time {
  margin-top: 4px;
  font-size: 12px;
  color: #c0c4cc;
}
.pager {
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  padding: 14px 0 4px;
}
</style>
