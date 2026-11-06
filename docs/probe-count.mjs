#!/usr/bin/env node
/**
 * 定点探针 2：定位「album.photo_count 从 3 变成 2」到底发生在哪一步。
 *
 * 依次执行 A7→A11 之间的每个动作，并在每步后打印 album.photo_count 与 photo 行数。
 *
 * 运行：
 *   BASE_URL=http://127.0.0.1:18081 DB_NAME=bee_family_e2e node docs/probe-count.mjs
 */

import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'

const BASE = process.env.BASE_URL ?? 'http://127.0.0.1:18081'
const DB_NAME = process.env.DB_NAME ?? 'bee_family_e2e'
const IMAGE_PATH = process.env.IMAGE_PATH ?? 'docs/screenshots/01-login-1440.png'
const image = readFileSync(IMAGE_PATH)

const DB_PASSWORD = process.env.DB_PASSWORD
if (!DB_PASSWORD) {
  console.error('缺少 DB_PASSWORD。用法：DB_PASSWORD=<本机 MySQL 口令> node docs/probe-count.mjs')
  process.exit(1)
}

function sql(query) {
  try {
    return execFileSync('mysql', ['-uroot', DB_NAME, '-N', '-B', '-e', query],
      { env: { ...process.env, MYSQL_PWD: DB_PASSWORD }, encoding: 'utf8' })
      .split('\n').filter((line) => line.length > 0)
  } catch (error) {
    console.error(`  [sql] ${String(error.message).split('\n')[0]}`)
    return []
  }
}

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

const stamp = Date.now()
const reg = await call('POST', '/api/auth/register', {
  json: { email: `probe2-${stamp}@home.local`, password: 'probe-secret-123', nickname: '探针2' },
})
const token = reg.body?.data?.token
if (!token) {
  console.error(`register failed: ${reg.status} ${JSON.stringify(reg.body)}`)
  process.exit(1)
}
const babyId = (await call('POST', '/api/babies', {
  token, json: { name: '团团', gender: 'female', birthday: '2025-08-18' },
})).body?.data?.id
const albumId = (await call('POST', '/api/albums', { token, json: { name: `探针2-${stamp}` } })).body?.data?.id

const form = new FormData()
for (const name of ['p1.png', 'p2.png', 'p3.png']) {
  form.append('files', new Blob([image], { type: 'image/png' }), name)
}
const up = await call('POST', `/api/photos/upload?albumId=${albumId}`, { token, form })
const photoId = up.body?.data?.successes?.[0]?.id

const snap = (label) => {
  const count = sql(`select photo_count from album where id = ${albumId}`)[0]
  const rows = sql(`select count(*) from photo where album_id = ${albumId}`)[0]
  const detail = sql(`select id, ifnull(album_id, 'NULL') from photo order by id`).join(' ')
  console.log(`  ${label}: photo_count=${count} 相册内行数=${rows} 全部照片(id,album_id)=[${detail}]`)
}

snap('上传 3 张后')
const tag1 = await call('PATCH', `/api/photos/${photoId}`, {
  token, json: { title: '海边的一天', description: '第一次看海', locationName: '青岛', tags: ['海边', '第一次', '海边'] },
})
console.log(`  PATCH photo #1: status=${tag1.status}`)
snap('PATCH 照片（打标签）后')
const tag2 = await call('PATCH', `/api/photos/${photoId}`, {
  token, json: { title: '海边的一天', description: '第一次看海', locationName: '青岛', tags: ['海边'] },
})
console.log(`  PATCH photo #2: status=${tag2.status}`)
snap('第二次 PATCH 后')
await call('GET', '/api/tags', { token })
snap('GET /api/tags 后')
const ms = await call('POST', '/api/milestones', {
  token, json: { babyId, title: '第一次走路', description: '扶着沙发走了三步', achievedAt: '2026-10-05', photoId },
})
console.log(`  POST milestone: status=${ms.status} code=${ms.body?.code}`)
snap('建里程碑（带 photoId）后')
const ms2 = await call('POST', '/api/milestones', {
  token, json: { babyId, title: '第一颗牙', achievedAt: '2026-09-01' },
})
console.log(`  POST milestone（无 photoId）: status=${ms2.status}`)
snap('再建一个无配图里程碑后')

const family = await call('GET', '/api/family', { token })
await call('DELETE', '/api/family', { token, json: { confirmName: family.body?.data?.name } })
sql(`delete from \`user\` where email = 'probe2-${stamp}@home.local'`)
console.log('  清理完成')
