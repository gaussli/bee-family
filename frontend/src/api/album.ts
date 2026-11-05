import { http } from './http'
import type { Album, AlbumDetail, AlbumList, DeleteAlbumResult } from '@/types/models'

/** 相册删除时照片的处置策略 */
export type PhotoStrategy = 'move' | 'delete'

export interface AlbumBody {
  name: string
  description?: string | null
}

export interface AlbumUpdateBody {
  name?: string
  description?: string | null
  coverPhotoId?: number | null
}

/** 相册列表 + 未分类汇总 */
export function listAlbums(): Promise<AlbumList> {
  return http.get<AlbumList>('/albums')
}

/** 相册详情 */
export function getAlbum(id: number): Promise<AlbumDetail> {
  return http.get<AlbumDetail>(`/albums/${id}`)
}

/** 新建相册（同名 40905） */
export function createAlbum(body: AlbumBody): Promise<Album> {
  return http.post<Album>('/albums', body)
}

/** 更新相册（设置封面 / 改名 / 改描述） */
export function updateAlbum(id: number, body: AlbumUpdateBody): Promise<Album> {
  return http.patch<Album>(`/albums/${id}`, body)
}

/** 删除相册，并决定其中照片的走向 */
export function deleteAlbum(id: number, photoStrategy: PhotoStrategy): Promise<DeleteAlbumResult> {
  return http.delete<DeleteAlbumResult>(`/albums/${id}`, { params: { photoStrategy } })
}
