import { computed, ref, type ComputedRef, type Ref } from 'vue'
import * as photoApi from '@/api/photo'
import { ApiError } from '@/api/http'
import { useUploadProgressStore } from '@/stores/upload-progress'
import type { Photo } from '@/types/models'
import type { UploadPhase, UploadStatus, UploadSummary, UploadTask } from '@/types/upload'
import { isGifImage, isHeicImage, isTooLarge } from '@/utils/validate'
import { createObjectUrl, revokeObjectUrl } from '@/utils/image'

/** 同时上传的并发数（浏览器对同域 keep-alive 连接数通常为 6，取 3 留余量）。 */
const CONCURRENCY = 3
/** 队列中最多保留的已完成条目，超出时从头部裁剪，避免内存无限增长。 */
const MAX_TASKS = 60

export interface UploadQueueOptions {
  /** 任一文件上传成功后回调（携带后端返回的照片实体）。 */
  onUploaded?: (photo: Photo) => void
  /** 全部结束（成功或失败）后回调。 */
  onSettled?: () => void
  /** 单个文件失败时回调，参数为面向用户的错误文案。 */
  onFailed?: (task: UploadTask) => void
  /** 选择阶段被拦截的 GIF 张数（不进入队列）。 */
  onRejectedGif?: (count: number) => void
}

/**
 * 上传队列。
 *
 * <p>关键约束（来自后端实测行为）：`POST /api/photos/upload` **永远返回 200**，
 * 单个文件是否成功体现在响应体的 `successes[] / failures[]` 中，
 * 因此单文件失败**不能**用 `catch` 的 HTTP 状态判断，必须解析 payload。
 * 失败文案逐字取 `failures[].message`（服务端权威），**不按 code 做本地映射**
 * （docs/06 §9.2 ④、docs/07 §9.9）。</p>
 *
 * <p>队列语义：`queued → uploading → done | error`；`error` 可 `retry` 重排。
 * HEIC 的用户可见四态映射：queued→①识别中、uploading(HEIC)→②转码中、
 * done→③成功、error→④失败；「排队」「上传中」为瞬时子状态，不占一等状态。
 * GIF 在 `addFiles` 阶段即被拦截，不进入队列，四态中不存在任何 GIF 分支。</p>
 */
