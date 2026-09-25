<template>
  <div class="publish-page">
    <el-card class="publish-card">
      <template #header>
        <div class="card-header">
          <b>{{ editId ? '编辑信息' : '发布失物 / 招领信息' }}</b>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="信息类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button value="LOST">😢 失物（我丢了东西）</el-radio-button>
            <el-radio-button value="FOUND">😄 招领（我捡到东西）</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="物品图片">
          <div class="upload-area">
            <el-upload
              list-type="picture-card"
              v-model:file-list="fileList"
              :http-request="doUpload"
              :on-remove="onRemove"
              :limit="3"
              accept="image/*"
            >
              <el-icon><Plus /></el-icon>
            </el-upload>
            <div class="upload-tip">
              <p>支持 jpg/png/gif/webp，最多 3 张。</p>
              <p class="ai-line">
                <template v-if="classifying"><el-icon style="vertical-align: -2px"><MagicStick /></el-icon> 正在自动识别物品类别…</template>
                <template v-else-if="aiResult"><el-icon style="vertical-align: -2px"><MagicStick /></el-icon> 已自动识别为「{{ aiResult }}」</template>
                <template v-else>上传后系统将自动识别物品类别</template>
              </p>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="物品名称" prop="title">
          <el-input v-model="form.title" placeholder="如：蓝色书包 / 黑色钱包" maxlength="50" show-word-limit />
        </el-form-item>

        <el-form-item label="物品类别" prop="category">
          <div class="category-row">
            <el-select v-model="form.category" placeholder="请选择类别" style="width: 220px">
              <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
            </el-select>
            <el-tag v-if="aiResult" type="success" size="small" effect="plain" class="ai-tag">
              <el-icon style="vertical-align: -2px"><MagicStick /></el-icon> AI 已识别
            </el-tag>
          </div>
        </el-form-item>

        <el-form-item label="地点" prop="location">
          <el-input v-model="form.location" maxlength="50"
            placeholder="最近去过哪里？可能在哪丢失（如：图书馆三楼 / 第一食堂）" />
          <div class="loc-tip">
            <el-icon><InfoFilled /></el-icon>
            想不起具体位置？填写最近去过的地方或大致区域（如"三食堂附近""西区教学楼"），系统会据此匹配可能的地点
          </div>
        </el-form-item>

        <el-form-item label="时间" prop="lostTime">
          <el-date-picker
            v-model="form.lostTime"
            type="datetime"
            placeholder="丢失 / 拾获时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 240px"
          />
        </el-form-item>

        <el-form-item label="详细描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="补充物品特征、颜色、品牌等细节，有助于更快匹配"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="onSubmit">
            {{ editId ? '保存修改' : '立即发布' }}
          </el-button>
          <el-button @click="$router.back()">取消</el-button>
          <span v-if="editId" class="edit-tip">保存后将重新进行 AI 审核，内容合规才会继续展示</span>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { postApi, fileApi, aiApi } from '../api'
import { useUserStore } from '../store/user'
import { imgUrl } from '../utils'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const submitting = ref(false)
const classifying = ref(false)
const categories = ref([])
const editId = route.query.id ? Number(route.query.id) : null

const form = reactive({
  type: 'LOST',
  title: '',
  category: '',
  location: '',
  lostTime: '',
  description: '',
  imageUrls: '',
  aiCategory: ''
})

const fileList = ref([])
const aiResult = ref('')
const aiConfidence = ref(0)

const rules = {
  type: [{ required: true, message: '请选择信息类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入物品名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择物品类别', trigger: 'change' }],
  location: [{ required: true, message: '请输入地点', trigger: 'blur' }],
  lostTime: [{ required: true, message: '请选择时间', trigger: 'change' }]
}

function doUpload({ file, onSuccess, onError }) {
  const fd = new FormData()
  fd.append('file', file)
  fileApi
    .upload(fd)
    .then((data) => {
      onSuccess(data)
      ElMessage.success('图片上传成功')
      autoClassify(file)
    })
    .catch((e) => onError(e))
}

/** 上传成功后自动调用 AI 识别物品类别，并自动填充到类别选择 */
async function autoClassify(file) {
  classifying.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const data = await aiApi.classify(fd)
    if (data.available && data.category && data.category !== '其他') {
      const changed = form.category !== data.category
      form.category = data.category
      form.aiCategory = data.category
      aiResult.value = data.category
      aiConfidence.value = data.confidence || 0
      if (changed) {
        ElMessage.success('AI 已自动识别为「' + data.category + '」，可手动切换类别')
      }
      // 隐私保护：证件/卡类图片可能含姓名、证件号等敏感信息，提醒脱敏
      if (data.category === '证件/卡') {
        ElMessageBox.alert(
          '检测到证件/卡类图片，可能包含姓名、证件号、照片等个人信息。建议先对敏感信息打码或遮挡后再上传，以免泄露隐私。',
          '隐私保护提醒',
          { type: 'warning', confirmButtonText: '我知道了' }
        )
      }
    }
  } catch (e) {
    /* 识别失败静默忽略，用户可手动选择类别 */
  } finally {
    classifying.value = false
  }
}

function onRemove(file) {
  fileList.value = fileList.value.filter((f) => f.uid !== file.uid)
  if (fileList.value.length === 0) {
    aiResult.value = ''
    aiConfidence.value = 0
    form.aiCategory = ''
  }
  refreshImageUrls()
}

function refreshImageUrls() {
  const local = fileList.value.map((f) => f.response?.path || f.path || '').filter(Boolean)
  form.imageUrls = local.join(',')
}

async function loadCategories() {
  try {
    categories.value = await postApi.categories()
  } catch (e) {
    /* ignore */
  }
}

async function loadEdit() {
  if (!editId) return
  try {
    const post = await postApi.detail(editId)
    if ([1, 2].includes(post.status)) {
      ElMessage.warning('该帖子已归档/下架，无法编辑')
      router.replace('/myposts')
      return
    }
    form.type = post.type
    form.title = post.title
    form.category = post.category
    form.location = post.location
    form.lostTime = post.lostTime
    form.description = post.description || ''
    form.imageUrls = post.imageUrls || ''
    form.aiCategory = post.aiCategory || ''
    const urls = (post.imageUrls || '').split(',').map((s) => s.trim()).filter(Boolean)
    fileList.value = urls.map((p, i) => ({
      uid: Date.now() + i,
      name: p.split('/').pop(),
      path: p,
      url: imgUrl(p)
    }))
  } catch (e) {
    /* ignore */
  }
}

async function onSubmit() {
  await formRef.value.validate()
  refreshImageUrls()
  submitting.value = true
  try {
    const payload = { ...form }
    if (editId) {
      await postApi.update(editId, payload)
      ElMessage.success('修改成功')
    } else {
      await postApi.publish(payload)
      if (userStore.isAdmin) {
        ElMessage.success('发布成功，已通过 AI 内容安全检测并上线信息广场')
      } else {
        ElMessage.success('提交成功，帖子已进入管理员审核，审核通过后将在信息广场展示')
      }
      router.push('/myposts')
      return
    }
    router.push('/myposts')
  } catch (e) {
    /* ignore */
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadCategories()
  loadEdit()
})
</script>

<style scoped>
.publish-page {
  height: 100%;
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
}
.publish-page :deep(.publish-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.publish-page :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.publish-card {
  border-radius: 10px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.upload-area {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.upload-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.9;
}
.loc-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.ai-line {
  margin-top: 6px;
}
.category-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ai-tag {
  flex-shrink: 0;
}
.edit-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
</style>
