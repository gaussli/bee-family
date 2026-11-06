import { http } from './http'
import type { MePayload, TokenPayload, User } from '@/types/models'

/** 注册请求体：3 字段，不填任何码（docs/06 §9.5 / docs/07 §5.3.1）。 */
export interface RegisterBody {
  email: string
  password: string
  nickname: string
}

export interface LoginBody {
  email: string
  password: string
}

export interface UpdateProfileBody {
  nickname?: string
  avatarUrl?: string | null
}

export interface ChangePasswordBody {
  oldPassword: string
  newPassword: string
}

/** 注册：成功后 0 家庭，前端跳 /onboarding 状态 A。 */
export function register(body: RegisterBody): Promise<TokenPayload> {
  return http.post<TokenPayload>('/auth/register', body)
}

/** 登录 */
export function login(body: LoginBody): Promise<TokenPayload> {
  return http.post<TokenPayload>('/auth/login', body)
}

/** 登出（服务端写入 token_valid_after，本地必须清 token） */
export function logout(): Promise<void> {
  return http.post<void>('/auth/logout')
}

/** 当前用户上下文：无家庭亦可访问，返回 onboardingState / families / pendingRequest。 */
export function me(): Promise<MePayload> {
  return http.get<MePayload>('/auth/me')
}

/** 更新个人资料 */
export function updateProfile(body: UpdateProfileBody): Promise<User> {
  return http.patch<User>('/auth/me', body)
}

/** 修改密码（成功后所有旧 token 失效） */
export function changePassword(body: ChangePasswordBody): Promise<void> {
  return http.put<void>('/auth/password', body)
}
