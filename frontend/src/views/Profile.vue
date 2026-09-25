<template>
  <div class="profile-page">
    <el-card class="profile-card">
      <template #header>
        <div class="card-header">
          <b>个人中心</b>
          <el-button size="small" type="primary" plain @click="openEdit">编辑资料</el-button>
        </div>
      </template>

      <!-- 个人信息区 -->
      <div class="user-info">
        <el-avatar :size="72" :src="avatarUrl">{{ (userStore.user?.nickname || '?').charAt(0) }}</el-avatar>
        <div class="user-main">
          <div class="user-nick-row">
            <span class="nick">{{ userStore.user?.nickname }}</span>
            <el-tag v-if="userStore.isAdmin" type="danger">管理员</el-tag>
            <el-tag v-else type="info">普通用户</el-tag>
          </div>
          <el-descriptions :column="2" class="user-desc">
            <el-descriptions-item label="用户名">{{ userStore.user?.username }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ userStore.user?.phone || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userStore.user?.email || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ fmtTime(userStore.user?.createTime) }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <el-button type="danger" plain class="logout-btn" @click="logout">退出登录</el-button>
      </div>

      <el-divider class="divider" />

      <!-- 我的发布区 -->
      <div class="posts-header">
        <b><el-icon style="vertical-align: -2px"><FolderOpened /></el-icon> 我的发布</b>
        <el-tag type="info" size="small" effect="plain">共 {{ total }} 条</el-tag>
      </div>

      <div class="posts-scroll">
        <el-empty v-if="!loading && myPosts.length === 0" description="还没有发布过信息" />
        <el-table v-loading="loading" :data="myPosts" stripe>
          <el-table-column label="类型" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.type === 'LOST' ? 'warning' : 'success'">
                {{ row.type === 'LOST' ? '失物' : '招领' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="物品" min-width="140" />
          <el-table-column prop="location" label="地点" min-width="120" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 0 ? 'success' : 'info'">{{ statusText(row.status, row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发布时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="$router.push('/post/' + row.id)">详情/匹配</el-button>
              <el-button v-if="[0, 3, 4].includes(row.status)" size="small" link @click="$router.push('/publish?id=' + row.id)">编辑</el-button>
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

    <!-- 编辑个人信息对话框 -->
    <el-dialog v-model="editDialog" title="编辑个人信息" width="440px">
      <el-form label-width="80px">
        <el-form-item label="头像">
          <div class="avatar-edit">
            <el-avatar :size="64" :src="imgUrl(form.avatar)">{{ (form.nickname || '?').charAt(0) }}</el-avatar>
            <el-upload :show-file-list="false" :http-request="uploadAvatar" accept="image/*">
              <el-button size="small">上传头像</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="20" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="20" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" maxlength="50" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { postApi, fileApi } from '../api'
import { useUserStore } from '../store/user'
import { fmtTime, statusText, imgUrl } from '../utils'

const router = useRouter()
const userStore = useUserStore()
const myPosts = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const editDialog = ref(false)
const saving = ref(false)
const form = ref({ nickname: '', phone: '', email: '', avatar: '' })

const avatarUrl = computed(() => imgUrl(userStore.user?.avatar))

async function load() {
  loading.value = true
  try {
    const data = await postApi.my({ page: page.value, size: size.value })
    myPosts.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

function openEdit() {
  const u = userStore.user || {}
  form.value = {
    nickname: u.nickname || '',
    phone: u.phone || '',
    email: u.email || '',
    avatar: u.avatar || ''
  }
  editDialog.value = true
}

function uploadAvatar({ file, onSuccess, onError }) {
  const fd = new FormData()
  fd.append('file', file)
  fileApi
    .upload(fd)
    .then((data) => {
      form.value.avatar = data.path
      onSuccess(data)
      ElMessage.success('头像上传成功')
    })
    .catch((e) => onError(e))
}

async function saveProfile() {
  if (!form.value.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    await userStore.updateProfile({
      nickname: form.value.nickname.trim(),
      phone: form.value.phone,
      email: form.value.email,
      avatar: form.value.avatar
    })
    ElMessage.success('保存成功')
    editDialog.value = false
  } catch (e) {
    /* ignore */
  } finally {
    saving.value = false
  }
}

function logout() {
  ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    .then(() => {
      userStore.logout()
      router.push('/login')
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.profile-page {
  height: 100%;
}
.profile-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.profile-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 20px;
}
.user-main {
  flex: 1;
  min-width: 0;
}
.user-nick-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.nick {
  font-size: 18px;
  font-weight: 700;
}
.logout-btn {
  flex-shrink: 0;
}
.divider {
  margin: 16px 0;
}
.posts-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.posts-scroll {
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
.avatar-edit {
  display: flex;
  align-items: center;
  gap: 14px;
}
</style>
