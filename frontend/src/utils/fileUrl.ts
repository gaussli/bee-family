import { readToken } from '@/api/http'

/** 图片尺寸档位（与后端 ThumbnailService 输出的两个 key 对应） */
export type FileSize = 'thumb' | 'preview' | 'origin'

const BASE = import.meta.env.VITE_API_BASE_URL ?? '/api'

/**
 * 把后端返回的存储 key 拼成可访问 URL。
 *
 * <p>`<img>` 无法设置请求头，因此只有这个函数使用 `?token=`；
 * token 为空时**不带** token 参数（未登录时图片自然 401，不产生脏 URL）。</p>
 *
 * @param key   相对存储 key，如 `1/2026/11/xxxx.jpg`
 * @param size  尺寸档位（仅用于语义提示，实际 key 已区分 thumb/preview）
 * @returns 可直接放进 `<img src>` 的地址
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
