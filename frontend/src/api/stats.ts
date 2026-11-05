import { http } from './http'
import type { Overview } from '@/types/models'

/** 家庭概览统计（实时聚合，无缓存） */
export function getOverview(): Promise<Overview> {
  return http.get<Overview>('/stats/overview')
}
