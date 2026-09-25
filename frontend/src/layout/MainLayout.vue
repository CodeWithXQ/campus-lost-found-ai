<template>
  <div class="layout">
    <header class="header">
      <div class="logo" @click="$router.push('/home')">
        <el-icon :size="26" color="#409EFF"><Search /></el-icon>
        <span class="logo-text">校园失物招领<span class="ai-badge">AI 智能匹配</span></span>
      </div>

      <nav class="nav">
        <router-link to="/home" class="nav-item">信息广场</router-link>
        <router-link v-if="userStore.isAdmin" to="/admin/posts" class="nav-item message-link">
          帖子管理
          <el-badge v-if="pendingCount > 0" :value="pendingCount" :max="99" class="badge" />
        </router-link>
        <router-link v-if="userStore.isAdmin" to="/admin/users" class="nav-item">用户管理</router-link>
        <router-link v-if="userStore.isAdmin" to="/dashboard" class="nav-item">数据看板</router-link>
        <router-link v-if="userStore.isLogin" to="/publish" class="nav-item">发布信息</router-link>
        <router-link v-if="userStore.isLogin" to="/myposts" class="nav-item">我的发布</router-link>
        <router-link v-if="userStore.isLogin" to="/claims" class="nav-item">认领管理</router-link>
        <router-link v-if="userStore.isLogin" to="/messages" class="nav-item message-link">
          消息
          <el-badge v-if="unread > 0" :value="unread" :max="99" class="badge" />
        </router-link>
        <router-link v-if="userStore.isLogin" to="/chat" class="nav-item message-link">
          私聊
          <el-badge v-if="chatUnread > 0" :value="chatUnread" :max="99" class="badge" />
        </router-link>
      </nav>

      <div class="user-area">
        <template v-if="userStore.isLogin">
          <el-dropdown @command="onCommand">
            <span class="user-name">
              <el-avatar :size="30" :src="avatar">{{ userStore.user?.nickname?.charAt(0) }}</el-avatar>
              <span class="nick">{{ userStore.user?.nickname }}</span>
              <el-tag v-if="userStore.isAdmin" size="small" type="danger">管理员</el-tag>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-tag size="small" type="info" effect="plain">游客浏览</el-tag>
          <el-button type="primary" size="small" @click="$router.push('/login')">登录</el-button>
          <el-button size="small" @click="$router.push('/register')">注册</el-button>
        </template>
      </div>
    </header>

    <main class="main" :class="{ 'main-full': isDashboard }">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'
import { notifyApi, chatApi, adminApi } from '../api'
import { imgUrl } from '../utils'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const unread = ref(0)
const chatUnread = ref(0)
const pendingCount = ref(0)

const avatar = computed(() => imgUrl(userStore.user?.avatar))
const isDashboard = computed(() => route.path === '/dashboard')

let timer = null

async function loadUnread() {
  if (!userStore.isLogin) return
  try {
    // 与消息列表页口径一致：认领 + 一键联系 + 订阅提醒 + 审核通知
    unread.value = await notifyApi.unreadCount({ type: 'CLAIM,CONTACT,SUBSCRIBE,AUDIT' })
  } catch (e) {
    /* ignore */
  }
}

async function loadChatUnread() {
  if (!userStore.isLogin) return
  try {
    chatUnread.value = await chatApi.unreadCount()
  } catch (e) {
    /* ignore */
  }
}

async function loadPendingCount() {
  if (!userStore.isAdmin) return
  try {
    pendingCount.value = await adminApi.pendingCount()
  } catch (e) {
    /* ignore */
  }
}

function onCommand(cmd) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
      .then(() => {
        userStore.logout()
        router.push('/login')
      })
      .catch(() => {})
  }
}

onMounted(() => {
  loadUnread()
  loadChatUnread()
  loadPendingCount()
  // 监听「已读变化」事件，消息页点击标记已读后即时刷新红点，无需等轮询
  window.addEventListener('unread-changed', loadUnread)
  // 监听「待审核数量变化」事件，管理员通过/拒绝后即时刷新角标
  window.addEventListener('pending-changed', loadPendingCount)
  timer = setInterval(() => {
    loadUnread()
    loadChatUnread()
    loadPendingCount()
  }, 20000)
})

onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('unread-changed', loadUnread)
  window.removeEventListener('pending-changed', loadPendingCount)
})
</script>

<style scoped>
.layout {
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.header {
  height: 60px;
  flex-shrink: 0;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  padding: 0 24px;
  z-index: 100;
}
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  margin-right: 32px;
}
.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}
.ai-badge {
  font-size: 11px;
  color: #409eff;
  border: 1px solid #409eff;
  border-radius: 8px;
  padding: 1px 6px;
  margin-left: 6px;
  vertical-align: middle;
  font-weight: 400;
}
.nav {
  flex: 1;
  display: flex;
  gap: 4px;
  align-items: center;
}
.nav-item {
  padding: 8px 14px;
  border-radius: 6px;
  color: #606266;
  text-decoration: none;
  font-size: 14px;
  transition: all 0.2s;
}
.nav-item:hover {
  color: #409eff;
  background: #ecf5ff;
}
.nav-item.router-link-active {
  color: #409eff;
  background: #ecf5ff;
  font-weight: 600;
}
.message-link {
  position: relative;
}
.badge {
  margin-left: 4px;
}
.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-name {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #303133;
  outline: none;
}
.nick {
  font-size: 14px;
}
.main {
  flex: 1;
  min-height: 0;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.main-full {
  max-width: none;
}
</style>

<style>
/* 让路由页面根节点占满内容区剩余高度（各页面根容器需为 flex column 布局） */
.main > * {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
/* 卡片内空状态垂直居中，且随卡片占满高度 */
.el-card__body .el-empty {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
</style>
