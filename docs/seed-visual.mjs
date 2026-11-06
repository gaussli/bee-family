#!/usr/bin/env node
/**
 * 视觉回归用的可复现数据集。
 *
 * 刻意**不碰**演示库 bee_family（那是产品给用户看的样例数据），
 * 而是在独立的空库上自建账号 + 宝宝 + 相册 + 照片 + 里程碑 + 成长记录，
 * 让截图验证可以随时重跑、凭据固定、数据量可控。
 *
 * 前置：后端跑在 8080（前端 vite 代理的默认目标），库为空（user 表 0 行）。
 * 运行：node docs/seed-visual.mjs
 */

import { readFileSync } from 'node:fs'

const BASE = process.env.BASE_URL ?? 'http://127.0.0.1:8080'
const EMAIL = process.env.SEED_EMAIL ?? 'qa-visual@home.local'
const PASSWORD = process.env.SEED_PASSWORD
if (!PASSWORD) {
  console.error('缺少 SEED_PASSWORD。用法：SEED_PASSWORD=<自定演示账号口令> node docs/seed-visual.mjs')
  process.exit(1)
}
const NICKNAME = process.env.SEED_NICKNAME ?? '验收妈妈'

const PHOTO_POOL = [
  'docs/screenshots/02-timeline-1440.png',
  'docs/screenshots/03-albums-1440.png',
  'docs/screenshots/07-timeline-milestone-1440.png',
  'docs/screenshots/09-upload-panel-1440.png',
  'docs/screenshots/10-batch-select-1440.png',
  'docs/screenshots/13-family-1440.png',
  'docs/screenshots/15-settings-1440.png',
  'docs/screenshots/17-baby-1440.png',
  'docs/screenshots/01-login-1440.png',
  'docs/screenshots/11-search-1440.png',
  'docs/screenshots/04-album-detail-1440.png',
  'docs/screenshots/05-growth-1440.png',
]

async function call(method, path, options = {}) {
  const headers = {}
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`
  }
  let body
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.json)
  } else if (options.form) {
    body = options.form
  }
  const response = await fetch(`${BASE}${path}`, { method, headers, body })
  const text = await response.text()
  let parsed = null
  try {
    parsed = text ? JSON.parse(text) : null
  } catch {
    parsed = null
  }
  return { status: response.status, body: parsed }
}

async function upload(token, albumId, names) {
  const form = new FormData()
  for (const [index, name] of names.entries()) {
    const buffer = readFileSync(PHOTO_POOL[index % PHOTO_POOL.length])
    form.append('files', new Blob([buffer], { type: 'image/png' }), `${name}.png`)
  }
  return call('POST', `/api/photos/upload?albumId=${albumId}`, { token, form })
}

const register = await call('POST', '/api/auth/register', {
  json: { email: EMAIL, password: PASSWORD, nickname: NICKNAME },
})
if (register.status !== 200) {
  console.error(`注册失败：${register.status} ${JSON.stringify(register.body)}`)
  process.exit(1)
}
const token = register.body.data.token
console.log(`账号：${EMAIL} / ${PASSWORD}（role=${register.body.data.role}）`)

const baby = await call('POST', '/api/babies', {
  token,
  json: { name: '团团', gender: 'female', birthday: '2025-08-18', birthHeightCm: 50.0, birthWeightKg: 3.3 },
})
const babyId = baby.body?.data?.id
console.log(`宝宝 id=${babyId} ageText=${baby.body?.data?.ageText}`)

const summer = await call('POST', '/api/albums', {
  token, json: { name: '2026 夏天', description: '海边的两个月' },
})
const summerId = summer.body?.data?.id
const daily = await call('POST', '/api/albums', { token, json: { name: '日常', description: '随手拍' } })
const dailyId = daily.body?.data?.id

const summerUpload = await upload(token, summerId, Array.from({ length: 15 }, (_, index) => `summer-${index + 1}`))
const dailyUpload = await upload(token, dailyId, ['daily-1', 'daily-2', 'daily-3'])
console.log(`相册 ${summerId} 上传 ${summerUpload.body?.data?.successCount} 张；`
  + `相册 ${dailyId} 上传 ${dailyUpload.body?.data?.successCount} 张`)

const firstPhotoId = summerUpload.body?.data?.successes?.[0]?.id
await call('PATCH', `/api/photos/${firstPhotoId}`, {
  token,
  json: {
    title: '第一次看海',
    description: '在青岛，她被浪花吓了一跳',
    locationName: '青岛',
    tags: ['海边', '第一次'],
    albumId: summerId,
  },
})

for (const [index, title] of ['第一次走路', '第一颗牙', '会叫妈妈了'].entries()) {
  await call('POST', '/api/milestones', {
    token,
    json: {
      babyId,
      title,
      description: index === 0 ? '扶着沙发走了三步' : null,
      achievedAt: `2026-0${index + 8}-1${index + 1}`,
      photoId: index === 0 ? firstPhotoId : null,
    },
  })
}

for (const [index, recordedAt] of ['2025-11-05', '2026-02-05', '2026-05-05', '2026-08-05'].entries()) {
  await call('POST', `/api/babies/${babyId}/growth-records`, {
    token,
    json: {
      recordedAt,
      heightCm: 62 + index * 7,
      weightKg: 6.4 + index * 1.1,
      headCm: 41 + index * 1.2,
      note: index === 3 ? '社区体检' : null,
    },
  })
}

const stats = await call('GET', '/api/stats/overview', { token })
console.log(`概览：${JSON.stringify(stats.body?.data)}`)
