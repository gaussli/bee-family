/**
 * 前端表单校验规则（与后端 @Size/@DecimalMin 双写，见 docs §11.7）。
 */

export const ALLOWED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp'] as const
export const BLOCKED_EXTENSIONS = ['heic', 'heif'] as const
export const MAX_FILE_SIZE = 20 * 1024 * 1024

export interface FieldRule {
  min?: number
  max?: number
  pattern?: RegExp
  message: string
}

export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
export const INVITE_CODE_PATTERN = /^[A-Za-z0-9]{8}$/

/**
 * 取小写扩展名（不含点）。
 *
 * @param fileName 文件名
 */
export function extensionOf(fileName: string): string {
  const index = fileName.lastIndexOf('.')
  return index < 0 ? '' : fileName.slice(index + 1).toLowerCase()
}

/**
 * 是否为本产品支持的图片扩展名（jpg/jpeg/png/webp）。
 *
 * @param fileName 文件名
 */
export function isAllowedImage(fileName: string): boolean {
  return (ALLOWED_EXTENSIONS as readonly string[]).includes(extensionOf(fileName))
}

/**
 * 是否为「明确不支持」的格式（HEIC/HEIF，需给 iPhone 专属提示）。
 *
 * @param fileName 文件名
 */
export function isUnsupportedImage(fileName: string): boolean {
  return (BLOCKED_EXTENSIONS as readonly string[]).includes(extensionOf(fileName))
}

/**
 * 文件体积是否超限。
 *
 * @param size 字节
 */
export function isTooLarge(size: number): boolean {
  return size > MAX_FILE_SIZE
}

/**
 * 邮箱校验。
 *
 * @param value 输入
 */
export function validateEmail(value: string): string {
  if (!value.trim()) {
    return '请输入邮箱'
  }
  if (!EMAIL_PATTERN.test(value.trim())) {
    return '邮箱格式不正确'
  }
  if (value.trim().length > 120) {
    return '邮箱长度不能超过 120 个字符'
  }
  return ''
}

/**
 * 密码校验（登录只校验非空，注册要求 6-32）。
 *
 * @param value      输入
 * @param withLength 是否校验长度
 */
export function validatePassword(value: string, withLength = true): string {
  if (!value) {
    return '请输入密码'
  }
  if (withLength && (value.length < 6 || value.length > 32)) {
    return '密码长度需为 6-32 个字符'
  }
  return ''
}

/**
 * 昵称校验。
 *
 * @param value 输入
 */
export function validateNickname(value: string): string {
  const trimmed = value.trim()
  if (!trimmed) {
    return '请输入昵称'
  }
  if (trimmed.length > 32) {
    return '昵称长度不能超过 32 个字符'
  }
  return ''
}

/**
 * 邀请码校验（选填，填写时必须是 8 位字母数字）。
 *
 * @param value 输入
 */
export function validateInviteCode(value: string): string {
  const trimmed = value.trim()
  if (!trimmed) {
    return ''
  }
  if (!INVITE_CODE_PATTERN.test(trimmed)) {
    return '邀请码为 8 位字母数字'
  }
  return ''
}

/**
 * 数值区间校验（用于身高/体重/头围）。
 *
 * @param value 输入
 * @param min   下限
 * @param max   上限
 * @param label 字段名
 */
export function validateRange(
  value: string,
  min: number,
  max: number,
  label: string,
): string {
  if (!value.trim()) {
    return ''
  }
  const num = Number(value)
  if (!Number.isFinite(num)) {
    return `${label}必须是数字`
  }
  if (num < min || num > max) {
    return `${label}需在 ${min}-${max} 之间`
  }
  return ''
}
