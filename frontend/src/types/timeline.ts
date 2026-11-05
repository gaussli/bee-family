import type { GrowthRecord, Milestone, Photo } from './models'
import type { PageResponse } from './api'

/** 时间轴条目类型（与后端 type 字段一致） */
export type TimelineItemType = 'photo' | 'milestone' | 'growth'

export interface TimelineItem {
  type: TimelineItemType
  id: number
  /** "yyyy-MM-dd HH:mm:ss" */
  sortTime: string
  photo: Photo | null
  milestone: Milestone | null
  growth: GrowthRecord | null
}

export interface TimelineGroup {
  /** "yyyy-MM" */
  month: string
  /** "2026 年 10 月"（后端生成，前端不自拼） */
  label: string
  items: TimelineItem[]
}

/** 后端 TimelinePageResp：groups 与 PageResponse 字段并列 */
export interface TimelinePage extends Omit<PageResponse<TimelineGroup>, 'items'> {
  groups: TimelineGroup[]
}
