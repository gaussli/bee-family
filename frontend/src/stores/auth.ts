import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { ACTIVE_BABY_KEY, TOKEN_KEY } from '@/api/http'
import type { Baby, Family, Role, User } from '@/types/models'

/**
 * 登录态与「我是谁、我在哪个家庭」。
 *
 * <p>只有 token 持久化到 localStorage；user/family 每次启动由 `/auth/me` 拉取，
 * 避免本地缓存与后端漂移。</p>
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>('')
  const user = ref<User | null>(null)
  const family = ref<Family | null>(null)
  const role = ref<Role | null>(null)
  const babies = ref<Baby[]>([])
  const bootstrapped = ref(false)
  const loading = ref(false)

  const isLoggedIn = computed(() => token.value !== '')
  const isOwner = computed(() => role.value === 'owner')
  const nickname = computed(() => user.value?.nickname ?? '')

  /** 从 localStorage 恢复 token（幂等） */
  function restore(): void {
    if (token.value) {
      return
    }
    try {
      token.value = localStorage.getItem(TOKEN_KEY) ?? ''
    } catch {
      token.value = ''
    }
  }

  function persistToken(value: string): void {
    token.value = value
    try {
      if (value) {
        localStorage.setItem(TOKEN_KEY, value)
      } else {
        localStorage.removeItem(TOKEN_KEY)
      }
    } catch {
      // 隐私模式下 localStorage 不可写：仅内存态仍可用
    }
  }

  function applyContext(payload: {
    user: User
    familyId: number | null
    role: Role | null
    babies: Baby[]
  }): void {
    user.value = payload.user
    role.value = payload.role
    babies.value = payload.babies
    // familyId 由 /auth/me 的 family 全量信息覆盖；登录响应只带 id/name，先占位
    if (payload.familyId !== null) {
      family.value = family.value && family.value.id === payload.familyId
        ? family.value
        : { ...(family.value ?? ({} as Family)), id: payload.familyId }
    }
  }

  async function login(email: string, password: string): Promise<void> {
    loading.value = true
    try {
      const payload = await authApi.login({ email, password })
      persistToken(payload.token)
      applyContext({
        user: payload.user,
        familyId: payload.familyId,
        role: payload.role,
        babies: payload.babies,
      })
      await fetchMe()
    } finally {
      loading.value = false
    }
  }

  async function register(body: authApi.RegisterBody): Promise<void> {
    loading.value = true
    try {
      const payload = await authApi.register(body)
      persistToken(payload.token)
      applyContext({
        user: payload.user,
        familyId: payload.familyId,
        role: payload.role,
        babies: payload.babies,
      })
      await fetchMe()
    } finally {
      loading.value = false
    }
  }

  async function fetchMe(): Promise<void> {
    const payload = await authApi.me()
    user.value = payload.user
    family.value = payload.family
    role.value = payload.role
    babies.value = payload.babies
    bootstrapped.value = true
  }

  async function updateProfile(body: authApi.UpdateProfileBody): Promise<void> {
    user.value = await authApi.updateProfile(body)
  }

  async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
    await authApi.changePassword({ oldPassword, newPassword })
    // 改密码后所有旧 token 失效（R-3），必须清本地重新登录
    clearLocal()
  }

  async function logout(): Promise<void> {
    try {
      if (token.value) {
        await authApi.logout()
      }
    } catch {
      // 登出失败也必须清本地：服务端 token 已可能被写坏
    } finally {
      clearLocal()
    }
  }

  function clearLocal(): void {
    persistToken('')
    user.value = null
    family.value = null
    role.value = null
    babies.value = []
    bootstrapped.value = false
    try {
      localStorage.removeItem(ACTIVE_BABY_KEY)
    } catch {
      // ignore
    }
  }

  /** 登录响应没有 family 详情时，占位对象补齐 */
  function ensureFamily(): void {
    if (family.value && (family.value.name === undefined || family.value.name === '')) {
      family.value = null
    }
  }

  return {
    token,
    user,
    family,
    role,
    babies,
    bootstrapped,
    loading,
    isLoggedIn,
    isOwner,
    nickname,
    restore,
    login,
    register,
    fetchMe,
    updateProfile,
    changePassword,
    logout,
    clearLocal,
    ensureFamily,
  }
})
