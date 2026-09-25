<template>
  <div class="detail-page" v-loading="loading">
    <el-card class="post-card" v-if="post">
      <template #header>
        <div class="detail-header">
          <div class="detail-header-left">
            <el-tag :type="post.type === 'LOST' ? 'warning' : 'success'" effect="dark" size="large">
              {{ post.type === 'LOST' ? '失物' : '招领' }}
            </el-tag>
            <span class="detail-title">{{ post.title }}</span>
          </div>
          <div class="detail-header-right">
            <el-tag :type="statusTagType(post.status)" effect="dark" class="status-tag">
              {{ statusText(post.status, post.type) }}
            </el-tag>
            <el-tag v-if="post.aiAuditResult" :type="aiAuditType(post.aiAuditResult)" effect="plain" class="status-tag">
              {{ aiAuditText(post.aiAuditResult) }}
            </el-tag>
            <div class="detail-actions" v-if="isOwner">
              <el-button v-if="[0, 3, 4].includes(post.status)" size="small" @click="$router.push('/publish?id=' + post.id)">编辑</el-button>
              <el-button v-if="post.status === 0" size="small" type="danger" plain @click="onArchive">下架/归档</el-button>
              <el-button size="small" type="danger" @click="onDelete">删除</el-button>
            </div>
          </div>
        </div>
      </template>

      <el-alert
        v-if="post.status !== 0"
        :title="archivedTip"
        type="info"
        show-icon
        :closable="false"
        class="archived-alert"
      />

      <div class="detail-body">
        <div class="imgs" v-if="images.length">
          <el-image
            v-for="(img, i) in images"
            :key="i"
            :src="img"
            :preview-src-list="images"
            fit="cover"
            class="detail-img"
          />
        </div>

        <el-descriptions :column="2" border class="desc">
          <el-descriptions-item label="物品类别">
            <el-tag size="small" type="info" effect="plain">{{ post.category }}</el-tag>
            <el-tag v-if="post.aiCategory" size="small" type="primary" effect="plain" class="ai-tag">
              AI识别: {{ post.aiCategory }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="地点">{{ post.location }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ fmtTime(post.lostTime) }}</el-descriptions-item>
          <el-descriptions-item label="发布者">
            <el-avatar :size="20">{{ (post.ownerName || '?').charAt(0) }}</el-avatar>
            {{ post.ownerName }}
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ fmtTime(post.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="详细描述" :span="2">{{ post.description || '暂无描述' }}</el-descriptions-item>
          <el-descriptions-item v-if="post.ocrKey" label="关键号码" :span="2">
            <el-tag size="small" type="info" effect="plain">{{ maskKey }}（已脱敏）</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 智能匹配结果（合并到同一卡片内） -->
      <div class="match-section">
        <div class="match-header">
          <b><el-icon style="vertical-align: -2px"><MagicStick /></el-icon> 智能匹配结果</b>
          <el-button v-if="isOwner && post.status === 0" size="small" :loading="running" @click="runMatch">重新匹配</el-button>
        </div>

        <el-alert
          v-if="matches.length"
          type="info"
          :closable="false"
          show-icon
          class="match-note"
          title="图像相似度说明：同一物品因拍摄角度、光照不同，相似度通常仅 60%~85%；请结合文字信息与对方私聊确认。"
        />

      <el-empty v-if="!matches.length" :description="isOwner ? '暂未找到高相似度匹配，可稍后点击「重新匹配」' : '暂无匹配信息'" />

      <div v-else class="match-list">
        <template v-for="(m, idx) in matches" :key="m.otherPost.id">
          <!-- 前 5 条高匹配与"可能相关"之间的分隔 + 展开/收起 -->
          <div v-if="idx === 5 && matches.length > 5" class="rest-toggle" @click="showRest = !showRest">
            <el-button size="small" text type="primary">
              <el-icon v-if="!showRest"><ArrowDown /></el-icon>
              <el-icon v-else><ArrowUp /></el-icon>
              {{ showRest ? '收起更多匹配' : '展开更多匹配（' + (matches.length - 5) + ' 条）' }}
            </el-button>
          </div>
          <div v-if="showRest && idx === 5" class="rest-hint">
            <el-icon><InfoFilled /></el-icon>
            以下匹配度较低，但你的物品可能就在其中，请结合图片与描述逐一核对
          </div>

          <div v-if="idx < 5 || showRest" class="match-item" :class="{ 'match-item-rest': idx >= 5 }" @click="goDetail(m.otherPost.id)">
            <div class="match-main">
            <div class="match-post">
              <div class="match-post-title">
                <el-tag size="small" :type="m.otherPost.type === 'LOST' ? 'warning' : 'success'">
                  {{ m.otherPost.type === 'LOST' ? '失物' : '招领' }}
                </el-tag>
                <span class="link">{{ m.otherPost.title }}</span>
              </div>
              <div class="match-post-meta">
                <el-tag size="small" type="info" effect="plain">{{ m.otherPost.category }}</el-tag>
                <span><el-icon><Location /></el-icon>{{ m.otherPost.location }}</span>
                <span>{{ fmtTime(m.otherPost.lostTime) }}</span>
                <span>发布者：{{ m.otherPost.ownerName }}</span>
              </div>
              <div v-if="m.otherPost.imageUrls" class="match-thumb">
                <el-image :src="imgUrl(m.otherPost.imageUrls.split(',')[0].trim())" fit="cover" class="thumb" />
              </div>
            </div>

            <div class="match-score">
              <el-progress type="circle" :percentage="Math.round((m.finalScore || 0) * 100)" :width="84" :stroke-width="8"
                :color="scoreColor(m.finalScore)">
                <template #default>
                  <div class="score-text">{{ ((m.finalScore || 0) * 100).toFixed(0) }}<small>%</small></div>
                </template>
              </el-progress>
              <div class="score-tip">综合匹配度</div>
            </div>
          </div>

          <!-- 匹配依据 -->
          <div class="match-basis">
            <span class="basis-label">
              匹配依据：
              <el-tooltip :content="basisSummary" placement="top" effect="dark">
                <el-icon class="basis-help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
            <el-tooltip :content="basisTips.name" placement="top" effect="dark">
              <el-tag size="small" effect="plain" type="primary">名称相似 {{ pct(m.nameScore) }}</el-tag>
            </el-tooltip>
            <el-tooltip :content="basisTips.category" placement="top" effect="dark">
              <el-tag size="small" effect="plain" type="primary">类别 {{ pct(m.categoryScore) }}</el-tag>
            </el-tooltip>
            <el-tooltip :content="basisTips.location" placement="top" effect="dark">
              <el-tag size="small" effect="plain" type="primary">地点 {{ pct(m.locationScore) }}</el-tag>
            </el-tooltip>
            <el-tooltip :content="basisTips.time" placement="top" effect="dark">
              <el-tag size="small" effect="plain" type="primary">时间 {{ pct(m.timeScore) }}</el-tag>
            </el-tooltip>
            <el-tooltip :content="basisTips.description" placement="top" effect="dark">
              <el-tag size="small" effect="plain" type="primary">描述 {{ pct(m.descriptionScore) }}</el-tag>
            </el-tooltip>
            <el-tooltip :content="basisTips.semantic" placement="top" effect="dark">
              <el-tag size="small" effect="plain" type="primary">语义 {{ pct(m.semanticScore) }}</el-tag>
            </el-tooltip>
            <el-tooltip v-if="m.imageScore !== null && m.imageScore !== undefined" placement="top" effect="dark">
              <template #content>
                <div v-if="m.myImageCaption || m.otherImageCaption" class="img-cap-tip">
                  <div v-if="m.myImageCaption">我方图片：{{ m.myImageCaption }}</div>
                  <div v-if="m.otherImageCaption">对方图片：{{ m.otherImageCaption }}</div>
                  <div class="img-cap-sim">视觉特征相似度 {{ pct(m.imageScore) }}（同物因角度/光照通常 60%~85%）</div>
                </div>
                <span v-else>{{ basisTips.image }}</span>
              </template>
              <el-tag size="small" effect="plain" :type="imageLevelType(m.imageScore)">
                图像相似 {{ pct(m.imageScore) }}（{{ imageLevelText(m.imageScore) }}）
              </el-tag>
            </el-tooltip>
            <el-tag v-if="m.matchStatus" size="small" :type="matchStatusType(m.matchStatus)">
              {{ matchStatusText(m.matchStatus) }}
            </el-tag>
          </div>

          <div class="match-actions" v-if="canClaim(m) || canContact(m) || canIgnore(m)">
            <el-button v-if="canContact(m)" type="primary" size="small" plain @click.stop="goChat(m)">联系对方</el-button>
            <template v-if="canClaim(m)">
              <el-button v-if="!claimed(m)" type="warning" size="small" @click.stop="openClaim(m)">申请认领</el-button>
              <el-tag v-else size="small" type="info">已申请认领，等待对方确认</el-tag>
            </template>
            <el-button v-if="canIgnore(m)" size="small" type="info" plain @click.stop="onIgnore(m)">忽略</el-button>
          </div>
        </div>
        </template>
      </div>
      </div>
    </el-card>

    <!-- 认领申请对话框 -->
    <el-dialog v-model="claimDialog" title="申请认领" width="460px">
      <el-form label-width="80px">
        <el-form-item label="对方信息">
          <span v-if="claimTarget">「{{ claimTarget.otherPost.title }}」- {{ claimTarget.otherPost.location }}</span>
        </el-form-item>
        <el-form-item label="核验凭据" required>
          <el-input v-model="claimMessage" type="textarea" :rows="3" maxlength="200"
            placeholder="请填写只有物主才知道的细节，用于拾主比对防冒领（如：钱包内有 XX 元现金、校园卡尾号 1234）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="claimDialog = false">取消</el-button>
        <el-button type="primary" :loading="claiming" @click="submitClaim">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { postApi, matchApi, claimApi } from '../api'
import { useUserStore } from '../store/user'
import { imgUrl, fmtTime, pct, statusText, matchStatusText, aiAuditText, aiAuditType } from '../utils'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const post = ref(null)
const matches = ref([])
const loading = ref(false)
const running = ref(false)

const claimDialog = ref(false)
const claimTarget = ref(null)
const claimMessage = ref('')
const claiming = ref(false)

// 匹配结果分级展示：前 5 条高亮，其余折叠为"可能相关"
const showRest = ref(false)

// 匹配依据各维度悬浮解释（可解释 AI：让用户看懂每个数字怎么来的、意味着什么）
const basisTips = {
  name: '标题文字相似度（编辑距离 + 包含关系加权）',
  category: '物品类别是否一致（含同义词归一化，如"钱包/包"与"钱包"视为同类）',
  location: '丢失/拾获地点文字相似度，地点越接近越可能是同一物品',
  time: '时间越接近越可能是同一物品',
  description: '从描述中抽取颜色/品牌/型号后的属性相似度',
  semantic: 'AI 语义向量相似度：字面不同但语义相关（如"双肩包"与"书包"）',
  image: '双方上传图片的视觉特征余弦相似度；同一物品因角度/光照不同通常仅 60%~85%，低于 50% 可能只是同款而非同一件，需结合文字与私聊确认'
}
const basisSummary = '匹配依据由名称、类别、地点、时间、描述、语义、图像等多维度加权计算，悬浮各标签查看含义'

const postId = computed(() => Number(route.params.id))
// 从消息通知跳转时携带我的帖子 id，用于只保留与「我的物品」的匹配
const myPostId = computed(() => (route.query.myPostId ? Number(route.query.myPostId) : null))
const images = computed(() => (post.value?.imageUrls || '').split(',').filter(Boolean).map((p) => imgUrl(p.trim())))
const maskKey = computed(() => {
  const k = post.value?.ocrKey
  if (!k) return ''
  if (k.length <= 4) return '****'
  return k.slice(0, 2) + '****' + k.slice(-2)
})
const isOwner = computed(() => !!userStore.user && post.value?.userId === userStore.user.id)
const archivedTip = computed(() => {
  if (post.value?.status === 1) return '认领已确认，双方信息已自动下架，无法再修改'
  if (post.value?.status === 2) return '该物品已下架/归档，无法再修改相关信息'
  if (post.value?.status === 3) return '该信息正在审核中，审核通过后将在信息广场展示'
  if (post.value?.status === 4) {
    const reason = post.value?.auditReason ? '，原因：' + post.value.auditReason : ''
    return '该信息未通过审核' + reason + '，可编辑后重新提交'
  }
  return ''
})

async function load() {
  loading.value = true
  try {
    post.value = await postApi.detail(postId.value)
    const list = (await matchApi.forPost(postId.value)) || []
    // 从消息通知进入时，只保留与「我的物品」的匹配
    matches.value = myPostId.value
      ? list.filter((m) => m.otherPost && m.otherPost.id === myPostId.value)
      : list
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

async function runMatch() {
  running.value = true
  try {
    matches.value = (await matchApi.run(postId.value)) || []
    ElMessage.success('匹配完成')
  } catch (e) {
    /* ignore */
  } finally {
    running.value = false
  }
}

function statusTagType(status) {
  if (status === 0) return 'success'
  if (status === 1) return 'warning'
  if (status === 3) return 'warning'
  if (status === 4) return 'danger'
  return 'info'
}

function scoreColor(v) {
  if (v >= 0.7) return '#67C23A'
  if (v >= 0.5) return '#E6A23C'
  return '#F56C6C'
}

/** 图像相似度分档文案（重新定位图片匹配：辅助筛选，而非自动判定同一物品） */
function imageLevelText(v) {
  if (v >= 0.9) return '高度一致'
  if (v >= 0.7) return '疑似同一物品'
  if (v >= 0.5) return '可能同款'
  return '参考性较低'
}

function imageLevelType(v) {
  if (v >= 0.9) return 'danger'
  if (v >= 0.7) return 'warning'
  if (v >= 0.5) return 'warning'
  return 'info'
}

function matchStatusType(s) {
  const map = { NEW: 'success', CONTACTED: 'warning', CLAIMED: 'info', IGNORED: 'info' }
  return map[s] || 'info'
}

/** 仅失主（当前帖是失物帖且我是发布者）可以申请认领 */
function canClaim(m) {
  return (
    userStore.isLogin &&
    isOwner.value &&
    post.value?.status === 0 &&
    post.value?.type === 'LOST' &&
    m.matchId &&
    m.matchStatus &&
    m.matchStatus !== 'CLAIMED' &&
    m.matchStatus !== 'IGNORED'
  )
}

function claimed(m) {
  return m.matchStatus === 'CONTACTED' && m.matchId
}

function canContact(m) {
  return userStore.isLogin && isOwner.value && post.value?.status === 0 && m.otherPost
}

function canIgnore(m) {
  return (
    userStore.isLogin &&
    isOwner.value &&
    post.value?.status === 0 &&
    m.matchId &&
    m.matchStatus !== 'CLAIMED' &&
    m.matchStatus !== 'IGNORED'
  )
}

async function onIgnore(m) {
  ElMessageBox.confirm('忽略后该匹配将不再推荐，确定忽略吗？', '提示', { type: 'warning' })
    .then(async () => {
      await matchApi.ignore(m.matchId)
      ElMessage.success('已忽略该匹配')
      load()
    })
    .catch(() => {})
}

function goChat(m) {
  // 会话的物品上下文统一用「失物帖」，保证失主与拾主进入同一会话（避免各自帖子 id 不同导致会话分裂）
  const lostPost = post.value?.type === 'LOST' ? post.value : m.otherPost
  router.push({
    path: '/chat/' + m.otherPost.userId,
    query: {
      name: m.otherPost.ownerName || '',
      postId: lostPost?.id,
      title: lostPost?.title || ''
    }
  })
}

function goDetail(id) {
  router.push('/post/' + id)
}

function openClaim(m) {
  claimTarget.value = m
  claimMessage.value = ''
  claimDialog.value = true
}

async function submitClaim() {
  if (!claimMessage.value.trim()) {
    ElMessage.warning('请填写物品核验凭据，便于拾主确认')
    return
  }
  claiming.value = true
  try {
    await claimApi.apply({ matchId: claimTarget.value.matchId, message: claimMessage.value })
    ElMessage.success('认领申请已提交，等待拾主确认')
    claimDialog.value = false
    load()
  } catch (e) {
    /* ignore */
  } finally {
    claiming.value = false
  }
}

function onArchive() {
  ElMessageBox.confirm('下架后该信息将不再展示，确定下架吗？', '提示', { type: 'warning' })
    .then(async () => {
      await postApi.archive(postId.value)
      ElMessage.success('已下架')
      load()
    })
    .catch(() => {})
}

function onDelete() {
  ElMessageBox.confirm('删除后不可恢复，确定删除吗？', '警告', { type: 'error' })
    .then(async () => {
      await postApi.remove(postId.value)
      ElMessage.success('已删除')
      router.push('/home')
    })
    .catch(() => {})
}

// 同组件路由切换（如 /post/1 → /post/2）复用组件实例，onMounted 不会再次触发，
// 若不监听参数变化，点击匹配列表名称/图片跳转后页面内容不会更新
watch(() => route.fullPath, () => load())

onMounted(load)
</script>

<style scoped>
.detail-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.detail-header-left {
  display: flex;
  align-items: center;
  min-width: 0;
}
.detail-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.status-tag {
  flex-shrink: 0;
}
.detail-title {
  font-size: 18px;
  font-weight: 700;
  margin-left: 10px;
  color: #303133;
}
.archived-alert {
  margin-bottom: 16px;
}
.detail-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.imgs {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.detail-img {
  width: 200px;
  height: 150px;
  border-radius: 8px;
}
.ai-tag {
  margin-left: 6px;
}
.post-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.post-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.match-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
.match-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.match-note {
  margin-bottom: 12px;
}
.match-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.match-item {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px 16px;
  transition: box-shadow 0.2s;
  cursor: pointer;
}
.match-item:hover {
  box-shadow: 0 4px 12px rgba(0, 21, 41, 0.08);
}
.match-item-rest {
  opacity: 0.88;
}
.rest-toggle {
  margin: 4px 0;
  text-align: center;
}
.rest-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: center;
  margin: 4px 0 8px;
  font-size: 12px;
  color: #909399;
}
.match-main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}
.match-post-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.link {
  color: #409eff;
  text-decoration: none;
  font-weight: 600;
}
.link:hover {
  text-decoration: underline;
}
.match-post-meta {
  margin-top: 8px;
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: #909399;
  align-items: center;
  flex-wrap: wrap;
}
.match-post-meta span {
  display: flex;
  align-items: center;
  gap: 3px;
}
.match-thumb {
  margin-top: 10px;
}
.thumb {
  width: 90px;
  height: 70px;
  border-radius: 6px;
}
.match-score {
  text-align: center;
  flex-shrink: 0;
}
.score-text {
  font-size: 18px;
  font-weight: 700;
}
.score-text small {
  font-size: 11px;
  font-weight: 400;
}
.score-tip {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}
.match-basis {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  border-top: 1px dashed #ebeef5;
  padding-top: 10px;
}
.basis-label {
  font-size: 12px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 2px;
}
.basis-help {
  color: #909399;
  cursor: help;
}
.img-cap-tip {
  max-width: 260px;
  line-height: 1.7;
}
.img-cap-sim {
  margin-top: 4px;
  color: #c0c4cc;
}
.match-actions {
  margin-top: 10px;
}
</style>
