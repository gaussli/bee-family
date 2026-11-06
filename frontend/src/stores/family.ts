import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as babyApi from '@/api/baby'
import * as familyApi from '@/api/family'
import { activeBabyStorageKey } from '@/api/http'
import type { Baby, Family, FamilyCode, Member } from '@/types/models'

/**
 * 家庭空间：家庭信息、成员、家庭码、宝宝列表与当前宝宝。
 *
 * <p>`activeBabyId` 按 familyId 分键持久化（`bee.activeBabyId.{familyId}`），
 * 切换家庭后禁止沿用旧值（docs/07 §10.3 第 1 / 11 项）。</p>
 * <p>不 import 其它 store：跨域协调集中在 `stores/switch.ts` 与页面组件。</p>
 */
export const useFamilyStore = defineStore('family', () => {
  const family = ref<Family | null>(null)
  const members = ref<Member[]>([])
  const familyCode = ref<FamilyCode | null>(null)
  const babies = ref<Baby[]>([])
  const pendingReviewCount = ref<number>(0)
  const loading = ref<boolean>(false)
  /** 当前 store 绑定的家庭（决定 activeBabyId 的分键）。 */
  const contextFamilyId = ref<number | null>(null)
  const activeBabyId = ref<number | null>(readActiveBaby(null))

  const memberCount = computed<number>(() => family.value?.memberCount ?? members.value.length)
  const memberLimit = computed<number>(() => family.value?.memberLimit ?? 6)
  const isFull = computed<boolean>(() => memberCount.value >= memberLimit.value)
  const activeBaby = computed<Baby | null>(
    () => babies.value.find((item) => item.id === activeBabyId.value) ?? babies.value[0] ?? null,
  )

  function readActiveBaby(familyId: number | null): number | null {
    try {
      const raw = localStorage.getItem(activeBabyStorageKey(familyId))
      if (!raw) return null
      const value = Number(raw)
      return Number.isFinite(value) ? value : null
    } catch {
      return null
    }
  }

  /** 绑定家庭上下文并重新读取该家庭下的 activeBabyId（切换家庭 / 首屏引导）。 */
  function bindFamily(familyId: number | null): void {
    contextFamilyId.value = familyId
    activeBabyId.value = readActiveBaby(familyId)
  }

  function setActiveBaby(id: number | null): void {
    activeBabyId.value = id
    try {
      const key = activeBabyStorageKey(contextFamilyId.value)
      if (id === null) localStorage.removeItem(key)
      else localStorage.setItem(key, String(id))
    } catch {
      // ignore
    }
  }

  async function fetchFamily(): Promise<Family> {
    family.value = await familyApi.getFamily()
    return family.value
  }

  async function updateFamily(name: string): Promise<void> {
    family.value = await familyApi.updateFamily(name)
  }

  async function fetchMembers(): Promise<void> {
    members.value = await familyApi.listMembers()
  }

  async function removeMember(userId: number): Promise<void> {
    await familyApi.removeMember(userId)
    members.value = members.value.filter((item) => item.userId !== userId)
    if (family.value) {
      family.value = { ...family.value, memberCount: Math.max(0, family.value.memberCount - 1) }
    }
  }

  /** 指定 / 撤销管理员后原地更新角色 Chip。 */
  async function setMemberRole(userId: number, role: 'admin' | 'member'): Promise<void> {
    const updated = await familyApi.setMemberRole(userId, role)
    members.value = members.value.map((item) => (item.userId === userId ? updated : item))
  }

  /** 家庭码对所有成员可见（不再有 TTL / 可见性分支）。 */
  async function fetchFamilyCode(): Promise<void> {
    familyCode.value = await familyApi.getFamilyCode()
  }

  async function resetFamilyCode(): Promise<void> {
    familyCode.value = await familyApi.resetFamilyCode()
    if (family.value && familyCode.value) {
      family.value = { ...family.value, familyCode: familyCode.value.code }
    }
  }

  /** 待审角标数据源；调用方须先判断 viewer 为 owner/admin。 */
  async function fetchPendingReviewCount(): Promise<void> {
    const result = await familyApi.getPendingCount()
    pendingReviewCount.value = result.count
  }

  async function fetchBabies(): Promise<void> {
    babies.value = await babyApi.listBabies()
    if (babies.value.length > 0) {
      const exists = babies.value.some((item) => item.id === activeBabyId.value)
      if (!exists) setActiveBaby(babies.value[0]?.id ?? null)
    } else {
      setActiveBaby(null)
    }
  }

  async function createBaby(body: babyApi.BabyBody): Promise<Baby> {
    const created = await babyApi.createBaby(body)
    babies.value = [...babies.value, created]
    setActiveBaby(created.id)
    return created
  }

  async function updateBaby(id: number, body: babyApi.BabyBody): Promise<Baby> {
    const updated = await babyApi.updateBaby(id, body)
    babies.value = babies.value.map((item) => (item.id === id ? updated : item))
    return updated
  }

  async function deleteBaby(id: number): Promise<void> {
    await babyApi.deleteBaby(id)
    babies.value = babies.value.filter((item) => item.id !== id)
    if (activeBabyId.value === id) setActiveBaby(babies.value[0]?.id ?? null)
  }

  async function loadAll(): Promise<void> {
    loading.value = true
    try {
      await Promise.all([
        fetchFamily(),
        fetchMembers(),
        fetchBabies(),
        // 家庭码接口在后端升级前可能不存在：失败不让整个聚合加载崩掉。
        fetchFamilyCode().catch(() => {
          familyCode.value = null
        }),
      ])
      // /family 响应已含 familyCode 时作为兜底（形状归一，不造 mock）。
      if (!familyCode.value && family.value?.familyCode) {
        familyCode.value = { code: family.value.familyCode }
      }
    } finally {
      loading.value = false
    }
  }

  function reset(): void {
    family.value = null
    members.value = []
    familyCode.value = null
    babies.value = []
    pendingReviewCount.value = 0
    activeBabyId.value = null
  }

  return {
    family,
    members,
    familyCode,
    babies,
    pendingReviewCount,
    loading,
    contextFamilyId,
    activeBabyId,
    memberCount,
    memberLimit,
    isFull,
    activeBaby,
    bindFamily,
    setActiveBaby,
    fetchFamily,
    updateFamily,
    fetchMembers,
    removeMember,
    setMemberRole,
    fetchFamilyCode,
    resetFamilyCode,
    fetchPendingReviewCount,
    fetchBabies,
    createBaby,
    updateBaby,
    deleteBaby,
    loadAll,
    reset,
  }
})
