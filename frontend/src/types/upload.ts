/**
 * 上传队列的本地状态（仅前端使用，不对应后端 DTO）。
 *
 * v1.1：`UploadStatus` 仍驱动调度器（queued / uploading / done / error），
 * 新增 `UploadPhase` 描述 HEIC 四个一等「用户可见状态」：
 * ① 识别中 → ② 转码中 → ③ 成功 → ④ 失败（docs/06 §9.2）。
 * 「排队」「上传中」是瞬时子状态，不是一等状态。
 */

export type UploadStatus = 'queued' | 'uploading' | 'done' | 'error'

export type UploadPhase =
  | 'queued'
  | 'detecting'
  | 'transcoding'
  | 'uploading'
  | 'done'
  | 'failed'

export interface UploadTask {
  id: number
  file: File
  /** `createObjectURL` 生成的本地预览地址，任务结束时必须 revoke；HEIC 不使用本地预览。 */
  previewUrl: string
  fileName: string
  size: number
  status: UploadStatus
  /** 用户可见状态（四态 + 两个瞬时子状态），驱动文案与进度条形态。 */
  phase: UploadPhase
  /** 是否 HEIC/HEIF：决定占位块、格式 Chip 与四态文案。 */
  isHeic: boolean
  /** 0-100 */
  progress: number
  errorCode: number | null
  /** 服务端 `failures[].message` 原文；前端不按 code 生成本地文案。 */
  errorMessage: string | null
  /** 目标相册；`null` 表示上传到「未分类」 */
  albumId: number | null
}

export interface UploadSummary {
  done: number
  total: number
  bytesPerSec: number
}
