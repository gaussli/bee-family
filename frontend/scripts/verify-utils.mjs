#!/usr/bin/env node
/**
 * 前端纯函数可靠性验证脚本（零依赖，不引入测试框架）。
 *
 * 为什么不用 vitest：本项目前端刻意保持「无测试框架」的依赖面（package.json 里没有任何
 * 测试相关 devDependency）。引入 vitest 会带来 jsdom / happy-dom / tinypool 等一串传递依赖，
 * 与「纯函数断言」这一目标不成比例；Node 22 已原生支持 TypeScript 类型擦除
 * （--experimental-strip-types），因此直接用 node 跑断言即可，零新增依赖、零版本漂移风险。
 *
 * 运行：
 *   cd frontend && npm run verify
 *
 * 覆盖范围（只测纯函数，不测组件渲染）：
 *   - src/utils/format.ts   日期 / 体积 / 年龄 / 高亮（含 XSS 转义）
 *   - src/utils/validate.ts 表单校验规则（与后端双写的阈值一致性）
 */

import assert from 'node:assert/strict'
import {
  escapeHtml,
  formatAge,
  formatBytes,
  formatDate,
  formatDateTime,
  formatDecimal,
  formatMonthDay,
  formatNumber,
  formatWeekday,
  highlight,
  monthLabel,
  parseDate,
  todayString,
} from '../src/utils/format.ts'
import {
  MAX_FILE_SIZE,
  extensionOf,
  isAllowedImage,
  isTooLarge,
  isUnsupportedImage,
  validateEmail,
  validateInviteCode,
  validateNickname,
  validatePassword,
  validateRange,
} from '../src/utils/validate.ts'

let passed = 0
const failures = []

/**
 * 注册并执行一条断言。
 *
 * @param {string} name 用例名
 * @param {() => void} body 断言体
 */
function test(name, body) {
  try {
    body()
    passed += 1
    console.log(`  \u2713 ${name}`)
  } catch (error) {
    failures.push({ name, error })
    console.log(`  \u2717 ${name}`)
    console.log(`      ${error.message.split('\n')[0]}`)
  }
}

console.log('\n[format.ts] 日期与文案')

test('formatDateTime: 截到分钟并把 T 换成空格', () => {
  assert.equal(formatDateTime('2026-10-24 15:32:10'), '2026-10-24 15:32')
  assert.equal(formatDateTime('2026-10-24T15:32:10'), '2026-10-24 15:32')
})

test('formatDateTime: null / undefined / 空串返回空串（不输出 null 字样）', () => {
  assert.equal(formatDateTime(null), '')
  assert.equal(formatDateTime(undefined), '')
  assert.equal(formatDateTime(''), '')
})

test('formatDate: 2026-10-24 → 2026年10月24日（月份不补零）', () => {
  assert.equal(formatDate('2026-10-24 15:32:10'), '2026年10月24日')
  assert.equal(formatDate('2026-01-05'), '2026年1月5日')
  assert.equal(formatDate(null), '')
})

test('formatMonthDay: 2026-10-24 → 10月 24', () => {
  assert.equal(formatMonthDay('2026-10-24'), '10月 24')
  assert.equal(formatMonthDay('2026-01-05'), '1月 5')
})

test('formatWeekday: 2026-10-24 是周六（时区无关，取本地日期）', () => {
  assert.equal(formatWeekday('2026-10-24'), '周六')
  assert.equal(formatWeekday('2026-11-05'), '周四')
  assert.equal(formatWeekday(''), '')
})

test('formatAge: 与后端 DateTimes.ageText 同规则（2025-08-18 → 2026-11-05）', () => {
  assert.equal(formatAge('2025-08-18', new Date(2026, 10, 5)), '1岁2个月18天')
})

test('formatAge: 不满 1 岁省略「X岁」（2026-07-20 → 2026-11-05）', () => {
  assert.equal(formatAge('2026-07-20', new Date(2026, 10, 5)), '3个月16天')
})

test('formatAge: 生日当天为 0天；非法输入返回空串', () => {
  assert.equal(formatAge('2026-11-05', new Date(2026, 10, 5)), '0天')
  assert.equal(formatAge('bad-date', new Date(2026, 10, 5)), '')
})

console.log('\n[format.ts] 数值与体积')

