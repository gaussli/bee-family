import { http } from './http'
import type { PageResponse } from '@/types/api'
import type { GrowthCurve, GrowthRange, GrowthRecord } from '@/types/models'

export interface GrowthBody {
  recordedAt: string
  heightCm?: number | null
  weightKg?: number | null
  headCm?: number | null
  note?: string | null
}

/** 成长记录分页（时间倒序） */
export function listRecords(
  babyId: number,
  page = 0,
  size = 20,
): Promise<PageResponse<GrowthRecord>> {
  return http.get<PageResponse<GrowthRecord>>(`/babies/${babyId}/growth-records`, {
    params: { page, size },
  })
}

/** 新增 / 覆盖某日测量（同 (babyId, recordedAt) UPSERT） */
export function upsertRecord(babyId: number, body: GrowthBody): Promise<GrowthRecord> {
  return http.post<GrowthRecord>(`/babies/${babyId}/growth-records`, body)
}

/** 成长曲线（range: 3m / 6m / 12m / all） */
export function getCurve(babyId: number, range: GrowthRange): Promise<GrowthCurve> {
  return http.get<GrowthCurve>(`/babies/${babyId}/growth-curve`, { params: { range } })
}

/** 编辑某条记录 */
export function updateRecord(id: number, body: GrowthBody): Promise<GrowthRecord> {
  return http.patch<GrowthRecord>(`/growth-records/${id}`, body)
}

/** 删除某条记录 */
export function deleteRecord(id: number): Promise<void> {
  return http.delete<void>(`/growth-records/${id}`)
}
