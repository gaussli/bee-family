import { http } from './http'
import type { PageResponse } from '@/types/api'
import type { BatchResult, Photo, PhotoDetail, PhotoUploadResult } from '@/types/models'

/** 照片排序白名单（与后端 PhotoQuery 一致） */
export type PhotoSort = 'takenAt_desc' | 'takenAt_asc' | 'createdAt_desc'

export interface PhotoListQuery {
  albumId?: number | null
  unclassified?: boolean
  page?: number
  size?: number
  sort?: PhotoSort
}

export interface PhotoUpdateBody {
  title?: string | null
  description?: string | null
  takenAt?: string | null
  locationName?: string | null
  albumId?: number | null
  /** 完整集合语义：[] 清空，undefined/null 不改 */
  tags?: string[]
}

/** 照片分页（相册内 / 未分类 / 全家庭） */
export function listPhotos(query: PhotoListQuery = {}): Promise<PageResponse<Photo>> {
  const params: Record<string, string | number | boolean> = {
    page: query.page ?? 0,
    size: query.size ?? 60,
    sort: query.sort ?? 'takenAt_desc',
  }
  if (query.albumId !== undefined && query.albumId !== null) {
    params.albumId = query.albumId
  }
  if (query.unclassified) {
    params.unclassified = true
  }
  return http.get<PageResponse<Photo>>('/photos', { params })
}

/** 照片详情 */
export function getPhoto(id: number): Promise<PhotoDetail> {
  return http.get<PhotoDetail>(`/photos/${id}`)
}

/**
 * 上传单张照片。
 *
 * <p>服务端始终返回 HTTP 200，逐张成败在 `successes[] / failures[]`。
 * `failures[].message` 是服务端逐字下发的用户可见文案（含 HEIC 转码失败的 iPhone
 * 降级指引），调用方**直接展示**，不得按 code 做本地文案映射（docs/07 §9.9）。</p>
 *
 * @param file       文件
 * @param albumId    目标相册（null = 未分类）
 * @param onProgress 进度回调 0-100
 * @param signal     取消信号
 */
export function uploadPhoto(
  file: File,
  albumId: number | null,
  onProgress?: (percent: number) => void,
  signal?: AbortSignal,
): Promise<PhotoUploadResult> {
  const form = new FormData()
  form.append('files', file)
  if (albumId !== null) {
    form.append('albumId', String(albumId))
  }
  return http.post<PhotoUploadResult>('/photos/upload', form, {
    signal,
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) {
        return
      }
      onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)))
    },
  })
}

/** 更新照片（PATCH 全量语义） */
export function updatePhoto(id: number, body: PhotoUpdateBody): Promise<Photo> {
  return http.patch<Photo>(`/photos/${id}`, body)
}

/** 删除单张 */
export function deletePhoto(id: number): Promise<void> {
  return http.delete<void>(`/photos/${id}`)
}

/** 批量删除（≤100） */
export function batchDelete(photoIds: number[]): Promise<BatchResult> {
  return http.post<BatchResult>('/photos/batch-delete', { photoIds })
}

/** 批量移动（albumId=null → 未分类） */
export function batchMove(photoIds: number[], albumId: number | null): Promise<BatchResult> {
  return http.post<BatchResult>('/photos/batch-move', { photoIds, albumId })
}
