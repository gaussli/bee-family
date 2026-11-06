import { http } from './http'
import type { PageResponse } from '@/types/api'
import type {
  FamilyLookupResp,
  JoinRequestResp,
  OnboardingStateResp,
  PendingCountResp,
} from '@/types/models'

export interface LookupBody {
  familyCode: string
}

export interface CreateJoinBody {
  familyCode: string
  message?: string
}

export interface RejectBody {
  reason?: string
}

/** 家庭码校验：只回显家庭名；码无效 / 已重置 / 家庭已满 / 已删除统一 valid=false。 */
export function lookupFamilyCode(body: LookupBody): Promise<FamilyLookupResp> {
  return http.post<FamilyLookupResp>('/family-code/lookup', body)
}

/** 提交加入申请：成功后进入待审核态（仅回显家庭名）。 */
export function createJoinRequest(body: CreateJoinBody): Promise<JoinRequestResp> {
  return http.post<JoinRequestResp>('/join-requests', body)
}

/** 我自己的申请状态（手动刷新时调用；页面不做轮询）。 */
export function getMine(): Promise<OnboardingStateResp> {
  return http.get<OnboardingStateResp>('/join-requests/mine')
}

/** 撤回申请（确认后回到被拒 / 过期态） */
export function withdrawJoinRequest(id: number): Promise<JoinRequestResp> {
  return http.post<JoinRequestResp>(`/join-requests/${id}/withdraw`)
}

/** 申请列表（仅 owner + admin；两类角色返回体完全一致） */
export function listJoinRequests(
  status: 'pending' | 'handled',
  page = 0,
  size = 20,
): Promise<PageResponse<JoinRequestResp>> {
  return http.get<PageResponse<JoinRequestResp>>('/family/join-requests', {
    params: { status, page, size },
  })
}

/** 待审数量（顶栏 / TabBar 角标） */
export function getPendingCount(): Promise<PendingCountResp> {
  return http.get<PendingCountResp>('/family/join-requests/pending-count')
}

/** 审批通过（owner + admin；满 6 人时返回 40903） */
export function approveJoinRequest(id: number): Promise<JoinRequestResp> {
  return http.post<JoinRequestResp>(`/join-requests/${id}/approve`)
}

/** 审批拒绝（owner + admin；理由选填 ≤100 字） */
export function rejectJoinRequest(id: number, body: RejectBody): Promise<JoinRequestResp> {
  return http.post<JoinRequestResp>(`/join-requests/${id}/reject`, body)
}
