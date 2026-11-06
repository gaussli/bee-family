import { http } from './http'
import type { Family, FamilyBrief, FamilyCode, Member, PendingCountResp, Role } from '@/types/models'

/** 家庭详情（含对所有成员可见的 10 位家庭码） */
export function getFamily(): Promise<Family> {
  return http.get<Family>('/family')
}

/** 修改家庭名（仅 owner） */
export function updateFamily(name: string): Promise<Family> {
  return http.patch<Family>('/family', { name })
}

/** 删除家庭（危险操作，confirmName 必须完全一致；仅 owner） */
export function deleteFamily(confirmName: string): Promise<void> {
  return http.delete<void>('/family', { data: { confirmName } })
}

/** 成员列表（role 三值） */
export function listMembers(): Promise<Member[]> {
  return http.get<Member[]>('/family/members')
}

/** 移除成员（仅 owner） */
export function removeMember(userId: number): Promise<void> {
  return http.delete<void>(`/family/members/${userId}`)
}

/** 指定 / 撤销管理员（仅 owner） */
export function setMemberRole(userId: number, role: 'admin' | 'member'): Promise<Member> {
  return http.patch<Member>(`/family/members/${userId}/role`, { role })
}

/** 当前 10 位家庭码（永久有效，仅 owner 可重置）。 */
export function getFamilyCode(): Promise<FamilyCode> {
  return http.get<FamilyCode>('/family/family-code')
}

/** 重置家庭码（仅 owner；旧码立即失效，既有申请与成员不受影响） */
export function resetFamilyCode(): Promise<FamilyCode> {
  return http.post<FamilyCode>('/family/family-code/reset')
}

/** 已加入家庭列表（切换器数据源） */
export function listFamilies(): Promise<FamilyBrief[]> {
  return http.get<FamilyBrief[]>('/families')
}

/** 创建家庭（任意登录用户，含 0 家庭；创建者自动成为 owner） */
export function createFamily(name: string): Promise<Family> {
  return http.post<Family>('/families', { name })
}

/** 待审申请数量（仅 owner + admin；member 调用会返回 40305，故 UI 不调用） */
export function getPendingCount(): Promise<PendingCountResp> {
  return http.get<PendingCountResp>('/family/join-requests/pending-count')
}

/** 家庭内角色 → 中文标签（统一在 UI 层复用，避免各页硬写）。 */
export function roleLabel(role: Role): string {
  if (role === 'owner') return '创建者'
  if (role === 'admin') return '管理员'
  return '成员'
}
