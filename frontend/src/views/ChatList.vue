<template>
  <div class="chat-list-page">
    <el-card>
      <template #header>
        <div class="header">
          <b><el-icon style="vertical-align: -2px"><ChatDotRound /></el-icon> 我的私聊</b>
        </div>
      </template>

      <div class="list-scroll">
        <el-empty v-if="!loading && list.length === 0" description="暂无私聊会话，去信息广场联系对方吧" />

        <div v-loading="loading" class="conv-list">
          <div v-for="c in list" :key="c.peerId" class="conv-item" @click="open(c)">
            <el-avatar :size="44" :src="imgUrl(c.peerAvatar)">{{ (c.peerName || '?').charAt(0) }}</el-avatar>
            <div class="conv-body">
              <div class="conv-top">
                <span class="conv-name">{{ c.peerName }}</span>
                <span class="conv-time">{{ fmtTime(c.lastTime) }}</span>
              </div>
              <div v-if="c.postTitle" class="conv-post">关于「{{ c.postTitle }}」</div>
              <div class="conv-bottom">
                <span class="conv-last">{{ c.lastContent }}</span>
                <el-badge v-if="c.unread > 0" :value="c.unread" :max="99" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { chatApi } from '../api'
import { imgUrl, fmtTime } from '../utils'

const router = useRouter()
const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = (await chatApi.conversations()) || []
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

function open(c) {
  router.push({
    path: '/chat/' + c.peerId,
    query: { name: c.peerName, avatar: c.peerAvatar || '', postId: c.postId, title: c.postTitle || '' }
  })
}

onMounted(load)
</script>

<style scoped>
.chat-list-page {
  height: 100%;
}
.chat-list-page :deep(.el-card) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.chat-list-page :deep(.el-card__body) {
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
.conv-list {
  display: flex;
  flex-direction: column;
  min-height: 80px;
}
.conv-item {
  display: flex;
  gap: 12px;
  padding: 14px 10px;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
  transition: background 0.2s;
  align-items: center;
}
.conv-item:hover {
  background: #f8fafc;
}
.conv-body {
  flex: 1;
  min-width: 0;
}
.conv-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.conv-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.conv-time {
  font-size: 12px;
  color: #c0c4cc;
}
.conv-post {
  margin-top: 2px;
  font-size: 12px;
  color: #188e8d;
}
.conv-bottom {
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.conv-last {
  font-size: 13px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 70%;
}
</style>
