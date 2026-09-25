<template>
  <div class="home">
    <!-- 搜索区 -->
    <div class="search-card">
      <div class="search-row">
        <el-radio-group v-model="query.type" @change="load(1)">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="LOST">失物</el-radio-button>
          <el-radio-button value="FOUND">招领</el-radio-button>
        </el-radio-group>
        <el-input v-model="query.keyword" placeholder="搜索物品名称（如：书包、钱包）" clearable class="w-220" @keyup.enter="load(1)">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.category" placeholder="物品类别" clearable class="w-160">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-input v-model="query.location" placeholder="地点（如：图书馆）" clearable class="w-160" />
        <el-button type="primary" @click="load(1)">搜索</el-button>
        <el-button type="warning" plain @click="onSemanticSearch">语义搜索</el-button>
        <el-button type="success" @click="onPublish">＋ 发布信息</el-button>
        <el-button type="primary" plain class="subscribe-btn" @click="openSubscribe">
          <el-icon><Bell /></el-icon>
          订阅提醒
        </el-button>
      </div>
    </div>

    <!-- 列表 -->
    <div class="list-area" v-loading="loading">
      <el-empty v-if="!loading && posts.length === 0" description="暂无相关信息，去发布一条吧～" />
      <div class="post-grid">
        <div v-for="post in posts" :key="post.id" class="post-card" @click="$router.push('/post/' + post.id)">
          <div class="post-img">
            <el-image v-if="post.imageUrls" :src="imgUrl(post.imageUrls.split(',')[0].trim())" fit="cover" lazy />
            <div v-else class="no-img">
              <el-icon :size="34"><Picture /></el-icon>
            </div>
            <el-tag :type="post.type === 'LOST' ? 'warning' : 'success'" class="type-tag" effect="dark">
              {{ post.type === 'LOST' ? '失物' : '招领' }}
            </el-tag>
            <el-tag v-if="isImportant(post.category)" type="danger" class="important-tag" effect="dark">
              重要
            </el-tag>
          </div>
          <div class="post-body">
            <div class="post-title">{{ post.title }}</div>
            <div class="post-meta">
              <el-tag size="small" type="info" effect="plain">{{ post.category }}</el-tag>
              <span class="meta-item"><el-icon><Location /></el-icon>{{ post.location }}</span>
            </div>
            <div class="post-time">{{ fmtTime(post.lostTime) }}</div>
            <div class="post-owner">
              <el-avatar :size="20">{{ (post.ownerName || '?').charAt(0) }}</el-avatar>
              <span>{{ post.ownerName }}</span>
            </div>
          </div>
        </div>
      </div>
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

    <!-- 订阅提醒弹窗（主动触达） -->
    <el-dialog v-model="subDialog" title="订阅提醒（主动触达）" width="560px">
      <div class="sub-tip">订阅后，当有匹配的新信息发布时，系统会主动通知你，无需反复刷新查看。</div>

      <div class="sub-form">
        <div class="sub-form-row">
          <span class="sub-label">类型</span>
          <el-radio-group v-model="subForm.type">
            <el-radio-button value="LOST">失物</el-radio-button>
            <el-radio-button value="FOUND">招领</el-radio-button>
          </el-radio-group>
        </div>
        <div class="sub-form-row">
          <span class="sub-label">类别</span>
          <el-select v-model="subForm.category" placeholder="全部类别" clearable style="width: 160px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </div>
        <div class="sub-form-row">
          <span class="sub-label">地点</span>
          <el-input v-model="subForm.location" placeholder="地点关键词（可空）" clearable style="width: 160px" />
        </div>
        <div class="sub-form-row">
          <el-button type="primary" @click="addSubscribe">添加订阅</el-button>
        </div>
      </div>

      <el-divider content-position="left">我的订阅</el-divider>
      <el-empty v-if="!subList.length" description="暂无订阅，添加后即可收到主动提醒" :image-size="60" />
      <div v-else class="sub-list">
        <div v-for="s in subList" :key="s.id" class="sub-item">
          <div class="sub-info">
            <el-tag size="small" :type="s.type === 'LOST' ? 'warning' : 'success'">
              {{ s.type === 'LOST' ? '失物' : '招领' }}
            </el-tag>
            <span>{{ s.category || '全部类别' }}</span>
            <span v-if="s.location" class="sub-loc">· {{ s.location }}</span>
          </div>
          <el-button size="small" type="danger" text @click="removeSubscribe(s)">删除</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { postApi, subscriptionApi } from '../api'
import { useUserStore } from '../store/user'
import { imgUrl, fmtTime } from '../utils'

