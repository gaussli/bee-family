import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as babyApi from '@/api/baby'
import * as familyApi from '@/api/family'
import { ACTIVE_BABY_KEY } from '@/api/http'
import type { Baby, Family, InviteCode, Member } from '@/types/models'

/**
 * 家庭空间：家庭信息、成员、邀请码、宝宝列表与当前宝宝。
 *
 * <p>`activeBabyId` 持久化到 localStorage（跨页面保持选择）。</p>
 */
export const useFamilyStore = defineStore('family', () => {
  const family = ref<Family | null>(null)
  const members = ref<Member[]>([])
  const inviteCode = ref<InviteCode | null>(null)
  const babies = ref<Baby[]>([])
  const activeBabyId = ref<number | null>(readActiveBaby())
  const loading = ref(false)

  const memberCount = computed(() => family.value?.memberCount ?? members.value.length)
  const memberLimit = computed(() => family.value?.memberLimit ?? 6)
  const isFull = computed(() => memberCount.value >= memberLimit.value)
  const activeBaby = computed<Baby | null>(
    () => babies.value.find((item) => item.id === activeBabyId.value) ?? babies.value[0] ?? null,
  )

  function readActiveBaby(): number | null {
    try {
      const raw = localStorage.getItem(ACTIVE_BABY_KEY)
      return raw ? Number(raw) : null
    } catch {
      return null
    }
  }

  function setActiveBaby(id: number | null): void {
    activeBabyId.value = id
    try {
      if (id === null) {
        localStorage.removeItem(ACTIVE_BABY_KEY)
      } else {
        localStorage.setItem(ACTIVE_BABY_KEY, String(id))
      }
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

  async function fetchInviteCode(): Promise<void> {
    inviteCode.value = await familyApi.getInviteCode()
  }

  async function resetInviteCode(): Promise<void> {
    inviteCode.value = await familyApi.resetInviteCode()
  }

  async function fetchBabies(): Promise<void> {
    babies.value = await babyApi.listBabies()
    if (babies.value.length > 0) {
      const exists = babies.value.some((item) => item.id === activeBabyId.value)
      if (!exists) {
        setActiveBaby(babies.value[0]?.id ?? null)
      }
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
    if (activeBabyId.value === id) {
      setActiveBaby(babies.value[0]?.id ?? null)
    }
  }

  async function loadAll(): Promise<void> {
    loading.value = true
    try {
      await Promise.all([fetchFamily(), fetchMembers(), fetchBabies()])
      const canSeeInvite = family.value?.inviteCode !== null && family.value?.inviteCode !== undefined
      if (canSeeInvite) {
        await fetchInviteCode()
      }
    } finally {
      loading.value = false
    }
  }

  function reset(): void {
    family.value = null
    members.value = []
    inviteCode.value = null
    babies.value = []
    activeBabyId.value = null
  }

  return {
    family,
    members,
    inviteCode,
    babies,
    activeBabyId,
    loading,
    memberCount,
    memberLimit,
    isFull,
    activeBaby,
    setActiveBaby,
    fetchFamily,
    updateFamily,
    fetchMembers,
    removeMember,
    fetchInviteCode,
    resetInviteCode,
    fetchBabies,
    createBaby,
    updateBaby,
    deleteBaby,
    loadAll,
    reset,
  }
})
