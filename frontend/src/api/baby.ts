import { http } from './http'
import type { Baby, BabyDetail, Gender } from '@/types/models'

export interface BabyBody {
  name: string
  gender: Gender | null
  birthday: string
  avatarUrl?: string | null
  birthHeightCm?: number | null
  birthWeightKg?: number | null
}

/** 宝宝列表（按 id 升序） */
export function listBabies(): Promise<Baby[]> {
  return http.get<Baby[]>('/babies')
}

/** 宝宝详情（含最近成长与最近里程碑） */
export function getBaby(id: number): Promise<BabyDetail> {
  return http.get<BabyDetail>(`/babies/${id}`)
}

/** 新建宝宝（上限 3 个） */
export function createBaby(body: BabyBody): Promise<Baby> {
  return http.post<Baby>('/babies', body)
}

/** 全量更新宝宝档案 */
export function updateBaby(id: number, body: BabyBody): Promise<Baby> {
  return http.patch<Baby>(`/babies/${id}`, body)
}

/** 删除宝宝（仅 owner，级联清理成长记录） */
export function deleteBaby(id: number): Promise<void> {
  return http.delete<void>(`/babies/${id}`)
}
