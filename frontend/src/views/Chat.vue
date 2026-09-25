<template>
  <div class="chat-page">
    <!-- 顶部：对方信息 -->
    <div class="chat-header">
      <el-button text circle @click="$router.push('/chat')"><el-icon><Back /></el-icon></el-button>
      <el-avatar :size="38" :src="imgUrl(peerAvatar)">{{ peerName.charAt(0) }}</el-avatar>
      <div class="peer-info">
        <div class="peer-name">{{ peerName }}</div>
        <div v-if="postTitle" class="peer-sub">正在沟通关于「{{ postTitle }}」</div>
      </div>
    </div>

    <!-- 安全提示 -->
    <el-alert
      class="safety-tip"
      type="warning"
      show-icon
      :closable="false"
      title="注意对方身份真实性"
      description="请核实对方身份，谨防诈骗。切勿透露密码、验证码、银行卡等敏感信息，建议在校园公共场所当面确认物品。"
    />

    <!-- 消息区 -->
    <div ref="msgBox" class="msg-box" v-loading="loading">
      <el-empty v-if="!loading && messages.length === 0" description="暂无消息，发送第一条消息开始沟通吧" />
      <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: isMine(m) }">
        <div class="bubble">
          <div class="bubble-text">{{ m.content }}</div>
          <div class="bubble-time">{{ fmtTime(m.createTime) }}</div>
        </div>
      </div>
    </div>

    <!-- 快捷短语 -->
    <div class="quick-bar">
      <span class="quick-label">快捷语：</span>
      <el-tag
        v-for="(p, i) in quickPhrases"
        :key="i"
        class="quick-tag"
        effect="plain"
        @click="usePhrase(p)"
      >{{ p }}</el-tag>
    </div>

    <!-- 输入区 -->
    <div class="input-bar">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        resize="none"
        maxlength="500"
        show-word-limit
        placeholder="请输入消息，Enter 发送，Shift+Enter 换行"
        @keydown.enter.exact.prevent="send"
      />
      <el-button type="primary" class="send-btn" :loading="sending" @click="send">发 送</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { Back } from '@element-plus/icons-vue'
import { chatApi } from '../api'
import { useUserStore } from '../store/user'
import { imgUrl, fmtTime } from '../utils'

const route = useRoute()
const userStore = useUserStore()

const peerId = computed(() => Number(route.params.peerId))
const peerName = computed(() => route.query.name || '对方')
const peerAvatar = computed(() => route.query.avatar || '')
const postId = computed(() => (route.query.postId ? Number(route.query.postId) : null))
const postTitle = computed(() => route.query.title || '')

const messages = ref([])
const input = ref('')
const loading = ref(false)
const sending = ref(false)
const msgBox = ref()

const quickPhrases = [
  '你好，我在平台上看到你的物品信息',
  '请问这个物品有什么明显特征吗？',
  '方便描述一下物品的外观和颜色吗？',
  '我这边可以核对细节，麻烦说下物品特征',
  '请问在哪里方便当面确认物品？'
]

let es = null
let timer = null

function isMine(m) {
  return m.senderId === userStore.user?.id
}

async function load() {
  loading.value = true
  try {
    messages.value = (await chatApi.history(peerId.value, postId.value)) || []
    scrollToBottom()
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

async function send() {
  const content = input.value.trim()
  if (!content) return
  sending.value = true
  try {
    const msg = await chatApi.send({ receiverId: peerId.value, content, postId: postId.value })
    messages.value.push(msg)
    input.value = ''
    scrollToBottom()
  } catch (e) {
    /* ignore */
  } finally {
    sending.value = false
  }
}

function usePhrase(p) {
  input.value = p
}

function scrollToBottom() {
  nextTick(() => {
    if (msgBox.value) {
      msgBox.value.scrollTop = msgBox.value.scrollHeight
    }
  })
}

function connectSse() {
  const token = localStorage.getItem('token')
  if (!token) return
  try {
    es = new EventSource(`/api/notification/stream?token=${encodeURIComponent(token)}`)
    es.addEventListener('chat', (e) => {
      try {
        const msg = JSON.parse(e.data)
        const samePost = postId.value == null || msg.postId === postId.value
        if (msg.senderId === peerId.value && samePost) {
          messages.value.push(msg)
          chatApi.markRead(peerId.value, postId.value)
          scrollToBottom()
        }
      } catch (err) {
        /* ignore */
      }
    })
  } catch (e) {
    /* 降级为轮询 */
  }
}

onMounted(async () => {
  await load()
  chatApi.markRead(peerId.value, postId.value)
  connectSse()
  timer = setInterval(load, 15000)
})

onUnmounted(() => {
  if (es) es.close()
  clearInterval(timer)
})
</script>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  border-radius: 8px;
  overflow: hidden;
}
.chat-header {
  height: 60px;
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
}
.peer-info {
  min-width: 0;
}
.peer-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.peer-sub {
  font-size: 12px;
  color: #909399;
}
.safety-tip {
  flex-shrink: 0;
  margin: 10px 12px 0;
}
.msg-box {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.msg-row {
  display: flex;
}
.msg-row.mine {
  justify-content: flex-end;
}
.bubble {
  max-width: 62%;
  padding: 10px 14px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #ebeef5;
}
.msg-row.mine .bubble {
  background: #a0d8d0;
  border-color: #a0d8d0;
  color: #303133;
}
.bubble-text {
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}
.bubble-time {
  margin-top: 4px;
  font-size: 11px;
  color: #c0c4cc;
  text-align: right;
}
.quick-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 16px;
  background: #fff;
  border-top: 1px solid #ebeef5;
}
.quick-label {
  font-size: 12px;
  color: #909399;
}
.quick-tag {
  cursor: pointer;
}
.input-bar {
  flex-shrink: 0;
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px 16px;
  background: #fff;
}
.input-bar .el-textarea {
  flex: 1;
}
.send-btn {
  height: 40px;
}
</style>
