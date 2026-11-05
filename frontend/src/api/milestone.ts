import { http } from './http'
import type { PageResponse } from '@/types/api'
import type { Milestone } from '@/types/models'

export interface MilestoneBody {
  babyId: number | null
  title: string
  description?: string | null
  achievedAt: string
  photoId?: number | null
}

/** 里程碑分页（achievedAt 倒序；babyId 为空则整个家庭） */
export function listMilestones(
  babyId: number | null,
  page = 0,
  size = 20,
): Promise<PageResponse<Milestone>> {
  return http.get<PageResponse<Milestone>>('/milestones', {
    params: babyId === null ? { page, size } : { babyId, page, size },
  })
}

/** 新建里程碑 */
export function createMilestone(body: MilestoneBody): Promise<Milestone> {
  return http.post<Milestone>('/milestones', body)
}

/** 全量更新里程碑 */
export function updateMilestone(id: number, body: MilestoneBody): Promise<Milestone> {
  return http.patch<Milestone>(`/milestones/${id}`, body)
}

/** 删除里程碑 */
export function deleteMilestone(id: number): Promise<void> {
  return http.delete<void>(`/milestones/${id}`)
}
