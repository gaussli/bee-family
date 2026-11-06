import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { TOKEN_KEY, clearActiveBabyKeys } from '@/api/http'
import type {
  Baby,
  Family,
  FamilyBrief,
  MePayload,
  OnboardingState,
  PendingRequest,
  Role,
  TokenPayload,
  User,
} from '@/types/models'

/**
 * 登录态与「我是谁、我在哪个家庭、我有哪些家庭」。
 *
 * <p>只有 token 持久化到 localStorage；user / family / families 每次启动由
 * `/auth/me` 拉取，避免本地缓存与后端漂移。</p>
 *
 * <p>v1.0 后端尚未返回 `families` / `onboardingState` 等新字段时，
 * `applyMe` 会用真实的 `family` 响应归一出一条 FamilyBrief，
 * 保证「有家庭」判定不被误判为无家庭（仅形状归一，不造 mock）。</p>
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>('')
  const user = ref<User | null>(null)
  const family = ref<Family | null>(null)
  const role = ref<Role | null>(null)
  const babies = ref<Baby[]>([])
  const activeFamilyId = ref<number | null>(null)
  const families = ref<FamilyBrief[]>([])
  const onboardingState = ref<OnboardingState>('NEW')
  const pendingRequestCount = ref<number>(0)
  const pendingRequest = ref<PendingRequest | null>(null)
  const historyRequestCount = ref<number>(0)
  const bootstrapped = ref(false)
  const loading = ref(false)

  const isLoggedIn = computed<boolean>(() => token.value !== '')
  const isOwner = computed<boolean>(() => role.value === 'owner')
  const isAdmin = computed<boolean>(() => role.value === 'admin')
  /** 审批 / 申请列表 / 待审角标对 owner + admin 均可见（D-1 / D-2）。 */
  const canReview = computed<boolean>(() => role.value === 'owner' || role.value === 'admin')
  const nickname = computed<string>(() => user.value?.nickname ?? '')
  /** 无家庭态：仅在上下文成功拉取后才判定为真，避免网络失败误拦截。 */
  const noFamily = computed<boolean>(() => bootstrapped.value && families.value.length === 0)

  const activeBrief = computed<FamilyBrief | null>(
    () => families.value.find((item) => item.familyId === activeFamilyId.value) ?? null,
  )
  const currentFamilyName = computed<string>(
    () => activeBrief.value?.name ?? family.value?.name ?? '我的家庭',
  )
  const currentRole = computed<Role | null>(() => activeBrief.value?.role ?? role.value)
  const currentPendingReviewCount = computed<number>(() => activeBrief.value?.pendingReviewCount ?? 0)

  /** 从 localStorage 恢复 token（幂等） */
  function restore(): void {
    if (token.value) return
    try {
      token.value = localStorage.getItem(TOKEN_KEY) ?? ''
    } catch {
      token.value = ''
    }
  }

  function persistToken(value: string): void {
    token.value = value
    try {
      if (value) localStorage.setItem(TOKEN_KEY, value)
      else localStorage.removeItem(TOKEN_KEY)
    } catch {
      // 隐私模式下 localStorage 不可写：仅内存态仍可用
    }
  }

  /**
   * 把 MeResp / TokenResp / 切换家庭响应统一应用到 store。
   * 字段缺失时按 v1.0 响应形状兜底，保证预览与升级期可用。
   */
  function applyMe(payload: MePayload | TokenPayload): void {
    user.value = payload.user
    role.value = payload.role ?? null
    babies.value = payload.babies ?? []
    family.value = payload.family ?? null
    activeFamilyId.value = payload.activeFamilyId ?? payload.family?.id ?? null
    onboardingState.value = payload.onboardingState ?? 'NEW'
    pendingRequestCount.value = payload.pendingRequestCount ?? 0
    historyRequestCount.value = 0
    pendingRequest.value = null

    const mePayload = payload as MePayload
    pendingRequest.value = mePayload.pendingRequest ?? null
    historyRequestCount.value = mePayload.historyRequestCount ?? 0

    const rawFamilies = payload.families ?? []
    if (rawFamilies.length > 0) {
      families.value = rawFamilies.map((item) => ({
        ...item,
        isActive: item.isActive ?? item.familyId === activeFamilyId.value,
      }))
    } else if (family.value) {
      // v1.0 形状归一：用真实 family 响应构造单条 brief。
      families.value = [
        {
          familyId: family.value.id,
          name: family.value.name,
          role: role.value ?? 'member',
          isActive: true,
          memberCount: family.value.memberCount,
          pendingReviewCount: null,
        },
      ]
    } else {
      families.value = []
    }
    bootstrapped.value = true
  }

  /** 登录响应只带部分上下文，先占位，随后由 fetchMe 补齐。 */
  function applyContext(payload: TokenPayload): void {
    applyMe(payload)
  }

  async function login(email: string, password: string): Promise<void> {
    loading.value = true
    try {
      const payload = await authApi.login({ email, password })
      persistToken(payload.token)
      applyContext(payload)
      await safeFetchMe()
    } finally {
      loading.value = false
    }
  }

  async function register(body: authApi.RegisterBody): Promise<void> {
    loading.value = true
    try {
      const payload = await authApi.register(body)
      persistToken(payload.token)
      applyContext(payload)
      await safeFetchMe()
    } finally {
      loading.value = false
    }
  }

  /** 上下文拉取失败不抛：401 已由拦截器处理，守卫会在下次导航重试。 */
  async function safeFetchMe(): Promise<void> {
    try {
      await fetchMe()
    } catch {
      // 静默：bootstrapped 保持 false，路由守卫会再拉一次
    }
  }

  async function fetchMe(): Promise<void> {
    applyMe(await authApi.me())
  }

  /** 手动刷新无家庭引导状态（待审核态只手动刷新，不做轮询）。 */
  async function refreshOnboarding(): Promise<void> {
    await fetchMe()
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
      if (token.value) await authApi.logout()
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
    activeFamilyId.value = null
    families.value = []
    onboardingState.value = 'NEW'
    pendingRequestCount.value = 0
    pendingRequest.value = null
    historyRequestCount.value = 0
    bootstrapped.value = false
    clearActiveBabyKeys()
  }

  return {
    token,
    user,
    family,
    role,
    babies,
    activeFamilyId,
    families,
    onboardingState,
    pendingRequestCount,
    pendingRequest,
    historyRequestCount,
    bootstrapped,
    loading,
    isLoggedIn,
    isOwner,
    isAdmin,
    canReview,
    nickname,
    noFamily,
    activeBrief,
    currentFamilyName,
    currentRole,
    currentPendingReviewCount,
    restore,
    applyMe,
    applyContext,
    login,
    register,
    fetchMe,
    refreshOnboarding,
    updateProfile,
    changePassword,
    logout,
    clearLocal,
  }
})
