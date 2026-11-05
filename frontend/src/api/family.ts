import { http } from './http'
import type { Family, InviteCode, Member } from '@/types/models'

/** 家庭详情 */
export function getFamily(): Promise<Family> {
  return http.get<Family>('/family')
}

/** 修改家庭名（仅 owner） */
export function updateFamily(name: string): Promise<Family> {
  return http.patch<Family>('/family', { name })
}

/** 删除家庭（危险操作，confirmName 必须完全一致） */
export function deleteFamily(confirmName: string): Promise<void> {
  return http.delete<void>('/family', { data: { confirmName } })
}

/** 成员列表 */
export function listMembers(): Promise<Member[]> {
  return http.get<Member[]>('/family/members')
}

/** 移除成员（仅 owner，且不能移除自己） */
export function removeMember(userId: number): Promise<void> {
  return http.delete<void>(`/family/members/${userId}`)
}

/** 当前邀请码 */
export function getInviteCode(): Promise<InviteCode> {
  return http.get<InviteCode>('/family/invite-code')
}

/** 重置邀请码（旧码立即失效） */
export function resetInviteCode(): Promise<InviteCode> {
  return http.post<InviteCode>('/family/invite-code/reset')
}
