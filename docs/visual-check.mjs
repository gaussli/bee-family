#!/usr/bin/env node
/**
 * 视觉回归自检（零依赖，走 Chrome DevTools Protocol）。
 *
 * 为什么不用 Playwright：本机没有 playwright 依赖，装全局包会污染环境；
 * Node 22 自带 WebSocket 客户端，直连 CDP 足够完成「登录 → 截图 → 量尺寸」。
 *
 * 前置：
 *   1) 后端跑在 8080（前端 vite 代理目标），且已执行 node docs/seed-visual.mjs；
 *   2) 前端 dev server 跑在 5173。
 *
 * 运行：node docs/visual-check.mjs
 * 输出：docs/screenshots-qa/*.png + 控制台测量结果（同时写入 /tmp/qa-visual.json）
 */

import { execFileSync, spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const APP = process.env.APP_URL ?? 'http://localhost:5173'
const API = process.env.API_URL ?? 'http://127.0.0.1:8080'
const EMAIL = process.env.SEED_EMAIL ?? 'qa-visual@home.local'
const PASSWORD = process.env.SEED_PASSWORD
if (!PASSWORD) {
  console.error('缺少 SEED_PASSWORD。用法：SEED_PASSWORD=<演示账号口令> node docs/visual-check.mjs')
  process.exit(1)
}
const OUT_DIR = process.env.OUT_DIR ?? 'docs/screenshots-qa'
const CHROME = process.env.CHROME_BIN
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'

const PORT = 9333
const measurements = {}

/* ------------------------------------------------------------------ CDP 最小客户端 */

let socket
let nextId = 1
const pending = new Map()
const events = []

function send(method, params = {}) {
  const id = nextId++
  socket.send(JSON.stringify({ id, method, params }))
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject })
    setTimeout(() => {
      if (pending.has(id)) {
        pending.delete(id)
        reject(new Error(`CDP timeout: ${method}`))
      }
    }, 30000)
  })
}

async function connect(wsUrl) {
  socket = new WebSocket(wsUrl)
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true })
    socket.addEventListener('error', reject, { once: true })
  })
  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    if (message.id && pending.has(message.id)) {
      const { resolve, reject } = pending.get(message.id)
      pending.delete(message.id)
      if (message.error) {
        reject(new Error(`${message.error.message} (${message.error.code})`))
      } else {
        resolve(message.result)
      }
    } else if (message.method) {
      events.push(message.method)
    }
  })
}

/** 在页面上下文执行表达式并返回 JSON 值。 */
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true,
  })
  if (result.exceptionDetails) {
    throw new Error(`evaluate failed: ${result.exceptionDetails.text} :: ${expression.slice(0, 120)}`)
  }
  return result.result.value
}

/** 轮询等待表达式为真。 */
async function waitFor(expression, label, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    // eslint-disable-next-line no-await-in-loop
    if (await evaluate(`(() => { try { return Boolean(${expression}) } catch { return false } })()`)) {
      return true
    }
    // eslint-disable-next-line no-await-in-loop
    await delay(200)
  }
  throw new Error(`waitFor 超时：${label}`)
}

async function setViewport(width, height) {
  await send('Emulation.setDeviceMetricsOverride', {
    width, height, deviceScaleFactor: 1, mobile: width < 500,
  })
}

async function goto(path) {
  await send('Page.navigate', { url: `${APP}${path}` })
  await delay(300)
  await waitFor("document.readyState === 'complete'", `load ${path}`)
  await delay(600)
}

async function shot(name) {
  const result = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync(`${OUT_DIR}/${name}.png`, Buffer.from(result.data, 'base64'))
  console.log(`  · 截图 ${OUT_DIR}/${name}.png`)
}

/* ----------------------------------------------------------------------- 主流程 */

if (!existsSync(CHROME)) {
  console.error(`找不到 Chrome：${CHROME}（可用 CHROME_BIN 覆盖）`)
  process.exit(1)
}
mkdirSync(OUT_DIR, { recursive: true })

