/**
 * 前端表单与上传前置校验规则（与后端 @Size / ImageValidator 双写，见 docs/07 §10.5）。
 *
 * v1.1 白名单：**JPEG / PNG / WebP / HEIC(HEIF)**，明确排除 GIF；
 * 不留任何可配置开关，不留「后续开启」隐式入口。GIF 在选择阶段即被拦截，
 * 不会进入上传队列，因此 HEIC 四态里不存在任何 GIF 分支。
 */

export const ALLOWED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'heic', 'heif'] as const
export const HEIC_EXTENSIONS = ['heic', 'heif'] as const
/** 明确排除：GIF 本期不纳入白名单。 */
export const GIF_EXTENSIONS = ['gif'] as const
export const MAX_FILE_SIZE = 20 * 1024 * 1024

/** 上传控件 accept：排除 GIF；同时给出 mime 与扩展名以兼容不同浏览器。 */
export const UPLOAD_ACCEPT =
  'image/jpeg,image/png,image/webp,image/heic,image/heif,.jpg,.jpeg,.png,.webp,.heic,.heif'

/** 全站统一的支持格式声明（docs/06 §9.4「必须出现」）。 */
export const SUPPORTED_FORMAT_TEXT = '支持 JPG / PNG / WebP / HEIC'
/** 唯一允许出现「GIF」字样的明示拒绝提示（docs/06 §9.3）。 */
export const GIF_REJECT_TEXT = '暂不支持 GIF 动图，请上传 JPG / PNG / WebP / HEIC 格式的照片'
/** C-12：HEIC 成功态 / 汇总 Toast / 浮层常驻说明 / 灯箱 hint 共用的尾句。 */
export const HEIC_COPY_TEXT = '已生成 1600px 展示副本，原图保留'

export interface FieldRule {
  min?: number
  max?: number
  pattern?: RegExp
  message: string
}

export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
/** 家庭码：10 位去混淆字符；展示时格式化为 XXXXX-XXXXX。 */
export const FAMILY_CODE_PATTERN = /^[A-Z0-9]{10}$/

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
 * 是否为本产品支持的图片扩展名（jpg/jpeg/png/webp/heic/heif）。
 *
 * @param fileName 文件名
 */
export function isAllowedImage(fileName: string): boolean {
  return (ALLOWED_EXTENSIONS as readonly string[]).includes(extensionOf(fileName))
}

/**
 * 是否为 HEIC/HEIF（触发四态、HEIC 占位块与格式 Chip）。
 *
 * @param fileName 文件名
 */
export function isHeicImage(fileName: string): boolean {
  return (HEIC_EXTENSIONS as readonly string[]).includes(extensionOf(fileName))
}

/**
 * 是否为明确排除的 GIF（选择阶段拦截，不进入上传队列）。
 *
 * @param fileName 文件名
 */
export function isGifImage(fileName: string): boolean {
  return (GIF_EXTENSIONS as readonly string[]).includes(extensionOf(fileName))
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
 * 家庭码输入归一：去空白、忽略连字符、统一大写。
 *
 * @param input 用户输入
 */
export function normalizeFamilyCode(input: string): string {
  return input.replace(/[\s-]/g, '').toUpperCase()
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
