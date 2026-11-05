/**
 * 上传队列的本地状态（仅前端使用，不对应后端 DTO）。
 */
export type UploadStatus = 'queued' | 'uploading' | 'done' | 'error'

export interface UploadTask {
  id: number
  file: File
  /** `createObjectURL` 生成的本地预览地址，任务结束时必须 revoke */
  previewUrl: string
  fileName: string
  size: number
  status: UploadStatus
  /** 0-100 */
  progress: number
  errorCode: number | null
  errorMessage: string | null
  /** 目标相册；`null` 表示上传到「未分类」 */
  albumId: number | null
}

export interface UploadSummary {
  done: number
  total: number
  bytesPerSec: number
}
