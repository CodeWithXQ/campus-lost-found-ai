<template>
  <div class="myposts-page">
    <el-card>
      <template #header>
        <div class="header">
          <div class="header-left">
            <b>我的发布</b>
            <el-tag type="info" size="small" effect="plain">共 {{ total }} 条</el-tag>
          </div>
          <el-button type="primary" size="small" @click="$router.push('/publish')">发布新信息</el-button>
        </div>
      </template>

      <div class="list-scroll">
        <el-empty v-if="!loading && list.length === 0" description="你还没有发布任何信息">
          <el-button type="primary" @click="$router.push('/publish')">去发布</el-button>
        </el-empty>

        <div v-loading="loading" class="post-list">
          <div v-for="p in list" :key="p.id" class="post-item" @click="goDetail(p)">
            <!-- 左：我发布的物品 -->
            <div class="item-side left-side">
              <div class="thumb-wrap">
                <el-image v-if="firstImg(p)" :src="firstImg(p)" fit="cover" class="thumb" />
                <div v-else class="thumb thumb-placeholder">
                  <el-icon :size="24" color="#c0c4cc"><Picture /></el-icon>
                </div>
              </div>
              <div class="info">
                <div class="title-row">
                  <el-tag size="small" :type="p.type === 'LOST' ? 'warning' : 'success'" effect="dark">
                    {{ p.type === 'LOST' ? '失物' : '招领' }}
                  </el-tag>
                  <span class="title">{{ p.title }}</span>
                  <el-tag size="small" type="info" effect="plain">{{ p.category }}</el-tag>
                </div>
                <div class="meta">
                  <span class="meta-item"><el-icon><Location /></el-icon>{{ p.location }}</span>
                  <span class="meta-item">{{ fmtTime(p.lostTime) }}</span>
                  <el-tag size="small" :type="statusTagType(p.status)">{{ statusText(p.status, p.type) }}</el-tag>
                  <el-tooltip v-if="p.aiAuditResult" :content="p.aiAuditNote || 'AI 初审核'">
                    <el-tag size="small" :type="aiAuditType(p.aiAuditResult)" effect="plain">{{ aiAuditText(p.aiAuditResult) }}</el-tag>
                  </el-tooltip>
                  <el-tooltip v-if="p.auditReason" :content="p.auditReason">
                    <span class="audit-reason">拒绝原因：{{ p.auditReason }}</span>
                  </el-tooltip>
                </div>
                <div class="hint">
                  <template v-if="p.matchCount">
                    <span class="hint-count">匹配到 {{ p.matchCount }} 条</span>
                  </template>
                  <span v-else class="no-match">暂无匹配记录</span>
                </div>
              </div>
            </div>

            <!-- 中：最高匹配度 + 长箭头 -->
            <div v-if="p.topMatchPost" class="match-arrow">
              <div class="match-arrow-info">
                <div class="match-arrow-label">最高匹配度</div>
                <div class="match-arrow-score">{{ pct(p.maxMatchScore) }}</div>
              </div>
              <div class="match-arrow-line"></div>
            </div>

            <!-- 右：最高匹配物品（与左侧 1:1 镜像，仅展示不可点击跳转） -->
            <div v-if="p.topMatchPost" class="item-side">
              <div class="thumb-wrap">
                <el-image v-if="firstImg(p.topMatchPost)" :src="firstImg(p.topMatchPost)" fit="cover" class="thumb" />
                <div v-else class="thumb thumb-placeholder">
                  <el-icon :size="24" color="#c0c4cc"><Picture /></el-icon>
                </div>
              </div>
              <div class="info">
                <div class="title-row">
                  <el-tag size="small" :type="p.topMatchPost.type === 'LOST' ? 'warning' : 'success'" effect="dark">
                    {{ p.topMatchPost.type === 'LOST' ? '失物' : '招领' }}
                  </el-tag>
                  <span class="title">{{ p.topMatchPost.title }}</span>
                  <el-tag size="small" type="info" effect="plain">{{ p.topMatchPost.category }}</el-tag>
                </div>
                <div class="meta">
                  <span class="meta-item"><el-icon><Location /></el-icon>{{ p.topMatchPost.location }}</span>
                  <span class="meta-item">{{ fmtTime(p.topMatchPost.lostTime) }}</span>
                </div>
              </div>
            </div>

            <!-- 最右：进入图标 -->
            <div class="enter-icon">
              <el-icon :size="18" color="#c0c4cc"><ArrowRight /></el-icon>
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { postApi } from '../api'
import { imgUrl, fmtTime, pct, statusText, aiAuditText, aiAuditType } from '../utils'

const router = useRouter()
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await postApi.my({ page: page.value, size: size.value })
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

function firstImg(p) {
  const urls = (p.imageUrls || '').split(',').filter(Boolean)
  return urls.length ? imgUrl(urls[0].trim()) : ''
}

function statusTagType(status) {
  if (status === 0) return 'success'
  if (status === 3) return 'warning'
  if (status === 4) return 'danger'
  return 'info'
}

function goDetail(p) {
  router.push('/post/' + p.id)
}

onMounted(load)
</script>

<style scoped>
.myposts-page {
  height: 100%;
}
.myposts-page :deep(.el-card) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.myposts-page :deep(.el-card__body) {
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
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.list-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.pager {
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  padding: 14px 0 4px;
}
.post-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100px;
}
.post-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}
.post-item:hover {
  box-shadow: 0 4px 12px rgba(0, 21, 41, 0.08);
  border-color: #c6e2ff;
}
.item-side {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;
}
.item-side.left-side {
  flex-direction: row-reverse;
}
.thumb-wrap {
  flex-shrink: 0;
}
.thumb {
  width: 90px;
  height: 70px;
  border-radius: 8px;
  display: block;
}
.thumb-placeholder {
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
}
.info {
  flex: 1;
  min-width: 0;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: #909399;
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 3px;
}
.audit-reason {
  color: #f56c6c;
  font-size: 12px;
}
.hint {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.hint-count {
  color: #409eff;
  font-weight: 600;
}
.no-match {
  color: #c0c4cc;
}
.match-arrow {
  flex-shrink: 0;
  width: 160px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.match-arrow-info {
  text-align: center;
  line-height: 1.3;
}
.match-arrow-label {
  font-size: 12px;
  color: #c0c4cc;
}
.match-arrow-score {
  font-size: 22px;
  font-weight: 700;
  color: #e6a23c;
}
.match-arrow-line {
  position: relative;
  width: 100%;
  height: 3px;
  background: #e6a23c;
  border-radius: 2px;
}
.match-arrow-line::after {
  content: '';
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 0;
  height: 0;
  border-top: 7px solid transparent;
  border-bottom: 7px solid transparent;
  border-left: 12px solid #e6a23c;
}
.enter-icon {
  flex-shrink: 0;
  color: #c0c4cc;
}
</style>