test('formatBytes: 0 / 负数 / NaN 都返回 0 B（不会出现 -1 B）', () => {
  assert.equal(formatBytes(0), '0 B')
  assert.equal(formatBytes(-1), '0 B')
  assert.equal(formatBytes(Number.NaN), '0 B')
})

test('formatBytes: 单位换算与小数位', () => {
  assert.equal(formatBytes(1023), '1023 B')
  assert.equal(formatBytes(1024), '1.0 KB')
  assert.equal(formatBytes(4823449), '4.6 MB')
  assert.equal(formatBytes(20 * 1024 * 1024), '20.0 MB')
})

test('formatDecimal: null / undefined 输出破折号而不是 NaN', () => {
  assert.equal(formatDecimal(null), '\u2014')
  assert.equal(formatDecimal(undefined), '\u2014')
  assert.equal(formatDecimal(72.456, 1), '72.5')
})

test('formatNumber: 千分位', () => {
  assert.equal(formatNumber(1234567), '1,234,567')
})

test('monthLabel: 2026-10 → 2026 年 10 月；非法输入原样返回', () => {
  assert.equal(monthLabel('2026-10'), '2026 年 10 月')
  assert.equal(monthLabel('2026'), '2026')
})

test('parseDate: 支持日期与日期时间；非法输入返回 null', () => {
  const dateOnly = parseDate('2026-10-24')
  assert.ok(dateOnly instanceof Date)
  assert.equal(dateOnly.getFullYear(), 2026)
  assert.equal(dateOnly.getMonth(), 9)
  assert.equal(dateOnly.getDate(), 24)
  assert.equal(dateOnly.getHours(), 0)
  assert.equal(parseDate('2026-10-24 15:32:10').getHours(), 15)
  assert.equal(parseDate('oops'), null)
})

test('todayString: yyyy-MM-dd 且与本地当天一致', () => {
  const today = todayString()
  assert.match(today, /^\d{4}-\d{2}-\d{2}$/)
  const now = new Date()
  const expected = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('-')
  assert.equal(today, expected, '必须用本地日期，不能用 UTC（否则跨时区会差一天）')
})

console.log('\n[format.ts] HTML 转义与高亮（XSS 关键用例）')

test('escapeHtml: 五个危险字符全部转义', () => {
  assert.equal(escapeHtml('<>&"\''), '&lt;&gt;&amp;&quot;&#39;')
  assert.equal(escapeHtml('&'), '&amp;')
})

test('highlight: 注入载荷 <img onerror=x> 被转义为不可执行文本', () => {
  const output = highlight('<img onerror=x>海', '海')
  assert.ok(!output.includes('<img'), '输出里不能出现可执行的 <img 标签')
  assert.ok(output.includes('&lt;img onerror=x&gt;'), '标签必须被转义')
  assert.ok(output.includes('<mark>海</mark>'), '关键词仍要高亮')
})

test('highlight: 关键词本身含尖括号时也不能注入', () => {
  const output = highlight('<script>alert(1)</script>', '<script>')
  assert.ok(!output.includes('<script'), '关键词不能把标签带进 DOM')
  assert.ok(output.includes('&lt;script&gt;'))
})

test('highlight: 文本中的引号被转义（属性截断场景）', () => {
  assert.equal(highlight('"海"', '海'), '&quot;<mark>海</mark>&quot;')
})

test('highlight: 关键词含正则元字符不抛异常（按字面量匹配）', () => {
  const output = highlight('a.b*c(d)', '.*')
  assert.ok(output.includes('a.b*c(d)'), '未命中时原文保持不变')
  const hit = highlight('x.*y', '.*')
  assert.ok(hit.includes('<mark>.*</mark>'), '应作为字面量命中而不是正则通配')
})

test('highlight: 大小写不敏感，且保留原文大小写', () => {
  assert.ok(highlight('海边 Beach', 'beach').includes('<mark>Beach</mark>'))
})

test('highlight: 空关键词返回纯转义文本，不产生 mark', () => {
  const output = highlight('<b>x</b>', '   ')
  assert.equal(output, '&lt;b&gt;x&lt;/b&gt;')
  assert.ok(!output.includes('<mark>'))
})

test('highlight: 中文重复出现时每处都高亮', () => {
  const output = highlight('海边 海边', '海边')
  assert.equal(output.match(/<mark>/g).length, 2)
})

