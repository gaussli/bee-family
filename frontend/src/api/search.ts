import { http } from './http'
import type { SearchGroup } from '@/types/search'
import type { Tag } from '@/types/models'

export type SearchType = 'all' | 'photo' | 'album' | 'milestone'

/** 全局搜索（关键词由 axios params 自动 URL 编码） */
export function search(q: string, type: SearchType = 'all', limit = 20): Promise<SearchGroup> {
  return http.get<SearchGroup>('/search', { params: { q, type, limit } })
}

/** 标签列表（q 为空则按最近使用倒序） */
export function listTags(q = ''): Promise<Tag[]> {
  return http.get<Tag[]>('/tags', { params: q ? { q } : {} })
}
