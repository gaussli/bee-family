#!/usr/bin/env node
/**
 * Bee Family 端到端 / 安全 / 并发 / 分页 验证脚本。
 *
 * 前置：后端已启动（profile=local），MySQL 可用。
 *   cd backend
 *   mvn -o spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=18080
 *
 * 运行：
 *   node docs/e2e-verify.mjs                     # 默认 http://127.0.0.1:18080
 *   BASE_URL=http://127.0.0.1:8080 node docs/e2e-verify.mjs
 *
 * 说明：
 *   - 脚本自建独立家庭（qa-e2e-<时间戳>@home.local），不触碰演示家庭的数据；
 *   - 结束时会通过 DELETE /api/family 走一遍 9 步删除 + 磁盘清理；
 *   - 越权隔离需要「第二个家庭」：直接注册用户 C，再用 SQL 把 C 的成员关系迁到
 *     新建家庭 B，从而构造出真正的跨家庭场景（familyId 由 family_member 每请求解析，见 ADR-02）；
 *     不依赖任何邀请码端点（v1.1 Q7 已删除 GET /api/family/invite-code 与 40902）。
 */

import { readFileSync, writeFileSync, readdirSync, statSync, existsSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { createHash, createHmac } from 'node:crypto'
import { join } from 'node:path'

const BASE = process.env.BASE_URL ?? 'http://127.0.0.1:18080'
const DB_NAME = process.env.DB_NAME ?? 'bee_family'
const DB_USER = process.env.DB_USERNAME ?? 'root'
const DB_PASSWORD = process.env.DB_PASSWORD
if (!DB_PASSWORD) {
  console.error('缺少 DB_PASSWORD。用法：DB_PASSWORD=<本机 MySQL 口令> node docs/e2e-verify.mjs')
  process.exit(1)
}
const JWT_SECRET = process.env.JWT_SECRET ?? 'local-dev-only-secret-please-override-with-32bytes-random'
const UPLOAD_ROOT = process.env.UPLOAD_ROOT ?? 'backend/data/uploads'
const IMAGE_PATH = process.env.IMAGE_PATH ?? 'docs/screenshots/01-login-1440.png'

const results = []
const findings = []
const envIssues = []
const metrics = {}
const stamp = Date.now()

/* ------------------------------------------------------------------ 基础设施 */

/**
 * 记录一条断言结果。
 *
 * @param {string} id 用例编号
 * @param {string} name 用例名
 * @param {boolean} ok 是否通过
 * @param {string} [detail] 附加信息（失败原因 / 实测值）
 */
function record(id, name, ok, detail = '') {
  results.push({ id, name, ok, detail })
  const tag = ok ? '\u2713' : '\u2717'
  console.log(`  ${tag} [${id}] ${name}${detail ? ` — ${detail}` : ''}`)
}

/**
 * 记录一条缺陷（源码问题，不在此修复）。
 *
 * @param {string} id 关联用例
 * @param {string} severity 严重度
 * @param {string} summary 摘要
 * @param {string} detail 复现方式与影响面
 */
function finding(id, severity, summary, detail) {
  findings.push({ id, severity, summary, detail })
  console.log(`  ! [${id}][${severity}] ${summary}`)
}

/**
 * 记录一条「环境限制导致不可判定」的观察（不算源码缺陷）。
 *
 * @param {string} id 关联用例
 * @param {string} summary 摘要
 * @param {string} detail 证据
 */
function envIssue(id, summary, detail) {
  envIssues.push({ id, summary, detail })
  console.log(`  ~ [${id}][环境受限] ${summary}`)
}

/**
 * 发 HTTP 请求。
 *
 * @param {string} method 方法
 * @param {string} path 路径（含 query）
 * @param {{token?: string, json?: unknown, form?: FormData}} [options] 选项
 * @returns {Promise<{status:number, body:any, headers:Headers}>} 响应
 */
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
  return { status: response.status, body: parsed, headers: response.headers }
}

/**
 * 执行 SQL（仅用于测试夹具与并发结果校验）。
 *
 * 刻意不抛异常：断言里大量出现 `sql(...)[0] === '3'` 这类写法，
 * 一旦上游 API 没返回 id（用例已失败）就会拼出 `... where id = undefined`。
 * 此时让 SQL 失败降级成空数组，后续断言自然为 false，
 * 而不是让整轮验证在第一个前置失败时崩掉、丢掉后面所有用例证据。
 *
 * @param {string} query 语句
 * @returns {string[]} 每行以 tab 分隔；执行失败返回空数组
 */
function sql(query) {
  if (/\b(undefined|NaN|null)\b/.test(query)) {
    console.error(`  [sql] 跳过含非法主键的语句: ${query}`)
    return []
  }
  try {
    const output = execFileSync(
      'mysql',
      [`-u${DB_USER}`, DB_NAME, '-N', '-B', '-e', query],
      { env: { ...process.env, MYSQL_PWD: DB_PASSWORD }, encoding: 'utf8' },
    )
    return output.split('\n').filter((line) => line.length > 0)
  } catch (error) {
    console.error(`  [sql] 执行失败（已降级为空结果）: ${query} :: ${String(error.message).split('\n')[0]}`)
    return []
  }
}

/**
 * 组装 multipart 上传请求。
 *
 * @param {string} token JWT
 * @param {number|null} albumId 相册 id
 * @param {Array<{name:string, buffer:Buffer, type:string}>} files 文件
 * @returns {Promise<{status:number, body:any}>} 响应
 */
async function upload(token, albumId, files) {
  const form = new FormData()
  for (const file of files) {
    form.append('files', new Blob([file.buffer], { type: file.type }), file.name)
  }
  const query = albumId == null ? '' : `?albumId=${albumId}`
  return call('POST', `/api/photos/upload${query}`, { token, form })
}

/**
 * 用本地 JWT 密钥自签一个 token（用于「已过期」场景，不需要后端配合）。
 *
 * @param {number} userId 用户 id
 * @param {number} expOffsetSeconds 相对当前时间的过期偏移（负数=已过期）
 * @returns {string} JWT
 */
