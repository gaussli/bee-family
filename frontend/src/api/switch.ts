import { http } from './http'
import type { MePayload } from '@/types/models'

export interface SwitchFamilyBody {
  familyId: number
}

/**
 * 切换当前家庭（服务端权威记录 user.active_family_id）。
 *
 * 成功后由 `resetAllForFamily(newFamilyId)` 集中执行 13 项状态重置并强制
 * `router.replace('/timeline')`；本模块只负责发请求，不做任何状态清理（docs/07 §10.3）。
 */
export function switchFamily(familyId: number): Promise<MePayload> {
  return http.post<MePayload>('/families/switch', { familyId } satisfies SwitchFamilyBody)
}
