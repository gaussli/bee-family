/**
 * 格式化工具：不引入 dayjs，只用字符串切片 + Intl。
 * 后端时间格式固定 "yyyy-MM-dd HH:mm:ss"，日期固定 "yyyy-MM-dd"（docs §11.2）。
 */

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/** 两位数补零 */
function pad2(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}

/**
 * "2026-10-24 15:32:10" → "2026-10-24 15:32"
 *
 * @param input 后端日期时间串
 */
export function formatDateTime(input: string | null | undefined): string {
  if (!input) {
    return ''
  }
  return input.slice(0, 16).replace('T', ' ')
}

/**
 * "2026-10-24 15:32:10" → "2026年10月24日"
 *
 * @param input 后端日期或日期时间串
 */
export function formatDate(input: string | null | undefined): string {
  if (!input) {
    return ''
  }
  const date = input.slice(0, 10)
  const [year, month, day] = date.split('-')
  if (!year || !month || !day) {
    return date
  }
  return `${year}年${Number(month)}月${Number(day)}日`
}

/**
 * "2026-10-24" → "10月 24"（时间轴左侧时间列上行）
 *
 * @param input 日期串
 */
export function formatMonthDay(input: string): string {
  const date = input.slice(0, 10)
  const [, month, day] = date.split('-')
  if (!month || !day) {
    return date
  }
  return `${Number(month)}月 ${Number(day)}`
}

/**
 * "2026-10-24" → "周五"
 *
 * @param input 日期串
 */
export function formatWeekday(input: string): string {
  const date = parseDate(input)
  if (!date) {
    return ''
  }
  return WEEKDAYS[date.getDay()] ?? ''
}

/**
 * 出生日期 → "1岁2个月4天"（与后端 ageText 同规则，仅用于本地预览）
 *
 * @param birthday 出生日期
 * @param today    参照日（默认今天）
 */
export function formatAge(birthday: string, today: Date = new Date()): string {
  const birth = parseDate(birthday)
  if (!birth) {
    return ''
  }
  let years = today.getFullYear() - birth.getFullYear()
  let months = today.getMonth() - birth.getMonth()
  let days = today.getDate() - birth.getDate()
  if (days < 0) {
    months -= 1
    const prevMonth = new Date(today.getFullYear(), today.getMonth(), 0)
    days += prevMonth.getDate()
  }
  if (months < 0) {
    years -= 1
    months += 12
  }
  const parts: string[] = []
  if (years > 0) {
    parts.push(`${years}岁`)
  }
  if (months > 0) {
    parts.push(`${months}个月`)
  }
  parts.push(`${days}天`)
  return parts.join('')
}

/**
 * 字节数 → "4.6 MB"
 *
 * @param bytes 字节
 */
export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let index = 0
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index += 1
  }
  const digits = index === 0 ? 0 : 1
  return `${value.toFixed(digits)} ${units[index]}`
}

/**
 * "2026-10" → "2026 年 10 月"
 *
 * @param month yyyy-MM
 */
export function monthLabel(month: string): string {
  const [year, mon] = month.split('-')
  if (!year || !mon) {
    return month
  }
  return `${year} 年 ${Number(mon)} 月`
}

/**
 * 关键词高亮：先做 HTML 转义，再包 `<mark>`，避免 XSS。
 *
 * @param text    原文
 * @param keyword 关键词（可空）
 */
export function highlight(text: string, keyword: string): string {
  const safe = escapeHtml(text)
  const needle = keyword.trim()
  if (!needle) {
    return safe
  }
  const escapedNeedle = escapeHtml(needle)
  const pattern = escapedNeedle.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return safe.replace(new RegExp(pattern, 'gi'), (match) => `<mark>${match}</mark>`)
}

/**
 * HTML 转义（高亮与富文本展示必须经过此函数）。
 *
 * @param text 原文
 */
export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 数字千分位。
 *
 * @param value 数字
 */
export function formatNumber(value: number): string {
  return value.toLocaleString('zh-CN')
}

/**
 * 保留小数位（null 安全）。
 *
 * @param value  数字
 * @param digits 小数位
 */
export function formatDecimal(value: number | null, digits = 1): string {
  return value === null || value === undefined ? '—' : value.toFixed(digits)
}

/**
 * 解析 "yyyy-MM-dd" 或 "yyyy-MM-dd HH:mm:ss" 为本地 Date。
 *
 * @param input 日期串
 */
export function parseDate(input: string): Date | null {
  if (!input) {
    return null
  }
  const [datePart, timePart] = input.split(' ')
  const [year, month, day] = datePart.split('-').map(Number)
  if (!year || !month || !day) {
    return null
  }
  const [hour, minute, second] = (timePart ?? '00:00:00').split(':').map(Number)
  return new Date(year, month - 1, day, hour || 0, minute || 0, second || 0)
}

/**
 * 今天（yyyy-MM-dd），用于 date input 的 max 属性。
 */
export function todayString(): string {
  const now = new Date()
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`
}
