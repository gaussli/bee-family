import { http } from './http'
import type { TimelinePage } from '@/types/timeline'

/** 时间轴分页（后端已按 sortTime 倒序 + 同刻 type_ord 排序并已按月分组） */
export function getTimeline(page = 0, size = 24): Promise<TimelinePage> {
  return http.get<TimelinePage>('/timeline', { params: { page, size } })
}