export function useUploadQueue(options: UploadQueueOptions = {}): {
  tasks: Ref<UploadTask[]>
  summary: ComputedRef<UploadSummary>
  activeCount: ComputedRef<number>
  failedCount: ComputedRef<number>
  isUploading: ComputedRef<boolean>
  addFiles: (files: File[], albumId: number | null) => number
  start: (albumId: number | null) => void
  retry: (id: number) => void
  cancel: (id: number) => void
  cancelAll: () => void
  clearFinished: () => void
  dispose: () => void
} {
  const tasks = ref<UploadTask[]>([])
  const controllers = new Map<number, AbortController>()
  const progress = useUploadProgressStore()
  let seed = 0
  let startedAt = 0
  let uploadedBytes = 0
  let pumping = false

  const summary = computed<UploadSummary>(() => {
    const total = tasks.value.length
    const done = tasks.value.filter((task) => task.status === 'done').length
    const elapsed = startedAt > 0 ? (Date.now() - startedAt) / 1000 : 0
    const bytesPerSec = elapsed > 1 ? uploadedBytes / elapsed : 0
    return { done, total, bytesPerSec }
  })

  const activeCount = computed<number>(
    () => tasks.value.filter((task) => task.status === 'uploading' || task.status === 'queued').length,
  )
  const failedCount = computed<number>(() => tasks.value.filter((task) => task.status === 'error').length)
  const isUploading = computed<boolean>(() => activeCount.value > 0)

  /** 加入文件：GIF 直接跳过；体积超限本地标红；HEIC 正常入队。 */
  function addFiles(files: File[], albumId: number | null): number {
    const accepted: UploadTask[] = []
    let gifCount = 0
    for (const file of files) {
      if (isGifImage(file.name)) {
        gifCount += 1
        continue
      }
      seed += 1
      const heic = isHeicImage(file.name)
      const task: UploadTask = {
        id: seed,
        file,
        previewUrl: createObjectUrl(file),
        fileName: file.name,
        size: file.size,
        status: 'queued',
        phase: 'detecting',
        isHeic: heic,
        progress: 0,
        errorCode: null,
        errorMessage: null,
        albumId,
      }

      if (isTooLarge(file.size)) {
        task.status = 'error'
        task.phase = 'failed'
        task.errorCode = 41301
        task.errorMessage = '文件超过 20MB'
      }
      accepted.push(task)
    }

    if (gifCount > 0) options.onRejectedGif?.(gifCount)

    tasks.value = [...tasks.value, ...accepted].slice(-MAX_TASKS)
    if (startedAt === 0) startedAt = Date.now()
    trim()
    start(albumId)
    notify()
    return seed
  }

  /** 已完成条目过多时裁剪最旧的「已成功」条目。 */
  function trim(): void {
    if (tasks.value.length <= MAX_TASKS) return
    const overflow = tasks.value.length - MAX_TASKS
    const removable = tasks.value.filter((task) => task.status === 'done').slice(0, overflow)
    for (const task of removable) revokeObjectUrl(task.previewUrl)
    const removedIds = new Set(removable.map((task) => task.id))
    tasks.value = tasks.value.filter((task) => !removedIds.has(task.id))
  }

  function update(id: number, patch: Partial<UploadTask>): void {
    tasks.value = tasks.value.map((task) => (task.id === id ? { ...task, ...patch } : task))
  }

  function start(albumId: number | null): void {
    if (pumping) return
    pumping = true
    void pump(albumId)
  }

  /** 正在传输的任务 id 集合，用于并发控制与「全部结束」判定。 */
  const runningIds = new Set<number>()
  let wake: (() => void) | null = null

  function notify(): void {
    const resolver = wake
    wake = null
    resolver?.()
  }

  /**
   * 调度器：保持最多 `CONCURRENCY` 个在传任务。
   *
   * <p>空闲时用一个挂起的 Promise 等待「有任务完成」或「有新任务入队」的事件唤醒，
   * 避免轮询空转。</p>
   */
  async function pump(albumId: number | null): Promise<void> {
    try {
      for (;;) {
        if (runningIds.size >= CONCURRENCY) {
          await new Promise<void>((resolve) => {
            wake = resolve
          })
          continue
        }
        const next = tasks.value.find((task) => task.status === 'queued')
        if (!next) {
          if (runningIds.size === 0) break
          await new Promise<void>((resolve) => {
            wake = resolve
          })
          continue
        }
        runningIds.add(next.id)
        void upload(next, albumId).finally(() => {
          runningIds.delete(next.id)
          notify()
        })
      }
    } finally {
      pumping = false
      if (activeCount.value === 0) options.onSettled?.()
    }
  }

  async function upload(task: UploadTask, albumId: number | null): Promise<void> {
    const controller = new AbortController()
    controllers.set(task.id, controller)
    progress.registerController(task.id, controller)
    progress.begin(task.id)
    // ① 识别中结束 → HEIC 进入 ② 转码中，其它格式进入瞬时「上传中」子状态。
    update(task.id, {
      status: 'uploading',
      phase: task.isHeic ? 'transcoding' : 'uploading',
      progress: 0,
      errorCode: null,
      errorMessage: null,
    })

    try {
      const result = await photoApi.uploadPhoto(
        task.file,
        albumId,
        (percent) => {
          const phase: UploadPhase = task.isHeic && percent < 100 ? 'transcoding' : 'uploading'
          update(task.id, { progress: percent, phase })
        },
        controller.signal,
      )

      const failure = result.failures[0]
      if (failure) {
        // 逐字渲染服务端 message（50003 / 50301 同为 §9.2 ④ 权威文案）。
        markFailed(task.id, failure.code, failure.message)
        return
      }

      const photo = result.successes[0]
      if (!photo) {
        markFailed(task.id, -1, '服务端未返回照片，请重试')
        return
      }

      uploadedBytes += task.size
      update(task.id, { status: 'done', phase: 'done', progress: 100 })
      options.onUploaded?.(photo)
      trim()
    } catch (error) {
      if (error instanceof ApiError) {
        markFailed(task.id, error.code, error.message)
        return
      }
      markFailed(task.id, -1, '上传失败，请检查网络后重试')
    } finally {
      controllers.delete(task.id)
      progress.unregisterController(task.id)
      progress.end(task.id)
    }
  }

  function markFailed(id: number, code: number, message: string): void {
    update(id, { status: 'error', phase: 'failed', errorCode: code, errorMessage: message })
    const task = tasks.value.find((item) => item.id === id)
    if (task) options.onFailed?.(task)
  }

  function retry(id: number): void {
    const task = tasks.value.find((item) => item.id === id)
    if (!task) return
    // 重新入队并回到 ① 识别中
    update(id, {
      status: 'queued',
      phase: 'detecting',
      progress: 0,
      errorCode: null,
      errorMessage: null,
    })
    start(task.albumId ?? null)
  }

  function cancel(id: number): void {
    controllers.get(id)?.abort()
    controllers.delete(id)
    progress.unregisterController(id)
    progress.end(id)
    const task = tasks.value.find((item) => item.id === id)
    if (!task) return
    if (task.status === 'uploading' || task.status === 'queued') {
      update(id, { status: 'error', phase: 'failed', errorCode: -2, errorMessage: '已取消上传' })
    }
  }

  function cancelAll(): void {
    for (const task of tasks.value) {
      if (task.status === 'uploading' || task.status === 'queued') cancel(task.id)
    }
  }

  function clearFinished(): void {
    const kept = tasks.value.filter((task) => task.status === 'uploading' || task.status === 'queued')
    for (const task of tasks.value) {
      if (!kept.includes(task)) revokeObjectUrl(task.previewUrl)
    }
    tasks.value = kept
    if (kept.length === 0) {
      startedAt = 0
      uploadedBytes = 0
    }
  }

  function dispose(): void {
    for (const controller of controllers.values()) controller.abort()
    controllers.clear()
    for (const task of tasks.value) {
      progress.unregisterController(task.id)
      progress.end(task.id)
      revokeObjectUrl(task.previewUrl)
    }
    tasks.value = []
  }

  return {
    tasks,
    summary,
    activeCount,
    failedCount,
    isUploading,
    addFiles,
    start,
    retry,
    cancel,
    cancelAll,
    clearFinished,
    dispose,
  }
}

/** 调度状态 → 中文标签（兼容旧调用点）。 */
export const UPLOAD_STATUS_TEXT: Record<UploadStatus, string> = {
  queued: '等待中',
  uploading: '上传中',
  done: '已完成',
  error: '失败',
}

/**
 * 用户可见状态文案（docs/06 §9.2 四态 + 瞬时子状态）。
 * 失败态由调用方直接用 `task.errorMessage`（服务端原文），不在此映射。
 */
export const UPLOAD_PHASE_TEXT: Record<UploadPhase, string> = {
  queued: '等待上传',
  detecting: '正在识别照片格式…',
  transcoding: '正在转换 HEIC 为通用格式…',
  uploading: '正在上传…',
  done: '已上传',
  failed: '上传失败',
}