console.log('\n[validate.ts] 上传前置校验')

test('extensionOf: 大小写归一、无扩展名返回空串', () => {
  assert.equal(extensionOf('IMG_0001.JPG'), 'jpg')
  assert.equal(extensionOf('a.b.webp'), 'webp')
  assert.equal(extensionOf('photo'), '')
})

test('isAllowedImage: 白名单与后端一致（jpg/jpeg/png/webp）', () => {
  for (const name of ['a.jpg', 'a.jpeg', 'a.png', 'a.webp', 'A.JPG']) {
    assert.equal(isAllowedImage(name), true, `${name} 应允许`)
  }
  for (const name of ['a.heic', 'a.svg', 'a.gif', 'a.pdf', 'noext']) {
    assert.equal(isAllowedImage(name), false, `${name} 应拒绝`)
  }
})

test('isUnsupportedImage: HEIC / HEIF 命中（用于触发 iPhone 专属提示）', () => {
  assert.equal(isUnsupportedImage('IMG_0001.heic'), true)
  assert.equal(isUnsupportedImage('IMG.HEIF'), true)
  assert.equal(isUnsupportedImage('a.jpg'), false)
})

test('isTooLarge: 20MB 边界（等于上限允许，多 1 字节拒绝）', () => {
  assert.equal(isTooLarge(MAX_FILE_SIZE), false)
  assert.equal(isTooLarge(MAX_FILE_SIZE + 1), true)
  assert.equal(MAX_FILE_SIZE, 20 * 1024 * 1024, '前端 20MB 必须与后端 Limits.MAX_FILE_SIZE_BYTES 一致')
})

console.log('\n[validate.ts] 表单规则')

test('validateEmail: 空 / 格式 / 长度三类错误互不覆盖', () => {
  assert.equal(validateEmail(''), '请输入邮箱')
  assert.equal(validateEmail('   '), '请输入邮箱')
  assert.equal(validateEmail('not-an-email'), '邮箱格式不正确')
  assert.equal(validateEmail('a@b.com'), '')
  assert.equal(validateEmail(`${'a'.repeat(120)}@b.com`), '邮箱长度不能超过 120 个字符')
})

test('validatePassword: 6-32 位；登录场景可关闭长度校验', () => {
  assert.equal(validatePassword(''), '请输入密码')
  assert.equal(validatePassword('12345'), '密码长度需为 6-32 个字符')
  assert.equal(validatePassword('123456'), '')
  assert.equal(validatePassword('x'.repeat(33)), '密码长度需为 6-32 个字符')
  assert.equal(validatePassword('123', false), '')
})

test('validateNickname: 必填且不超 32 字', () => {
  assert.equal(validateNickname('  '), '请输入昵称')
  assert.equal(validateNickname('严过关'), '')
  assert.equal(validateNickname('名'.repeat(33)), '昵称长度不能超过 32 个字符')
})

test('validateInviteCode: 选填，但填写时必须是 8 位字母数字', () => {
  assert.equal(validateInviteCode(''), '')
  assert.equal(validateInviteCode('ABC'), '邀请码为 8 位字母数字')
  assert.equal(validateInviteCode('ABCD1234'), '')
  assert.equal(validateInviteCode('ABCD-234'), '邀请码为 8 位字母数字')
})

test('validateRange: 空值放行，非数字与越界分别报错', () => {
  assert.equal(validateRange('', 30, 150, '身高'), '')
  assert.equal(validateRange('abc', 30, 150, '身高'), '身高必须是数字')
  assert.equal(validateRange('20', 30, 150, '身高'), '身高需在 30-150 之间')
  assert.equal(validateRange('100', 30, 150, '身高'), '')
  assert.equal(validateRange('150.0', 30, 150, '身高'), '')
  assert.equal(validateRange('150.1', 30, 150, '身高'), '身高需在 30-150 之间')
})

const total = passed + failures.length
console.log(`\n${'-'.repeat(56)}`)
console.log(`用例总数: ${total}   通过: ${passed}   失败: ${failures.length}`)
if (failures.length > 0) {
  console.log('\n失败用例：')
  for (const failure of failures) {
    console.log(`- ${failure.name}`)
    console.log(`  ${failure.error.message.replace(/\n/g, '\n  ')}`)
  }
  process.exitCode = 1
} else {
  console.log('结果: PASS')
}
