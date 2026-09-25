<template>
  <div class="claim-detail-page" v-loading="loading">
    <el-card class="claim-card" v-if="claim">
      <template #header>
        <div class="claim-header">
          <b>失物信息</b>
          <div class="status-block">
            <el-tag :type="statusType(claim.status)" size="large" effect="dark">{{ claimStatusText(claim.status) }}</el-tag>
            <span class="status-desc">{{ statusDesc(claim.status) }}</span>
          </div>
        </div>
      </template>

      <!-- 失物信息 -->
      <div class="imgs" v-if="lostImages.length">
        <el-image v-for="(img, i) in lostImages" :key="i" :src="img" :preview-src-list="lostImages" fit="cover" class="img" />
      </div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="物品名称">{{ claim.postTitle || lostPost?.title || '—' }}</el-descriptions-item>
        <el-descriptions-item label="物品类别">
          <el-tag size="small" type="info" effect="plain">{{ lostPost?.category || '—' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="丢失地点">{{ lostPost?.location || '—' }}</el-descriptions-item>
        <el-descriptions-item label="丢失时间">{{ fmtTime(lostPost?.lostTime) }}</el-descriptions-item>
        <el-descriptions-item label="发布者">{{ claim.claimantName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="详细描述" :span="2">{{ lostPost?.description || '—' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 认领申请 -->
      <el-divider content-position="left">认领申请</el-divider>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申请人">{{ claim.claimantName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ fmtTime(claim.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="认领说明" :span="2">{{ claim.message || '—' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 匹配的招领信息 -->
      <el-divider content-position="left">匹配的招领信息</el-divider>
      <template v-if="foundPost">
        <div class="found-title">
          <el-tag size="small" type="success">招领</el-tag>
          <router-link :to="'/post/' + foundPost.id" class="link">{{ foundPost.title }}</router-link>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="拾获地点">{{ foundPost.location || '—' }}</el-descriptions-item>
          <el-descriptions-item label="拾获时间">{{ fmtTime(foundPost.lostTime) }}</el-descriptions-item>
          <el-descriptions-item label="匹配度">{{ pct(claim.match?.score) }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ foundPost.description || '—' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 匹配依据（六维可解释，与帖子详情一致） -->
        <div v-if="claim.match && claim.match.nameScore != null" class="match-basis">
          <div class="basis-label">匹配依据</div>
          <el-tag size="small" effect="plain" type="primary">名称相似 {{ pct(claim.match.nameScore) }}</el-tag>
          <el-tag size="small" effect="plain" type="primary">类别 {{ pct(claim.match.categoryScore) }}</el-tag>
          <el-tag size="small" effect="plain" type="primary">地点 {{ pct(claim.match.locationScore) }}</el-tag>
          <el-tag size="small" effect="plain" type="primary">时间 {{ pct(claim.match.timeScore) }}</el-tag>
          <el-tag size="small" effect="plain" type="primary">描述 {{ pct(claim.match.descriptionScore) }}</el-tag>
          <el-tag size="small" effect="plain" type="primary">语义 {{ pct(claim.match.semanticScore) }}</el-tag>
          <el-tag v-if="claim.match.imageScore != null" size="small" effect="plain" type="warning">图像相似 {{ pct(claim.match.imageScore) }}</el-tag>
        </div>
      </template>
      <el-empty v-else description="招领信息不存在" />

      <!-- 处理认领 -->
      <template v-if="canHandle">
        <el-divider content-position="left">处理认领</el-divider>
        <div class="actions">
          <el-button type="success" :loading="acting" @click="confirm">确认认领</el-button>
          <el-button type="danger" plain :loading="acting" @click="reject">拒绝认领</el-button>
          <span class="tip">确认后，失物与招领信息将自动下架</span>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { claimApi } from '../api'
import { useUserStore } from '../store/user'
import { imgUrl, fmtTime, pct, claimStatusText } from '../utils'

const route = useRoute()
const userStore = useUserStore()

const claim = ref(null)
const loading = ref(false)
const acting = ref(false)

const lostPost = computed(() => claim.value?.match?.lostPost || null)
const foundPost = computed(() => claim.value?.match?.foundPost || null)
const lostImages = computed(() => (lostPost.value?.imageUrls || '').split(',').filter(Boolean).map((p) => imgUrl(p.trim())))
const isFinder = computed(() => !!userStore.user && foundPost.value?.userId === userStore.user.id)
const canHandle = computed(() => claim.value?.status === 'PENDING' && isFinder.value)

function statusType(s) {
  const map = { PENDING: 'warning', CONFIRMED: 'success', REJECTED: 'danger' }
  return map[s] || 'info'
}

function statusDesc(s) {
  const map = {
    PENDING: '认领申请待拾主确认',
    CONFIRMED: '认领已确认，双方信息已自动下架',
    REJECTED: '认领申请已被拒绝'
  }
  return map[s] || ''
}

async function load() {
  loading.value = true
  try {
    claim.value = await claimApi.detail(Number(route.params.id))
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

function confirm() {
  ElMessageBox.confirm('确认认领后，失物与招领信息将自动下架，确定继续吗？', '确认认领', { type: 'warning' })
    .then(async () => {
      acting.value = true
      try {
        await claimApi.confirm(claim.value.id)
        ElMessage.success('认领成功，相关帖子已自动下架')
        load()
      } catch (e) {
        /* ignore */
      } finally {
        acting.value = false
      }
    })
    .catch(() => {})
}

function reject() {
  ElMessageBox.confirm('确定拒绝该认领申请吗？', '拒绝认领', { type: 'warning' })
    .then(async () => {
      acting.value = true
      try {
        await claimApi.reject(claim.value.id)
        ElMessage.success('已拒绝该认领申请')
        load()
      } catch (e) {
        /* ignore */
      } finally {
        acting.value = false
      }
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.claim-detail-page {
  height: 100%;
}
.claim-detail-page :deep(.claim-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.claim-detail-page :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.claim-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.status-block {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.status-desc {
  font-size: 12px;
  color: #909399;
}
.imgs {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.img {
  width: 180px;
  height: 130px;
  border-radius: 8px;
}
.found-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.link {
  color: #409eff;
  text-decoration: none;
  font-weight: 600;
}
.link:hover {
  text-decoration: underline;
}
.match-basis {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
}
.basis-label {
  font-size: 13px;
  color: #606266;
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tip {
  color: #909399;
  font-size: 12px;
}
</style>
