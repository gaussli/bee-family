#!/usr/bin/env node
/**
 * 单点探针：隔离 D6「恶意上传后 album.photo_count 减少」与 E3「并发打标签丢关联」两个异常，
 * 便于在写测试报告前拿到可复现的最小证据链。
 *
 * 运行：
 *   BASE_URL=http://127.0.0.1:18081 DB_NAME=bee_family_e2e node docs/probe-d6.mjs
 */

import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'

const BASE = process.env.BASE_URL ?? 'http://127.0.0.1:18081'
const DB_NAME = process.env.DB_NAME ?? 'bee_family_e2e'
const DB_USER = process.env.DB_USERNAME ?? 'root'
const DB_PASSWORD = process.env.DB_PASSWORD
if (!DB_PASSWORD) {
  console.error('缺少 DB_PASSWORD。用法：DB_PASSWORD=<本机 MySQL 口令> node docs/probe-d6.mjs')
  process.exit(1)
}
const IMAGE_PATH = process.env.IMAGE_PATH ?? 'docs/screenshots/01-login-1440.png'

function sql(query) {
  if (/\b(undefined|NaN)\b/.test(query)) {
    return []
  }
  try {
    return execFileSync('mysql', [`-u${DB_USER}`, DB_NAME, '-N', '-B', '-e', query],
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
  return { status: response.status, body: parsed, raw: text }
}

async function upload(token, albumId, files) {
  const form = new FormData()
  for (const file of files) {
    form.append('files', new Blob([file.buffer], { type: file.type }), file.name)
  }
  return call('POST', `/api/photos/upload?albumId=${albumId}`, { token, form })
}

const image = readFileSync(IMAGE_PATH)
const stamp = Date.now()
const reg = await call('POST', '/api/auth/register', {
  json: { email: `probe-${stamp}@home.local`, password: 'probe-secret-123', nickname: '探针' },
})
const token = reg.body?.data?.token
console.log(`register: status=${reg.status} code=${reg.body?.code} role=${reg.body?.data?.role}`)
if (!token) {
  process.exit(1)
}

const album = await call('POST', '/api/albums', { token, json: { name: `探针-${stamp}` } })
const albumId = album.body?.data?.id
console.log(`album: id=${albumId}`)

const count = (label) => {
  const value = sql(`select photo_count from album where id = ${albumId}`)[0]
  const actual = sql(`select count(*) from photo where album_id = ${albumId}`)[0]
  console.log(`  ${label}: photo_count=${value} 实际行数=${actual}`)
  return Number(value)
}

await upload(token, albumId, [
  { name: 'g1.png', buffer: image, type: 'image/png' },
  { name: 'g2.png', buffer: image, type: 'image/png' },
  { name: 'g3.png', buffer: image, type: 'image/png' },
])
count('3 张正常上传后')

const evilText = Buffer.from('这不是图片，只是把扩展名改成 .jpg 的文本', 'utf8')
const svgBytes = Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>', 'utf8')
const heicBytes = Buffer.concat([Buffer.from([0x00, 0x00, 0x00, 0x18]), Buffer.from('ftypheic'), Buffer.alloc(64)])
const mixed = await upload(token, albumId, [
  { name: 'fake.jpg', buffer: evilText, type: 'image/jpeg' },
  { name: 'logo.svg', buffer: svgBytes, type: 'image/svg+xml' },
  { name: 'IMG_0001.HEIC', buffer: heicBytes, type: 'image/heic' },
])
console.log(`  混合恶意上传（不含超大文件）: success=${mixed.body?.data?.successCount} `
  + `failures=${(mixed.body?.data?.failures ?? []).map((f) => `${f.fileName}:${f.code}`).join(',')}`)
count('3 个恶意文件被拒后')

const huge = Buffer.concat([image, Buffer.alloc(21 * 1024 * 1024 - image.length)])
const hugeResp = await upload(token, albumId, [{ name: 'huge.jpg', buffer: huge, type: 'image/jpeg' }])
console.log(`  21MB 上传: status=${hugeResp.status} success=${hugeResp.body?.data?.successCount} `
  + `failures=${(hugeResp.body?.data?.failures ?? []).map((f) => `${f.fileName}:${f.code}`).join(',')}`)
count('21MB 被拒后')

/* ---------------------------------------------------------------- E3 隔离 */

const tagAlbum = (await call('POST', '/api/albums', { token, json: { name: `探针标签-${stamp}` } })).body?.data?.id
const tagUpload = await upload(token, tagAlbum, Array.from({ length: 10 }, (_, index) => ({
  name: `tag-${index}.png`, buffer: image, type: 'image/png',
})))
const tagPhotoIds = (tagUpload.body?.data?.successes ?? []).map((photo) => photo.id)
console.log(`\n并发打标签：${tagPhotoIds.length} 张照片，tag='并发标签'`)
const tagResponses = await Promise.all(tagPhotoIds.map((photoId) => call('PATCH', `/api/photos/${photoId}`, {
  token, json: { title: null, description: null, tags: ['并发标签'] },
})))
for (const [index, response] of tagResponses.entries()) {
  console.log(`  photo ${tagPhotoIds[index]}: status=${response.status} code=${response.body?.code} `
    + `msg=${String(response.body?.message ?? '').slice(0, 80)}`)
}
console.log(`  tag 行数=${sql(`select count(*) from tag where name = '并发标签'`)[0]} `
  + `photo_tag 关联=${sql(`select count(*) from photo_tag pt join tag t on t.id = pt.tag_id where t.name = '并发标签'`)[0]}`)

const familyId = reg.body?.data?.familyId
console.log(`\n清理 family=${familyId}`)
const family = await call('GET', '/api/family', { token })
const deleted = await call('DELETE', '/api/family', { token, json: { confirmName: family.body?.data?.name } })
console.log(`  DELETE /api/family: status=${deleted.status} code=${deleted.body?.code}`)
sql(`delete from \`user\` where email = 'probe-${stamp}@home.local'`)