const login = await fetch(`${API}/api/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
}).then((response) => response.json())
if (login.code !== 0) {
  console.error(`登录失败，先跑 docs/seed-visual.mjs：${JSON.stringify(login)}`)
  process.exit(1)
}
const token = login.data.token
const babyId = login.data.babies?.[0]?.id ?? 5

const albums = await fetch(`${API}/api/albums`, {
  headers: { Authorization: `Bearer ${token}` },
}).then((response) => response.json())
const albumId = (albums.data?.items ?? []).find((item) => item.name === '2026 夏天')?.id
  ?? albums.data?.items?.[0]?.id
console.log(`登录成功，babyId=${babyId} albumId=${albumId}`)

const chrome = spawn(CHROME, [
  '--headless=new',
  `--remote-debugging-port=${PORT}`,
  '--user-data-dir=/tmp/qa-chrome-profile',
  '--no-first-run',
  '--no-default-browser-check',
  '--disable-gpu',
  '--hide-scrollbars',
], { stdio: 'ignore' })

try {
  // 等 CDP 端口就绪
  let version
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      // eslint-disable-next-line no-await-in-loop
      version = await fetch(`http://127.0.0.1:${PORT}/json/version`).then((response) => response.json())
      break
    } catch {
      // eslint-disable-next-line no-await-in-loop
      await delay(300)
    }
  }
  if (!version) {
    throw new Error('Chrome 未在 30s 内打开调试端口')
  }
  const target = await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: 'PUT' })
    .then((response) => response.json())
  await connect(target.webSocketDebuggerUrl)
  await send('Page.enable')
  await send('Runtime.enable')

  /* 1. 登录页 1440（缺陷 1：蜂巢纹理只出现在右下角，不穿过品牌文案） */
  await setViewport(1440, 900)
  await goto('/login')
  await shot('01-login-1440')
  measurements.loginBackground = await evaluate(`(() => {
    const body = getComputedStyle(document.body)
    const layers = [body.backgroundImage, getComputedStyle(document.documentElement).backgroundImage]
    return {
      bodyBackground: body.backgroundColor,
      hasTexture: layers.some((value) => value && value !== 'none'),
      textureLayers: layers.filter((value) => value && value !== 'none').length,
    }
  })()`)

  // 注入 token（等价于走完登录表单），之后所有页面都是已登录态
  await evaluate(`localStorage.setItem('bee.token', ${JSON.stringify(token)});
    localStorage.setItem('bee.activeBabyId', '${babyId}'); true`)

  /* 2. 相册详情 1440（缺陷 2：5 列网格铺满、排序下拉不被截断） */
  await goto(`/albums/${albumId}`)
  await waitFor("document.querySelectorAll('img').length > 3", '相册照片渲染')
  await shot('04-album-detail-1440')
  measurements.albumGrid = await evaluate(`(() => {
    const grids = Array.from(document.querySelectorAll('*')).filter((el) => {
      const style = getComputedStyle(el)
      return style.display === 'grid' && el.children.length >= 4
    })
    const grid = grids.sort((a, b) => b.children.length - a.children.length)[0]
    if (!grid) return { found: false }
    const style = getComputedStyle(grid)
    const rect = grid.getBoundingClientRect()
    const columns = style.gridTemplateColumns.split(' ').filter(Boolean)
    const parent = grid.parentElement.getBoundingClientRect()
    return {
      found: true,
      columns: columns.length,
      columnWidths: columns.map((value) => Math.round(Number.parseFloat(value))),
      gridWidth: Math.round(rect.width),
      parentWidth: Math.round(parent.width),
      gaps: style.columnGap,
      childCount: grid.children.length,
    }
  })()`)
  measurements.albumSortSelect = await evaluate(`(() => {
    const selects = Array.from(document.querySelectorAll('select'))
    return selects.map((el) => ({
      text: (el.options[el.selectedIndex]?.text ?? '').trim(),
      clientWidth: Math.round(el.getBoundingClientRect().width),
      scrollWidth: el.scrollWidth,
      clipped: el.scrollWidth > el.clientWidth + 1,
    }))
  })()`)
  measurements.albumDateTexts = await evaluate(`(() => {
    const pattern = /^\\d{4}-\\d{2}-\\d{2}/
    return Array.from(document.querySelectorAll('*'))
      .filter((el) => el.children.length === 0 && pattern.test((el.textContent ?? '').trim()))
      .slice(0, 6)
      .map((el) => ({
        text: el.textContent.trim(),
        lines: el.getClientRects().length,
        whiteSpace: getComputedStyle(el).whiteSpace,
        height: Math.round(el.getBoundingClientRect().height),
      }))
  })()`)

  /* 3. 成长页 1440（缺陷 3：日期列不换行） */
  await goto('/growth')
  await delay(1500)
  await shot('05-growth-1440')
  measurements.growthDates = await evaluate(`(() => {
    const pattern = /\\d{4}-\\d{2}-\\d{2}|\\d{1,2}月\\d{1,2}日/
    return Array.from(document.querySelectorAll('*'))
      .filter((el) => el.children.length === 0 && pattern.test((el.textContent ?? '').trim()))
      .slice(0, 8)
      .map((el) => {
        const style = getComputedStyle(el)
        return {
          text: el.textContent.trim(),
          lines: el.getClientRects().length,
          whiteSpace: style.whiteSpace,
          height: Math.round(el.getBoundingClientRect().height),
          fontSize: style.fontSize,
        }
      })
  })()`)
  measurements.growthChart = await evaluate(`(() => {
    const canvas = document.querySelector('canvas')
    return canvas
      ? { rendered: canvas.width > 0 && canvas.height > 0, width: canvas.width, height: canvas.height }
      : { rendered: false }
  })()`)

  /* 4. 375 宽：时间轴 / 相册详情不能横向溢出 */
  await setViewport(375, 812)
  await goto('/timeline')
  await delay(1200)
  await shot('06-timeline-375')
  measurements.mobileTimeline = await evaluate(`(() => ({
    innerWidth: window.innerWidth,
    scrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth,
    overflow: document.documentElement.scrollWidth > window.innerWidth + 1,
  }))()`)

  await goto(`/albums/${albumId}`)
  await delay(1200)
  await shot('20-album-detail-375')
  measurements.mobileAlbumDetail = await evaluate(`(() => ({
    innerWidth: window.innerWidth,
    scrollWidth: document.documentElement.scrollWidth,
    overflow: document.documentElement.scrollWidth > window.innerWidth + 1,
  }))()`)

  writeFileSync('/tmp/qa-visual.json', JSON.stringify(measurements, null, 2))
  console.log('\n--- 测量结果 ---')
  console.log(JSON.stringify(measurements, null, 2))
} finally {
  try {
    execFileSync('kill', [String(chrome.pid)])
  } catch {
    // 忽略：可能已退出
  }
}
