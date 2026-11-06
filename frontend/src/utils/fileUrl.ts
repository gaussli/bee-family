import { readToken } from '@/api/http'
import type { PhotoDetail } from '@/types/models'

/** 图片尺寸档位（与后端 ThumbnailService 输出的两个 key 对应） */
export type FileSize = 'thumb' | 'preview' | 'origin'

const BASE = import.meta.env.VITE_API_BASE_URL ?? '/api'

/**
 * 把后端返回的存储 key 拼成可访问 URL。
 *
 * <p>`<img>` / `<a download>` 无法设置请求头，因此只有这个函数使用 `?token=`；
 * token 为空时**不带** token 参数（未登录时图片自然 401，不产生脏 URL）。</p>
 *
 * <p>URL 首段已含 familyId，切换家庭天然隔离 HTTP 缓存；**不追加任何家庭参数**
 * （docs/07 §10.3 R-6 禁止项）。</p>
 *
 * @param key   相对存储 key，如 `1/2026/11/xxxx.jpg`
 * @param size  尺寸档位（仅用于语义提示，实际 key 已区分 thumb/preview）
 * @returns 可直接放进 `<img src>` / `<a href>` 的地址
 */
export function fileUrl(key: string | null | undefined, size: FileSize = 'thumb'): string {
  if (!key) {
    return ''
  }
  const clean = key.replace(/^\/+/, '')
  const path = `${BASE}/files/${clean}`
  const token = readToken()
  const withToken = token ? `${path}?token=${encodeURIComponent(token)}` : path
  // size 只影响调用方语义：preview 档位通常传 previewKey，这里不再改写路径
  void size
  return withToken
}

/** 下载入口的视觉变体（复用既有 secondary / ghost 按钮规格）。 */
export type DownloadVariant = 'secondary' | 'ghost'

export interface DownloadEntry {
  /** 按钮文案（docs/06 §8.6 准确措辞） */
  label: string
  /** 可访问的下载地址 */
  href: string
  variant: DownloadVariant
  /** 是否为 HEIC 原始文件入口 */
  original: boolean
}

/**
 * 依 `displayKey !== null` 生成下载入口（docs/07 §4.5 / docs/06 §8.6）。
 *
 * - HEIC：双按钮「下载照片（JPG）」（1600px 展示副本，默认下载）+「下载原始文件（HEIC）」；
 * - 非 HEIC：单按钮「下载原图」→ storageKey；
 * - 详情接口暂不可用时用 previewKey 兜底，保证灯箱永远不因下载区阻断浏览。
 *
 * 仅提供上述入口，不追加任何调节项；静态文案严格遵循 §9.4 禁令。
 *
 * @param detail 照片详情（可为 null，例如后端尚未返回新字段或请求失败）
 */
export function downloadEntries(detail: PhotoDetail | null): DownloadEntry[] {
  if (!detail) return []
  const isHeic = detail.displayKey !== null && detail.displayKey !== undefined
  const originalKey = detail.storageKey || detail.previewKey || ''
  if (isHeic) {
    return [
      {
        label: '下载照片（JPG）',
        href: fileUrl(detail.displayKey ?? detail.previewKey, 'preview'),
        variant: 'secondary',
        original: false,
      },
      {
        label: '下载原始文件（HEIC）',
        href: fileUrl(originalKey, 'origin'),
        variant: 'ghost',
        original: true,
      },
    ]
  }
  return [
    {
      label: '下载原图',
      href: fileUrl(originalKey, 'origin'),
      variant: 'secondary',
      original: true,
    },
  ]
}
