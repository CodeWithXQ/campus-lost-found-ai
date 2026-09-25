/**
 * 通用工具
 */

/** 帖子图片完整地址 */
export function imgUrl(path) {
  if (!path) return ''
  const p = String(path).trim()
  if (/^https?:\/\//i.test(p)) return p // 完整网络 URL（http/https）
  if (p.startsWith('//')) return 'https:' + p // 协议相对 URL
  if (p.startsWith('data:')) return p // base64 data URI
  return '/uploads/' + p // 本地相对路径
}

/** 图片列表 */
export function imgList(urls) {
  if (!urls) return []
  return urls.split(',').filter(Boolean).map((p) => imgUrl(p.trim()))
}

/** 分数 → 百分比展示（保留1位小数） */
export function pct(v) {
  if (v === null || v === undefined) return '-'
  return (v * 100).toFixed(1) + '%'
}

/** 帖子类型文案 */
export function typeText(type) {
  return type === 'LOST' ? '失物' : '招领'
}

/** 帖子状态文案 */
export function statusText(status, type) {
  if (status === 0) return type === 'FOUND' ? '待认领' : '寻找中'
  if (status === 1) return '已认领归档'
  if (status === 2) return '已下架'
  if (status === 3) return '待审核'
  if (status === 4) return '审核未通过'
  return '未知'
}

/** AI 初审核结论文案 */
export function aiAuditText(result) {
  const map = { PASS: 'AI 通过', WARN: 'AI 预警', REJECT: 'AI 已拦截' }
  return map[result] || ''
}

/** AI 初审核结论标签类型 */
export function aiAuditType(result) {
  const map = { PASS: 'success', WARN: 'warning', REJECT: 'danger' }
  return map[result] || 'info'
}

/** 匹配记录状态文案 */
export function matchStatusText(s) {
  const map = {
    NEW: '新匹配',
    CONTACTED: '已联系',
    CLAIMED: '已认领',
    IGNORED: '已忽略'
  }
  return map[s] || s
}

/** 认领状态文案 */
export function claimStatusText(s) {
  const map = {
    PENDING: '待确认',
    CONFIRMED: '已确认',
    REJECTED: '已拒绝'
  }
  return map[s] || s
}

/** 时间格式化 */
export function fmtTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}
