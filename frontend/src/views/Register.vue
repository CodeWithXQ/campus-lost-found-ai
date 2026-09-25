<template>
  <div class="auth-page">
    <div class="auth-left">
      <div class="brand">
        <img class="brand-logo" src="/favicon.png" alt="logo" title="点击切换到登录" @click="$router.push('/login')" />
        <h1>校园失物招领智能匹配系统</h1>
        <p>失物招领 · AI 图像识别 · 智能匹配</p>
      </div>
    </div>
    <div class="auth-right">
      <div class="auth-card">
        <div class="auth-title">
          <img class="title-logo" src="/favicon.png" alt="logo" />
          <h2>注册账号</h2>
          <p>加入校园失物招领平台，互帮互助</p>
        </div>
        <el-form ref="formRef" :model="form" :rules="rules" size="large">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名（至少3个字符）" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="nickname">
            <el-input v-model="form.nickname" placeholder="昵称（选填）" :prefix-icon="Avatar" />
          </el-form-item>
          <el-form-item prop="phone">
            <el-input v-model="form.phone" placeholder="手机号（选填）" :prefix-icon="Iphone" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码（至少6位）" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item prop="confirm">
            <el-input v-model="form.confirm" type="password" placeholder="确认密码" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item>
            <el-button class="submit-btn" :loading="loading" @click="onRegister">注 册</el-button>
          </el-form-item>
        </el-form>
        <div class="auth-links">
          <span>已有账号？</span>
          <el-link class="auth-link" type="primary" @click="$router.push('/login')">去登录</el-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Avatar, Iphone } from '@element-plus/icons-vue'
import { authApi } from '../api'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', nickname: '', phone: '', password: '', confirm: '' })

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, message: '用户名至少 3 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function onRegister() {
  await formRef.value.validate()
  loading.value = true
  try {
    await authApi.register({
      username: form.username,
      nickname: form.nickname,
      phone: form.phone,
      password: form.password
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    /* 错误已由拦截器提示 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
}
.auth-left {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: url('/login-bg.png') no-repeat center center / cover;
}
.auth-left::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.15) 0%, rgba(0, 0, 0, 0.5) 100%);
}
.brand {
  position: relative;
  text-align: center;
  color: #fff;
  padding: 0 40px;
}
.brand-logo {
  width: 96px;
  height: 96px;
  border-radius: 22px;
  background: #fff;
  object-fit: contain;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.28);
  margin-bottom: 24px;
  cursor: pointer;
  transition: box-shadow 0.3s ease, filter 0.3s ease;
  animation: logo-pop 0.7s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}
.brand-logo:hover {
  filter: brightness(1.08);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.4);
}
.brand h1 {
  font-size: 30px;
  font-weight: 700;
  margin: 0 0 12px;
  letter-spacing: 1px;
  animation: fade-up 0.7s ease 0.15s both;
}
.brand p {
  font-size: 15px;
  opacity: 0.92;
  letter-spacing: 2px;
  animation: fade-up 0.7s ease 0.25s both;
}
.auth-right {
  width: 520px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  padding: 40px 65px;
}
.auth-card {
  --el-color-primary: #188e8d;
  width: 100%;
  background: #fff;
  border-radius: 12px;
  padding: 30px 28px 22px;
  box-shadow: 0 8px 30px rgba(0, 21, 41, 0.12);
  animation: card-fly-in 0.9s cubic-bezier(0.16, 1, 0.3, 1) 0.1s both;
}
.auth-title {
  text-align: center;
  margin-bottom: 24px;
}
.title-logo {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  object-fit: contain;
  margin-bottom: 12px;
}
.auth-title h2 {
  font-size: 20px;
  margin: 0 0 6px;
  color: #303133;
}
.auth-title p {
  color: #909399;
  font-size: 13px;
  margin: 0;
}
.submit-btn {
  width: 100%;
  background-color: #a0d8d0;
  border-color: #a0d8d0;
  color: #303133;
}
.submit-btn:hover,
.submit-btn:focus {
  background-color: #8cc9bf;
  border-color: #8cc9bf;
  color: #303133;
}
.auth-links {
  text-align: center;
  font-size: 14px;
  color: #606266;
  margin-top: 4px;
}
.auth-link {
  --el-link-text-color: #188e8d;
  --el-link-hover-text-color: #13807f;
}
@keyframes logo-pop {
  0% {
    opacity: 0;
    transform: scale(0);
  }
  70% {
    transform: scale(1.08);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}
@keyframes fade-up {
  0% {
    opacity: 0;
    transform: translateY(20px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes card-fly-in {
  0% {
    opacity: 0;
    transform: translateX(-50vw) scale(0.3);
  }
  60% {
    opacity: 1;
  }
  100% {
    opacity: 1;
    transform: translateX(0) scale(1);
  }
}
@media (max-width: 768px) {
  .auth-left {
    display: none;
  }
  .auth-right {
    width: 100%;
  }
}
</style>