const router = useRouter()
const userStore = useUserStore()
const posts = ref([])
const total = ref(0)
const loading = ref(false)
const categories = ref([])

const query = reactive({
  type: '',
  keyword: '',
  category: '',
  location: '',
  page: 1,
  size: 12
})

/** 高价值刚需物品（补办麻烦/价值高），信息广场加「重要」角标 */
const IMPORTANT_CATEGORIES = ['手机', '钱包/包', '证件/卡', '电脑/电子设备']
function isImportant(category) {
  return IMPORTANT_CATEGORIES.includes(category)
}

// ===== 订阅提醒（主动触达）=====
const subDialog = ref(false)
const subList = ref([])
const subForm = reactive({ type: 'LOST', category: '', location: '' })

function openSubscribe() {
  if (!userStore.isLogin) {
    ElMessage.info('请先登录后再使用订阅提醒')
    router.push('/login?redirect=/home')
    return
  }
  subDialog.value = true
  loadSubscriptions()
}

async function loadSubscriptions() {
  try {
    subList.value = (await subscriptionApi.my()) || []
  } catch (e) {
    /* ignore */
  }
}

async function addSubscribe() {
  if (!subForm.type) return
  try {
    await subscriptionApi.add({ ...subForm })
    ElMessage.success('订阅成功，有新信息时将主动通知你')
    subForm.category = ''
    subForm.location = ''
    loadSubscriptions()
  } catch (e) {
    /* ignore */
  }
}

async function removeSubscribe(s) {
  try {
    await subscriptionApi.remove(s.id)
    ElMessage.success('已删除订阅')
    loadSubscriptions()
  } catch (e) {
    /* ignore */
  }
}

function onPublish() {
  if (userStore.isLogin) {
    router.push('/publish')
  } else {
    ElMessage.info('请先登录后再发布信息')
    router.push('/login?redirect=/publish')
  }
}

async function load() {
  loading.value = true
  try {
    const data = await postApi.list({
      type: query.type || undefined,
      keyword: query.keyword || undefined,
      category: query.category || undefined,
      location: query.location || undefined,
      page: query.page,
      size: query.size
    })
    posts.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

async function onSemanticSearch() {
  const text = query.keyword
  if (!text) {
    ElMessage.info('请先在搜索框输入物品描述（如：黑色双肩包），再点击语义搜索')
    return
  }
  loading.value = true
  try {
    const data = await postApi.search(text)
    if (!data || data.length === 0) {
      ElMessage.info('未找到语义相似的物品（可能 AI 服务未启动）')
      posts.value = []
      total.value = 0
    } else {
      posts.value = data
      total.value = data.length
      ElMessage.success(`找到 ${data.length} 条语义相似的信息`)
    }
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  load()
  try {
    categories.value = await postApi.categories()
  } catch (e) {
    /* ignore */
  }
})
</script>

<style scoped>
.list-area {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.list-area :deep(.el-empty) {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.search-card {
  background: #fff;
  border-radius: 10px;
  padding: 18px 20px;
  margin-bottom: 18px;
  box-shadow: 0 2px 8px rgba(0, 21, 41, 0.06);
}
.search-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}
.w-220 {
  width: 220px;
}
.w-160 {
  width: 160px;
}
.subscribe-btn {
  background-color: #ecf5ff;
}
.subscribe-btn:hover,
.subscribe-btn:focus {
  background-color: #d9ecff;
  border-color: #409eff;
  color: #409eff;
}
.post-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}
.post-card {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 21, 41, 0.06);
  transition: transform 0.2s, box-shadow 0.2s;
}
.post-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(0, 21, 41, 0.12);
}
.post-img {
  position: relative;
  height: 150px;
  background: #f0f2f5;
}
.post-img .el-image {
  width: 100%;
  height: 100%;
}
.no-img {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}
.type-tag {
  position: absolute;
  top: 8px;
  left: 8px;
}
.important-tag {
  position: absolute;
  top: 8px;
  right: 8px;
}
.post-body {
  padding: 12px 14px;
}
.post-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.post-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.meta-item {
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 2px;
}
.post-time {
  margin-top: 6px;
  font-size: 12px;
  color: #c0c4cc;
}
.post-owner {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #606266;
}
.pager {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
.sub-tip {
  font-size: 13px;
  color: #909399;
  margin-bottom: 16px;
}
.sub-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sub-form-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.sub-label {
  width: 40px;
  font-size: 13px;
  color: #606266;
  flex-shrink: 0;
}
.sub-list {
  max-height: 220px;
  overflow: auto;
}
.sub-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px;
  border-bottom: 1px dashed #ebeef5;
}
.sub-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}
.sub-loc {
  color: #909399;
}
</style>
