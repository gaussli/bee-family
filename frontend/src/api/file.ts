import { fileUrl } from '@/utils/fileUrl'

/**
 * 图片访问地址（`<img src>` 专用）。
 *
 * <p>只有 `/api/files/**` 接受 `?token=`（ADR-04），其余请求一律走 axios header。
 * 实现放在 `utils/fileUrl.ts`，此处仅做同层 re-export，保持 §9.1 目录结构完整。</p>
 */
export { fileUrl }
