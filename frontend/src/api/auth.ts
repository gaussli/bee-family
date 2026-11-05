import { http } from './http'
import type {
  Family,
  InviteCode,
  Member,
  MePayload,
  TokenPayload,
  User,
} from '@/types/models'

export interface RegisterBody {
  email: string
  password: string
  nickname: string
  inviteCode?: string
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

/** 注册（首个用户自动建家庭，其余需有效邀请码） */
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

/** 当前用户 + 家庭 + 宝宝上下文 */
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

/** 家庭信息（MePayload 内已含，此处供设置页刷新用） */
export function familyInfo(): Promise<Family> {
  return http.get<Family>('/family')
}

/** 成员列表 */
export function members(): Promise<Member[]> {
  return http.get<Member[]>('/family/members')
}

/** 邀请码 */
export function inviteCode(): Promise<InviteCode> {
  return http.get<InviteCode>('/family/invite-code')
}
