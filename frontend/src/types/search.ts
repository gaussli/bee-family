/**
 * 搜索结果的类型（后端 SearchResp，与 models 解耦以避免循环引用）。
 */
import type { Album, Milestone, Photo } from './models'

export interface SearchGroupResp<T> {
  total: number
  items: T[]
  hasMore: boolean
}

export interface SearchGroup {
  query: string
  totalCount: number
  photos: SearchGroupResp<Photo>
  albums: SearchGroupResp<Album>
  milestones: SearchGroupResp<Milestone>
}