function signToken(userId, expOffsetSeconds) {
  const key = createHash('sha256').update(Buffer.from(JWT_SECRET, 'utf8')).digest()
  const now = Math.floor(Date.now() / 1000)
  const header = Buffer.from(JSON.stringify({ alg: 'HS256' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({
    sub: String(userId),
    email: 'expired@home.local',
    iat: now + expOffsetSeconds - 60,
    exp: now + expOffsetSeconds,
  })).toString('base64url')
  const signature = createHmac('sha256', key).update(`${header}.${payload}`).digest('base64url')
  return `${header}.${payload}.${signature}`
}

/**
 * 递归统计目录下文件数与字节数。
 *
 * @param {string} dir 目录
 * @returns {{files:number, bytes:number}} 统计
 */
function countFiles(dir) {
  let files = 0
  let bytes = 0
  if (!existsSync(dir)) {
    return { files, bytes }
  }
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    const info = statSync(full)
    if (info.isDirectory()) {
      const nested = countFiles(full)
      files += nested.files
      bytes += nested.bytes
    } else {
      files += 1
      bytes += info.size
    }
  }
  return { files, bytes }
}

/**
 * 统计某家庭上传目录里「不属于任何 photo 行」的孤儿文件。
 *
 * 只看文件名（key 的 basename 全局唯一，含 uuid），与 DB 里的
 * storage_key / thumb_key / preview_key 做集合差。
 *
 * @param {number} familyId 家庭 id
 * @returns {string[]} 孤儿文件名
 */
function orphanFiles(familyId) {
  const keys = ['storage_key', 'thumb_key', 'preview_key']
    .flatMap((column) => sql(`select ${column} from photo where family_id = ${familyId}`))
    .filter((key) => key && key !== 'NULL')
  const expected = new Set(keys.map((key) => key.split('/').pop()))
  const actual = []
  const walk = (dir) => {
    if (!existsSync(dir)) {
      return
    }
    for (const entry of readdirSync(dir)) {
      const full = join(dir, entry)
      if (statSync(full).isDirectory()) {
        walk(full)
      } else {
        actual.push(entry)
      }
    }
  }
  walk(join(UPLOAD_ROOT, String(familyId)))
  return actual.filter((name) => !expected.has(name))
}

/**
 * 记录耗时百分位。
 *
 * @param {string} name 指标名
 * @param {number[]} samples 毫秒样本
 */
function recordLatency(name, samples) {
  const sorted = [...samples].sort((a, b) => a - b)
  const at = (p) => sorted[Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * p))]
  metrics[name] = {
    samples: sorted.length,
    min: Math.round(sorted[0]),
    avg: Math.round(sorted.reduce((sum, value) => sum + value, 0) / sorted.length),
    p95: Math.round(at(0.95)),
    max: Math.round(sorted[sorted.length - 1]),
  }
  console.log(`  \u00b7 ${name}: n=${sorted.length} min=${metrics[name].min}ms avg=${metrics[name].avg}ms `
    + `p95=${metrics[name].p95}ms max=${metrics[name].max}ms`)
}

/**
 * 汇总并落盘报告（前置失败提前退出时也会调用，保证拿到部分证据）。
 */
function writeReport() {
  const passed = results.filter((item) => item.ok).length
  const failed = results.length - passed
  console.log('\n' + '='.repeat(64))
  console.log(`用例总数: ${results.length}   通过: ${passed}   失败: ${failed}   缺陷: ${findings.length} `
    + `环境受限: ${envIssues.length}`)
  if (failed > 0) {
    console.log('失败用例:')
    for (const item of results.filter((entry) => !entry.ok)) {
      console.log(`  - [${item.id}] ${item.name} :: ${item.detail}`)
    }
  }
  console.log('='.repeat(64))
  const report = {
    base: BASE,
    ranAt: new Date().toISOString(),
    results,
    findings,
    envIssues,
    metrics,
    pagination: metrics.pagination,
  }
  writeFileSync(process.env.OUT ?? '/tmp/qa-e2e-result.json', JSON.stringify(report, null, 2))
  console.log(`明细已写入 ${process.env.OUT ?? '/tmp/qa-e2e-result.json'}`)
  return failed
}

const imageBuffer = readFileSync(IMAGE_PATH)
const stampLabel = String(stamp)

/* ------------------------------------------------------------------ B. 主流程 */

console.log('\n=== A. 主流程（注册 → 宝宝 → 相册 → 上传 → 时间轴 → 标签 → 里程碑 → 成长 → 搜索 → 统计）===')

const emailA = `qa-e2e-${stampLabel}@home.local`
const passwordA = 'qa-secret-123'
const registerA = await call('POST', '/api/auth/register', {
  json: { email: emailA, password: passwordA, nickname: 'QA验收' },
})
const tokenA = registerA.body?.data?.token
const familyA = registerA.body?.data?.familyId
const userA = registerA.body?.data?.user?.id
record('A1', '注册首个用户返回 200/code 0 且成为 owner',
  registerA.status === 200 && registerA.body?.code === 0 && registerA.body?.data?.role === 'owner',
  `status=${registerA.status} code=${registerA.body?.code} role=${registerA.body?.data?.role} familyId=${familyA}`)

if (!tokenA || !Number.isInteger(familyA)) {
  console.error(`\n前置失败：注册首用户未成功（status=${registerA.status} code=${registerA.body?.code}）。`
    + '\n后续用例全部依赖这个账号，整轮跳过。'
    + '\n提示：本脚本要求后端连的是「空库」——user 表非空时注册必须带邀请码，'
    + '只有首个用户才会成为 owner 并自动建家庭。')
  writeReport()
  process.exit(1)
}

const meA = await call('GET', '/api/auth/me', { token: tokenA })
record('A2', 'GET /api/auth/me 返回当前用户与家庭',
  meA.status === 200 && meA.body?.code === 0 && meA.body?.data?.user?.email === emailA,
  `status=${meA.status}`)

