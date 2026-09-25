import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/register', component: () => import('../views/Register.vue') },
  {
    path: '/',
    component: MainLayout,
    redirect: '/home',
    children: [
      { path: 'home', name: 'Home', component: () => import('../views/Home.vue'), meta: { title: '信息广场' } },
      { path: 'publish', name: 'Publish', component: () => import('../views/Publish.vue'), meta: { title: '发布信息', auth: true } },
      { path: 'post/:id', name: 'PostDetail', component: () => import('../views/PostDetail.vue'), meta: { title: '帖子详情' } },
      { path: 'myposts', name: 'MyPosts', component: () => import('../views/MyPosts.vue'), meta: { title: '我的发布', auth: true } },
      { path: 'claims', name: 'Claims', component: () => import('../views/Claims.vue'), meta: { title: '认领管理', auth: true } },
      { path: 'claim/:id', name: 'ClaimDetail', component: () => import('../views/ClaimDetail.vue'), meta: { title: '认领详情', auth: true } },
      { path: 'messages', name: 'Messages', component: () => import('../views/Messages.vue'), meta: { title: '消息通知', auth: true } },
      { path: 'chat', name: 'ChatList', component: () => import('../views/ChatList.vue'), meta: { title: '私聊', auth: true } },
      { path: 'chat/:peerId', name: 'Chat', component: () => import('../views/Chat.vue'), meta: { title: '聊天', auth: true } },
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '数据看板', auth: true, admin: true } },
      { path: 'admin/posts', name: 'AdminPosts', component: () => import('../views/AdminPosts.vue'), meta: { title: '帖子管理', auth: true, admin: true } },
      { path: 'admin/users', name: 'AdminUsers', component: () => import('../views/AdminUsers.vue'), meta: { title: '用户管理', auth: true, admin: true } },
      { path: 'profile', name: 'Profile', component: () => import('../views/Profile.vue'), meta: { title: '个人中心', auth: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  if (to.meta.auth && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && user?.role !== 'ADMIN') {
    return '/home'
  }
  if ((to.path === '/login' || to.path === '/register') && token) {
    return '/home'
  }
  document.title = (to.meta.title ? to.meta.title + ' - ' : '') + '校园失物招领智能匹配系统'
})

export default router