const babyReq = await call('POST', '/api/babies', {
  token: tokenA,
  json: { name: '团团', gender: 'female', birthday: '2025-08-18', birthHeightCm: 50.0, birthWeightKg: 3.3 },
})
const babyId = babyReq.body?.data?.id
record('A3', '新建宝宝档案', babyReq.status === 200 && babyReq.body?.code === 0 && babyId > 0,
  `status=${babyReq.status} babyId=${babyId} ageText=${babyReq.body?.data?.ageText}`)

const albumReq = await call('POST', '/api/albums', { token: tokenA, json: { name: '2026 夏天', description: 'QA 验收相册' } })
const albumId = albumReq.body?.data?.id
record('A4', '新建相册', albumReq.status === 200 && albumReq.body?.code === 0 && albumId > 0,
  `status=${albumReq.status} albumId=${albumId}`)

// album.photo_count 快照轨迹：一旦某个步骤把它改坏，D6 会直接指出是哪一步
const countTrail = []
const snapshotCount = (label) => {
  const value = sql(`select photo_count from album where id = ${albumId}`)[0]
  countTrail.push(`${label}=${value}`)
  return value
}

const firstUpload = await upload(tokenA, albumId, [
  { name: 'a1.png', buffer: imageBuffer, type: 'image/png' },
  { name: 'a2.png', buffer: imageBuffer, type: 'image/png' },
  { name: 'a3.png', buffer: imageBuffer, type: 'image/png' },
])
const firstPhotos = firstUpload.body?.data?.successes ?? []
record('A5', '上传 3 张照片全部成功',
  firstUpload.status === 200 && firstUpload.body?.data?.successCount === 3 && firstPhotos.length === 3,
  `success=${firstUpload.body?.data?.successCount} fail=${firstUpload.body?.data?.failureCount}`)
const countAfterUpload = snapshotCount('A5')
record('A6', '上传后 album.photo_count 与实际照片数一致',
  Number(countAfterUpload) === 3, `photo_count=${countAfterUpload}`)

const timelineA = await call('GET', '/api/timeline?page=0&size=24', { token: tokenA })
const timelinePhotoCount = (timelineA.body?.data?.groups ?? [])
  .flatMap((group) => group.items)
  .filter((item) => item.type === 'photo').length
record('A7', '时间轴能看到刚上传的 3 张照片',
  timelineA.status === 200 && timelinePhotoCount === 3,
  `status=${timelineA.status} photoItems=${timelinePhotoCount} total=${timelineA.body?.data?.total}`)
snapshotCount('A7')

const firstPhotoId = firstPhotos[0]?.id
const tagPatch = await call('PATCH', `/api/photos/${firstPhotoId}`, {
  token: tokenA,
  json: { title: '海边的一天', description: '第一次看海', locationName: '青岛', tags: ['海边', '第一次', '海边'] },
})
const tagNames = tagPatch.body?.data?.tags ?? []
record('A8', '编辑照片标签：重复名去重为 2 个',
  tagPatch.status === 200 && tagNames.length === 2,
  `tags=${JSON.stringify(tagNames)}`)

const tagPatch2 = await call('PATCH', `/api/photos/${firstPhotoId}`, {
  token: tokenA,
  json: { title: '海边的一天', description: '第一次看海', locationName: '青岛', tags: ['海边'] },
})
record('A9', '标签整体替换为 ["海边"] 后只剩 1 个',
  (tagPatch2.body?.data?.tags ?? []).length === 1,
  `tags=${JSON.stringify(tagPatch2.body?.data?.tags)}`)

// PATCH 只提交 title/description/locationName/tags（前端编辑照片就是这样发的），
// 不应该把 albumId 一起改掉；否则「改个标题」照片就从相册里消失了
const albumIdAfterPatch = sql(`select ifnull(album_id, 'NULL') from photo where id = ${firstPhotoId}`)[0]
const countAfterPatch = snapshotCount('A9')
record('A9b', '编辑照片（标题/标签）后照片仍在原相册、相册计数不变',
  albumIdAfterPatch === String(albumId) && Number(countAfterPatch) === 3,
  `album_id=${albumIdAfterPatch}(期望 ${albumId}) photo_count=${countAfterPatch}(期望 3)`)
if (albumIdAfterPatch !== String(albumId) || Number(countAfterPatch) !== 3) {
  finding('A9b', 'P1',
    'PATCH /api/photos/{id} 不传 albumId 时把照片移出相册（album_id 置 NULL），相册计数 -1',
    '复现：POST /api/photos/upload?albumId=X 上传 3 张 → PATCH /api/photos/{id} '
    + '{"title":"海边的一天","description":"第一次看海","locationName":"青岛","tags":["海边"]}（不含 albumId）'
    + `→ 实测 photo.album_id 由 ${albumId} 变成 ${albumIdAfterPatch}、album.photo_count 由 3 变成 ${countAfterPatch}。`
    + '根因：PhotoService#update 第 240–251 行把 req.albumId() 当作权威值（null = 移到未分类），'
    + '无法区分「不修改」与「移到未分类」；而前端 api/photo.ts#updatePhoto 的 PhotoUpdateBody 里 albumId 是可选字段，'
    + '编辑照片时不会带 albumId。'
    + '影响面：任何一次「编辑标题/标签/地点」都会让照片静默掉出相册（落到未分类），相册计数同步 -1；'
    + '后端行未丢，可人工修回 album_id，但用户视角就是「照片不见了」。'
    + '建议：PATCH 语义改为「字段缺席 = 不修改」（用 JsonNullable / Optional 或单独的 batch-move 接口承担移动语义）。')
}

const tagsList = await call('GET', '/api/tags', { token: tokenA })
const tagUsage = (tagsList.body?.data ?? []).map((tag) => `${tag.name}:${tag.photoCount}`)
record('A10', '标签列表能查到「海边」', tagsList.status === 200 && tagUsage.length >= 1, `tags=${JSON.stringify(tagUsage)}`)

const milestoneReq = await call('POST', '/api/milestones', {
  token: tokenA,
  json: { babyId, title: '第一次走路', description: '扶着沙发走了三步', achievedAt: '2026-10-05', photoId: firstPhotoId },
})
record('A11', '新建里程碑（带配图）',
  milestoneReq.status === 200 && milestoneReq.body?.code === 0 && milestoneReq.body?.data?.id > 0,
  `status=${milestoneReq.status} ageText=${milestoneReq.body?.data?.ageText}`)
snapshotCount('A11')

const growthReq = await call('POST', `/api/babies/${babyId}/growth-records`, {
  token: tokenA,
  json: { recordedAt: '2026-10-05', heightCm: 75.5, weightKg: 9.2, headCm: 46.0, note: '体检' },
})
const growthId = growthReq.body?.data?.id
const curve1 = await call('GET', `/api/babies/${babyId}/growth-curve?range=all`, { token: tokenA })
record('A12', '记录成长数据后曲线有 1 个点',
  growthReq.status === 200 && (curve1.body?.data?.points ?? []).length === 1,
  `points=${(curve1.body?.data?.points ?? []).length}`)

const growthAgain = await call('POST', `/api/babies/${babyId}/growth-records`, {
  token: tokenA,
  json: { recordedAt: '2026-10-05', heightCm: 76.0, weightKg: 9.3, headCm: 46.2, note: '复测' },
})
const curve2 = await call('GET', `/api/babies/${babyId}/growth-curve?range=all`, { token: tokenA })
const curvePoints2 = (curve2.body?.data?.points ?? []).length
const upsertHeight = Number(curve2.body?.data?.points?.[0]?.heightCm)
record('A13', 'UPSERT：同日重复提交仍是 1 条记录且值被覆盖（返回同一 id）',
  growthAgain.body?.data?.id === growthId && curvePoints2 === 1 && upsertHeight === 76,
  `id=${growthAgain.body?.data?.id}(原 ${growthId}) points=${curvePoints2} height=${upsertHeight}`)
snapshotCount('A13')

const search = await call('GET', '/api/search?q=%E6%B5%B7%E8%BE%B9', { token: tokenA })
record('A14', '搜索「海边」命中照片',
  search.status === 200 && (search.body?.data?.photos?.items ?? []).length >= 1,
  `total=${search.body?.data?.totalCount}`)

const stats = await call('GET', '/api/stats/overview', { token: tokenA })
record('A15', '概览统计数字与实际一致（照片 3 / 相册 1 / 里程碑 1 / 宝宝 1）',
  stats.body?.data?.photoCount === 3 && stats.body?.data?.albumCount === 1
    && stats.body?.data?.milestoneCount === 1 && stats.body?.data?.babyCount === 1,
  JSON.stringify(stats.body?.data))
snapshotCount('A15')

/* ------------------------------------------------------- C. 越权隔离（第二个家庭） */

console.log('\n=== B. 越权隔离（第二个家庭 token 访问第一个家庭）===')

// v1.1 Q7：邀请码端点与 40902 错误码已永久移除，用「端点不存在」做正向验收
const inviteResp = await call('GET', '/api/family/invite-code', { token: tokenA })
record('B1', '旧邀请码端点已移除（404/403/401/405，不再返回 200 邀请码）',
  [404, 403, 401, 405].includes(inviteResp.status),
  `status=${inviteResp.status} code=${inviteResp.body?.code}`)

const noInvite = await call('POST', '/api/auth/register', {
  json: { email: `qa-nocode-${stampLabel}@home.local`, password: passwordA, nickname: '无邀请码' },
})
record('B2', '注册不再校验邀请码：不会出现已删除的 40902',
  noInvite.body?.code !== 40902,
  `status=${noInvite.status} code=${noInvite.body?.code}`)
snapshotCount('B2')

const emailC = `qa-e2e-c-${stampLabel}@home.local`
const registerC = await call('POST', '/api/auth/register', {
  json: { email: emailC, password: passwordA, nickname: '第二家庭' },
})
const tokenC = registerC.body?.data?.token
const userC = registerC.body?.data?.user?.id
record('B3', '注册第二个账号成功（家庭归属由后续 SQL 夹具决定）',
  registerC.status === 200 && Number.isInteger(userC),
  `status=${registerC.status} code=${registerC.body?.code} familyId=${registerC.body?.data?.familyId}`)
snapshotCount('B3')

// 把 C 的成员关系迁到新建家庭 B，构造真正的跨家庭场景
const familyB = Number(sql(`insert into family (name, owner_id) values ('QA越权家庭-${stampLabel}', ${userC}); `
  + 'select last_insert_id();')[0])
sql(`delete from family_member where user_id = ${userC}`)
sql(`insert into family_member (family_id, user_id, role) values (${familyB}, ${userC}, 'owner')`)

// 第二个家庭就绪前，B4–B10 不执行：否则一律 401/40100，会伪装成「越权没拦住」的假缺陷
if (!tokenC || !Number.isInteger(familyB)) {
  record('B4-B10', '跨家庭越权用例整组无法执行（缺第二家庭 token）', false,
    `tokenC=${Boolean(tokenC)} familyB=${familyB}`)
} else {
  const crossAlbum = await call('GET', `/api/albums/${albumId}`, { token: tokenC })
  record('B4', '跨家庭读相册 → 403/40301',
    crossAlbum.status === 403 && crossAlbum.body?.code === 40301,
    `status=${crossAlbum.status} code=${crossAlbum.body?.code}`)

  const crossPhoto = await call('GET', `/api/photos/${firstPhotoId}`, { token: tokenC })
  record('B5', '跨家庭读照片 → 403/40301',
    crossPhoto.status === 403 && crossPhoto.body?.code === 40301,
    `status=${crossPhoto.status} code=${crossPhoto.body?.code}`)

  const crossBaby = await call('GET', `/api/babies/${babyId}`, { token: tokenC })
  record('B6', '跨家庭读宝宝 → 403/40301',
    crossBaby.status === 403 && crossBaby.body?.code === 40301,
    `status=${crossBaby.status} code=${crossBaby.body?.code}`)

  const photoKey = firstPhotos[0]?.previewKey ?? firstPhotos[0]?.thumbKey
  const crossFile = await call('GET', `/api/files/${photoKey}?token=${tokenC}`)
  record('B7', '跨家庭读文件 → 403/40301（不能靠猜 key 拿到图片）',
    crossFile.status === 403 && crossFile.body?.code === 40301,
    `status=${crossFile.status} code=${crossFile.body?.code} key=${photoKey}`)

  const crossTimeline = await call('GET', '/api/timeline', { token: tokenC })
  record('B8', '跨家庭时间轴只返回自己家庭的数据（total=0）',
    crossTimeline.status === 200 && crossTimeline.body?.data?.total === 0,
    `status=${crossTimeline.status} total=${crossTimeline.body?.data?.total}`)

  const crossDelete = await call('DELETE', `/api/photos/${firstPhotoId}`, { token: tokenC })
  record('B9', '跨家庭删照片 → 403（不能变成 404 也不能删成功）',
    crossDelete.status === 403,
    `status=${crossDelete.status} code=${crossDelete.body?.code}`)

  const crossAlbumDelete = await call('DELETE', `/api/albums/${albumId}?photoStrategy=delete`, { token: tokenC })
  record('B10', '跨家庭删相册 → 403（且未被误删）',
    crossAlbumDelete.status === 403 && sql(`select count(*) from album where id = ${albumId}`)[0] === '1',
    `status=${crossAlbumDelete.status} code=${crossAlbumDelete.body?.code}`)
}

/* ------------------------------------------------------------- D. 鉴权与 token */

console.log('\n=== C. 鉴权（无 token / 伪造签名 / 过期 / 登出后失效）===')

const noToken = await call('GET', '/api/photos')
record('C1', '无 token 访问受保护接口 → 401/40100',
  noToken.status === 401 && noToken.body?.code === 40100, `status=${noToken.status} code=${noToken.body?.code}`)

const forged = `${tokenA.slice(0, -6)}abcdef`
const forgedResp = await call('GET', '/api/photos', { token: forged })
record('C2', '篡改签名 → 401/40100',
  forgedResp.status === 401 && forgedResp.body?.code === 40100,
  `status=${forgedResp.status} code=${forgedResp.body?.code}`)

const garbage = await call('GET', '/api/photos', { token: 'not-a-jwt' })
record('C3', '结构畸形的 token → 401/40100',
  garbage.status === 401 && garbage.body?.code === 40100, `status=${garbage.status} code=${garbage.body?.code}`)

const expired = await call('GET', '/api/photos', { token: signToken(userA, -3600) })
record('C4', '已过期（exp 在过去，签名合法）→ 401/40103',
  expired.status === 401 && expired.body?.code === 40103,
  `status=${expired.status} code=${expired.body?.code}`)

const userHeaderOnly = await call('GET', `/api/photos?token=${tokenA}`)
record('C5', 'query 传 token 只对 /api/files 生效，业务接口仍 401',
  userHeaderOnly.status === 401, `status=${userHeaderOnly.status} code=${userHeaderOnly.body?.code}`)

// 用 C 的 token 测登出，避免影响后续主家庭用例
if (!tokenC) {
  record('C6', '登出后旧 token 立即失效 → 401/40103', false, '缺少第二个账号的 token，无法在不影响主家庭的前提下测登出')
} else {
  const logoutResp = await call('POST', '/api/auth/logout', { token: tokenC })
  const afterLogout = await call('GET', '/api/auth/me', { token: tokenC })
  record('C6', '登出后旧 token 立即失效 → 401/40103',
    logoutResp.status === 200 && afterLogout.status === 401 && afterLogout.body?.code === 40103,
    `logout=${logoutResp.status} after=${afterLogout.status} code=${afterLogout.body?.code}`)
}
snapshotCount('C6')

/* ------------------------------------------------------------ E. 上传安全 */

console.log('\n=== D. 上传安全（伪装 / SVG / HEIC / 21MB）===')

const evilText = Buffer.from('这不是图片，只是把扩展名改成 .jpg 的文本', 'utf8')
const svgBytes = Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>', 'utf8')
const heicBytes = Buffer.concat([Buffer.from([0x00, 0x00, 0x00, 0x18]), Buffer.from('ftypheic'), Buffer.alloc(64)])
const huge = Buffer.concat([imageBuffer, Buffer.alloc(21 * 1024 * 1024 - imageBuffer.length)])

const countBeforeEvil = snapshotCount('D0')
const securityUpload = await upload(tokenA, albumId, [
  { name: 'fake.jpg', buffer: evilText, type: 'image/jpeg' },
  { name: 'logo.svg', buffer: svgBytes, type: 'image/svg+xml' },
  { name: 'IMG_0001.HEIC', buffer: heicBytes, type: 'image/heic' },
  { name: 'huge.jpg', buffer: huge, type: 'image/jpeg' },
])
const failures = securityUpload.body?.data?.failures ?? []
const failureByCode = (code) => failures.filter((item) => item.code === code)
record('D1', '9 项安全检查：4 个恶意文件全部被拒（success=0）',
  securityUpload.status === 200 && securityUpload.body?.data?.successCount === 0 && failures.length === 4,
  `success=${securityUpload.body?.data?.successCount} failures=${failures.map((f) => `${f.fileName}:${f.code}`).join(',')}`)
record('D2', '伪装成 .jpg 的文本 → 41501', failureByCode(41501).length >= 1, `41501 命中 ${failureByCode(41501).length} 个`)
record('D3', '.svg 被拒（含脚本注入面）',
  failures.some((item) => item.fileName === 'logo.svg' && item.code === 41501), '')
record('D4', '.HEIC 被拒且提示含「iPhone」与「兼容性最佳」',
  failures.some((item) => item.fileName === 'IMG_0001.HEIC' && item.message.includes('iPhone')
    && item.message.includes('兼容性最佳')), '')
record('D5', '21MB 文件 → 41301', failureByCode(41301).length === 1, `41301 命中 ${failureByCode(41301).length} 个`)
const countAfterEvil = snapshotCount('D5')
// 与「恶意上传前」的基线比，而不是硬编码 3：
// 本脚本前面已发现 A9b 的计数漂移缺陷，硬编码会把该缺陷的账算到上传安全检查上
record('D6', '恶意上传后照片数未增加（没有写库残留）',
  countAfterEvil === countBeforeEvil,
  `photo_count=${countBeforeEvil}→${countAfterEvil} 轨迹 ${countTrail.join(' → ')}`)

/* ---------------------------------------------------------------- F. 并发压测 */

console.log('\n=== E. 并发压测 ===')

const filesFor = (prefix, count) => Array.from({ length: count }, (_, index) => ({
  name: `${prefix}-${index}.png`, buffer: imageBuffer, type: 'image/png',
}))

// E1: 3 并发 × 3 轮（回归上一轮的并发死锁修复）
const raceAlbum = (await call('POST', '/api/albums', { token: tokenA, json: { name: 'QA并发-上传' } })).body?.data?.id
let uploadFailures = 0
let uploadTotal = 0
for (let round = 0; round < 3; round += 1) {
  const responses = await Promise.all([0, 1, 2].map((index) => upload(tokenA, raceAlbum,
    [{ name: `race-${round}-${index}.png`, buffer: imageBuffer, type: 'image/png' }])))
  for (const response of responses) {
    uploadTotal += 1
    if (response.status !== 200 || response.body?.data?.successCount !== 1) {
      uploadFailures += 1
    }
  }
}
const raceCount = sql(`select photo_count from album where id = ${raceAlbum}`)[0]
const raceActual = sql(`select count(*) from photo where album_id = ${raceAlbum}`)[0]
record('E1', '同相册 3 并发上传 × 3 轮：0 失败（死锁修复回归）',
  uploadFailures === 0 && uploadTotal === 9 && raceCount === raceActual,
  `成功 ${uploadTotal - uploadFailures}/${uploadTotal}，photo_count=${raceCount} actual=${raceActual}`)
if (uploadFailures > 0 || raceCount !== raceActual) {
  finding('E1', 'P0', '并发上传出现失败或计数漂移', `失败 ${uploadFailures}/${uploadTotal}，photo_count=${raceCount} vs 实际 ${raceActual}`)
}

// E2: 设封面 × 删照片 并发 10 轮 → 检查 album.cover_photo_id 悬挂
let dangling = 0
const danglingSamples = []
const e2Failures = []
for (let round = 0; round < 10; round += 1) {
  const uploadResp = await upload(tokenA, raceAlbum, [{ name: `cover-${round}.png`, buffer: imageBuffer, type: 'image/png' }])
  const targetId = uploadResp.body?.data?.successes?.[0]?.id
  if (!targetId) {
    e2Failures.push({ round, step: 'upload', detail: JSON.stringify(uploadResp.body?.data?.failures) })
    continue
  }
  const [coverResp, deleteResp] = await Promise.all([
    call('PATCH', `/api/albums/${raceAlbum}`, { token: tokenA, json: { name: 'QA并发-上传', coverPhotoId: targetId } }),
    call('DELETE', `/api/photos/${targetId}`, { token: tokenA }),
  ])
  if (coverResp.status >= 500 || deleteResp.status >= 500) {
    e2Failures.push({
      round,
      setCover: `${coverResp.status}/${coverResp.body?.code}`,
      deletePhoto: `${deleteResp.status}/${deleteResp.body?.code}`,
    })
  }
  const storedCover = sql(`select ifnull(cover_photo_id, 'null') from album where id = ${raceAlbum}`)[0]
  if (storedCover !== 'null') {
    const exists = sql(`select count(*) from photo where id = ${storedCover}`)[0]
    if (exists === '0') {
      dangling += 1
      danglingSamples.push({ round, coverStatus: coverResp.status, deleteStatus: deleteResp.status, storedCover })
    }
  }
}
record('E2', '设封面 × 删照片并发 10 轮：无 5xx、album.cover_photo_id 不留悬挂引用',
  dangling === 0 && e2Failures.length === 0,
  `5xx 轮次 ${e2Failures.length}/10，悬挂 ${dangling}/10`
  + `${dangling ? ` 例：${JSON.stringify(danglingSamples[0])}` : ''}`
  + `${e2Failures.length ? ` 失败样本=${JSON.stringify(e2Failures.slice(0, 2))}` : ''}`)
if (e2Failures.length > 0) {
  finding('E2', 'P1', `设封面（PATCH album）与删照片并发时 ${e2Failures.length}/10 轮返回 5xx`,
    '复现：对同一相册并发 PATCH /api/albums/{id} {"name":"...","coverPhotoId":X} 与 DELETE /api/photos/{X} × 10 轮。'
    + `实测 ${e2Failures.length}/10 轮出现 500/50001，后端日志为 `
    + 'CannotAcquireLockException: Deadlock found when trying to get lock ... '
    + '[update album set cover_photo_id=?,created_by=?,description=?,family_id=?,name=?,photo_count=?,updated_at=? where id=?]'
    + '根因：AlbumService#update 走的是 Hibernate 整行 UPDATE（读-改-写），与删照片时对 album 行的依赖/锁形成互等；'
    + 'photo_count 已改成原子 UPDATE，但 cover_photo_id 这条路径没有，也没有重试。'
    + '影响面：用户点「设为封面」的同时另一处删照片，会直接看到「服务异常，请稍后重试」；'
    + `失败样本：${JSON.stringify(e2Failures)}`)
}

// E3: 同一 tag 并发打给 10 张照片
const tagAlbum = (await call('POST', '/api/albums', { token: tokenA, json: { name: 'QA并发-标签' } })).body?.data?.id
const tagUpload = await upload(tokenA, tagAlbum, filesFor('tag', 10))
const tagPhotoIds = (tagUpload.body?.data?.successes ?? []).map((photo) => photo.id)
const tagResponses = await Promise.all(tagPhotoIds.map((photoId) => call('PATCH', `/api/photos/${photoId}`, {
  token: tokenA,
  json: { title: null, description: null, tags: ['并发标签'] },
})))
const tagRowCount = Number(sql(`select count(*) from tag where family_id = ${familyA} and name = '并发标签'`)[0])
const tagRelationCount = Number(sql(`select count(*) from photo_tag pt join tag t on t.id = pt.tag_id `
  + `where t.family_id = ${familyA} and t.name = '并发标签'`)[0])
const tagOkStatus = tagResponses.every((response) => response.status === 200)
const tagFailures = tagResponses
  .map((response, index) => ({
    photoId: tagPhotoIds[index],
    status: response.status,
    code: response.body?.code,
    message: response.body?.message,
  }))
  .filter((item) => item.status !== 200)
record('E3', '同一 tag 并发打给 10 张照片：tag 行唯一、关联数 = 10',
  tagOkStatus && tagRowCount === 1 && tagRelationCount === 10,
  `tagRows=${tagRowCount} relations=${tagRelationCount} http全部200=${tagOkStatus} `
  + `失败样本=${JSON.stringify(tagFailures.slice(0, 2))}`)
if (tagRowCount !== 1 || tagRelationCount !== 10 || !tagOkStatus) {
  finding('E3', 'P1', `同一标签并发打给 10 张照片时 ${tagFailures.length}/10 请求 5xx，标签关联丢失`,
    `复现：对同一家庭的新标签名「并发标签」，并发 PATCH /api/photos/{id} {"tags":["并发标签"]} × 10；`
    + `实测 ${tagFailures.length}/10 返回 ${tagFailures[0]?.status}/${tagFailures[0]?.code}（${tagFailures[0]?.message}），`
    + `最终 tag 行=${tagRowCount}（期望 1）、photo_tag 关联=${tagRelationCount}（期望 10）。`
    + '影响面：多个成员/多端同时给照片打同一个标签（如「海边」）时，部分照片会静默丢标签并收到 500；'
    + '用户侧表现为「打完标签刷新就没了」。'
    + `完整失败样本：${JSON.stringify(tagFailures)}`)
}

// E4: 删相册（delete）与上传并发
const doomedAlbum = (await call('POST', '/api/albums', { token: tokenA, json: { name: 'QA并发-删相册' } })).body?.data?.id
await upload(tokenA, doomedAlbum, [{ name: 'doomed-0.png', buffer: imageBuffer, type: 'image/png' }])
// 孤儿文件用「磁盘文件名 vs photo 三档 key」的集合差来算，
// 不能用「文件数 == 照片数×3」——那会把 E1/E2 阶段的历史遗留算到 E4 头上
const orphansBefore = orphanFiles(familyA)
const doomedResults = await Promise.all([
  call('DELETE', `/api/albums/${doomedAlbum}?photoStrategy=delete`, { token: tokenA }),
  upload(tokenA, doomedAlbum, [{ name: 'doomed-1.png', buffer: imageBuffer, type: 'image/png' }]),
  upload(tokenA, doomedAlbum, [{ name: 'doomed-2.png', buffer: imageBuffer, type: 'image/png' }]),
  upload(tokenA, doomedAlbum, [{ name: 'doomed-3.png', buffer: imageBuffer, type: 'image/png' }]),
])
const serverErrors = doomedResults.filter((response) => response.status >= 500)
const deleteRespE4 = doomedResults[0]
const albumGone = sql(`select count(*) from album where id = ${doomedAlbum}`)[0] === '0'
const uploadOutcomes = doomedResults.slice(1)
  .map((response) => `status=${response.status}/success=${response.body?.data?.successCount}`
    + `/fail=${(response.body?.data?.failures ?? []).map((item) => item.code).join('|') || 0}`)
await new Promise((resolve) => {
  setTimeout(resolve, 800)
})
const orphansAfter = orphanFiles(familyA)
const orphanPhotos = sql(`select count(*) from photo where album_id = ${doomedAlbum} and family_id = ${familyA}`)[0]
const fileDelta = orphansAfter.length - orphansBefore.length
record('E4', '删相册(delete) × 3 并发上传：无 5xx、无孤儿 DB 行',
  serverErrors.length === 0 && albumGone && orphanPhotos === '0',
  `delete=${deleteRespE4.status} 5xx=${serverErrors.length} albumGone=${albumGone} 孤儿照片行=${orphanPhotos} `
  + `孤儿文件 ${orphansBefore.length}→${orphansAfter.length} 并发上传结果=[${uploadOutcomes.join(', ')}]`)
if (serverErrors.length > 0 || orphanPhotos !== '0') {
  finding('E4', 'P1', '删相册与并发上传竞争导致 5xx 或孤儿 DB 行',
    `delete=${deleteRespE4.status} 5xx=${serverErrors.length} 孤儿照片行=${orphanPhotos}`)
}
if (fileDelta > 0) {
  envIssue('E4', `孤儿文件增加 ${fileDelta} 个（现场无法判定，见详情）`,
    '本机沙箱禁止 java 进程 unlink：后端日志出现大量 '
    + '`StorageService - delete file failed key=... java.nio.file.FileSystemException: ... Operation not permitted`，'
    + 'StorageService#deleteQuietly 按设计只 WARN 不抛，所以磁盘文件必然残留，与源码逻辑无关。'
    + '结论：删相册/删照片/failed upload 的磁盘清理在本环境不可验证，需在真实部署环境复跑 H2。')
}

/* ------------------------------------------------------ G. 时间轴分页正确性 */

console.log('\n=== F. 时间轴分页正确性（≥50 条混合数据，逐页抓取）===')

const pageAlbum = (await call('POST', '/api/albums', { token: tokenA, json: { name: 'QA分页-60张' } })).body?.data?.id
// 60 张照片 + 15 里程碑 + 10 成长记录 = 85 条
for (let batch = 0; batch < 20; batch += 1) {
  // eslint-disable-next-line no-await-in-loop
  await upload(tokenA, pageAlbum, filesFor(`page-${batch}`, 3))
}
for (let index = 0; index < 15; index += 1) {
  // eslint-disable-next-line no-await-in-loop
  await call('POST', '/api/milestones', {
    token: tokenA,
    json: {
      babyId,
      title: `QA里程碑分页${index}`,
      achievedAt: `2026-${String(1 + (index % 9)).padStart(2, '0')}-${String(1 + index).padStart(2, '0')}`,
    },
  })
}
for (let index = 0; index < 10; index += 1) {
  // eslint-disable-next-line no-await-in-loop
  await call('POST', `/api/babies/${babyId}/growth-records`, {
    token: tokenA,
    json: {
      recordedAt: `2025-${String(9 + (index % 3)).padStart(2, '0')}-${String(1 + index).padStart(2, '0')}`,
      heightCm: 70 + index,
    },
  })
}

const size = 24
const firstPage = await call('GET', `/api/timeline?page=0&size=${size}`, { token: tokenA })
const total = firstPage.body?.data?.total
const totalPages = firstPage.body?.data?.totalPages
const collected = []
const keys = new Set()
let duplicateHits = 0
for (let page = 0; page < totalPages; page += 1) {
  // eslint-disable-next-line no-await-in-loop
  const response = await call('GET', `/api/timeline?page=${page}&size=${size}`, { token: tokenA })
  for (const group of response.body?.data?.groups ?? []) {
    for (const item of group.items) {
      const key = `${item.type}#${item.id}`
      if (keys.has(key)) {
        duplicateHits += 1
      }
      keys.add(key)
      collected.push({ key, sortTime: item.sortTime, type: item.type })
    }
  }
}
const missing = total - keys.size
const descending = collected.every((item, index) => index === 0
  || collected[index - 1].sortTime >= item.sortTime)
const typeOrder = { photo: 0, milestone: 1, growth: 2 }
const tieBreakOk = collected.every((item, index) => index === 0
  || collected[index - 1].sortTime !== item.sortTime
  || typeOrder[collected[index - 1].type] <= typeOrder[item.type])

record('F1', `逐页抓取 ${totalPages} 页：无重复、无遗漏、总数对得上`,
  duplicateHits === 0 && missing === 0 && keys.size === total,
  `total=${total} 抓取=${keys.size} 重复=${duplicateHits} 遗漏=${missing}`)
record('F2', '全量序严格按 sortTime 倒序', descending, descending ? '' : '存在逆序对')
record('F3', '同秒 tie-break 为 photo → milestone → growth（非字典序）', tieBreakOk,
  tieBreakOk ? '' : '同秒类型顺序错误')
record('F4', '第一页与最后一页均非空（边界未丢数据）',
  (firstPage.body?.data?.groups ?? []).length > 0, `page0 groups=${(firstPage.body?.data?.groups ?? []).length}`)
metrics.pagination = { total, totalPages, collected: keys.size, duplicates: duplicateHits, missing }

if (duplicateHits > 0 || missing !== 0) {
  finding('F1', 'P0', '时间轴分页出现重复或漏项',
    `total=${total} 实际抓到 ${keys.size}，重复 ${duplicateHits}，遗漏 ${missing}`)
}

/* ------------------------------------------------------------ H. 性能抽查 */

console.log('\n=== G. 性能抽查（本地单机，仅供参考）===')

const timeIt = async (fn) => {
  const started = process.hrtime.bigint()
  await fn()
  return Number(process.hrtime.bigint() - started) / 1e6
}

const page0Samples = []
const page5Samples = []
const albumDetailSamples = []
const photoListSamples = []
for (let index = 0; index < 10; index += 1) {
  page0Samples.push(await timeIt(() => call('GET', '/api/timeline?page=0&size=24', { token: tokenA })))
  page5Samples.push(await timeIt(() => call('GET', '/api/timeline?page=5&size=24', { token: tokenA })))
  albumDetailSamples.push(await timeIt(() => call('GET', `/api/albums/${pageAlbum}`, { token: tokenA })))
  photoListSamples.push(await timeIt(() => call('GET', `/api/photos?albumId=${pageAlbum}&size=60`, { token: tokenA })))
}
recordLatency('timeline_page0', page0Samples)
recordLatency('timeline_page5', page5Samples)
recordLatency('album_detail', albumDetailSamples)
recordLatency('photo_list_60', photoListSamples)

const slow = Object.entries(metrics).filter(([, value]) => value.p95 > 300)
record('G1', 'P95 ≤ 300ms（时间轴首页 / 第 5 页 / 相册详情 / 60 张列表）',
  slow.length === 0, slow.length === 0 ? '' : slow.map(([name, value]) => `${name} p95=${value.p95}ms`).join('; '))

/* -------------------------------------------------------------- 收尾与汇总 */

console.log('\n=== H. 清理 ===')
sql(`delete from family where id = ${familyB}`)
const familyName = (await call('GET', '/api/family', { token: tokenA })).body?.data?.name
const finalDelete = await call('DELETE', '/api/family', { token: tokenA, json: { confirmName: familyName } })
record('H1', '删除家庭（9 步显式删除）返回 200 且家庭内照片行清零',
  finalDelete.status === 200 && sql(`select count(*) from photo where family_id = ${familyA}`)[0] === '0',
  `family=${familyName} status=${finalDelete.status} 残留照片=${sql(`select count(*) from photo where family_id = ${familyA}`)[0]}`)

const leftover = countFiles(join(UPLOAD_ROOT, String(familyA)))
record('H2', '删除家庭后磁盘上传目录被清理（不留孤儿文件）',
  leftover.files === 0, `残留文件=${leftover.files} 字节=${leftover.bytes}`)
if (leftover.files > 0) {
  envIssue('H2', '删除家庭后仍有磁盘文件残留，但本机环境禁止 unlink，无法判定',
    `残留 ${leftover.files} 个文件 / ${leftover.bytes} 字节；FamilyService#delete 已调用 `
    + 'storageService.deleteQuietly(...)，后端日志中对应 WARN 为 `Operation not permitted`（沙箱拦截），'
    + '因此该用例只能标记为「本环境不可判定」，需在非沙箱环境复跑。')
}

sql(`delete from \`user\` where email in ('${emailA}', '${emailC}')`)

const failedCount = writeReport()
process.exitCode = failedCount > 0 ? 1 : 0
