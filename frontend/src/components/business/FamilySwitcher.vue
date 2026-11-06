<script setup lang="ts">
/**
 * 家庭切换器（docs/06 §7）。
 *
 * <p>≥768 下拉面板；<768 底部选择面板（高 ≤60vh）。
 * 在途上传 N>0 时进入阻断态：不展开下拉，改弹内联提示条（`aria-disabled="true"`）。
 * 切换成功后由 `resetAllForFamily()` 集中做 13 项状态重置并强制回 /timeline。</p>
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as switchApi from '@/api/switch'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeTag from '@/components/base/BeeTag.vue'
import { useAuthStore } from '@/stores/auth'
import { useUploadProgressStore } from '@/stores/upload-progress'
import { useSwitchStore } from '@/stores/switch'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useToast } from '@/composables/useToast'
import { roleLabel } from '@/api/family'
import type { FamilyBrief, Role } from '@/types/models'

interface Props {
  /** <768 精简形态：宽 ≤140px、名 4 字 + chevron、底部选择面板。 */
  compact?: boolean
}

withDefaults(defineProps<Props>(), { compact: false })

const emit = defineEmits<{ viewQueue: [] }>()

const router = useRouter()
const auth = useAuthStore()
const progress = useUploadProgressStore()
const toast = useToast()
const { isMobile } = useBreakpoint()

const open = ref<boolean>(false)
const blockedHint = ref<boolean>(false)
const switchingId = ref<number | null>(null)
const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLButtonElement | null>(null)

const isBlocked = computed<boolean>(() => progress.activeCount > 0)
const activeCount = computed<number>(() => progress.activeCount)
const currentName = computed<string>(() => auth.currentFamilyName)
const currentRole = computed<Role | null>(() => auth.currentRole)
const families = computed<FamilyBrief[]>(() => auth.families)

/** ≥1280 ≤12 字省略；<768 只保留 4 字 + chevron。 */
const displayName = computed<string>(() => {
  const name = currentName.value
  if (isMobile.value && name.length > 4) return `${name.slice(0, 4)}…`
  if (name.length > 12) return `${name.slice(0, 12)}…`
  return name
})

function tagVariant(role: Role): 'honey' | 'outline' | 'default' {
  if (role === 'owner') return 'honey'
  if (role === 'admin') return 'outline'
  return 'default'
}

function toggle(): void {
  if (isBlocked.value) {
    blockedHint.value = !blockedHint.value
    return
  }
  blockedHint.value = false
  open.value = !open.value
}

function close(): void {
  open.value = false
  blockedHint.value = false
}

function focusTrigger(): void {
  triggerRef.value?.focus()
}

function onDocumentPointerDown(event: PointerEvent): void {
  if (!open.value && !blockedHint.value) return
  const root = rootRef.value
  if (root && !root.contains(event.target as Node)) close()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape') return
  if (open.value || blockedHint.value) {
    event.stopPropagation()
    close()
    focusTrigger()
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onKeydown)
})

async function selectFamily(item: FamilyBrief): Promise<void> {
  if (isBlocked.value || switchingId.value !== null || item.isActive) {
    close()
    return
  }
  switchingId.value = item.familyId
  try {
    const payload = await switchApi.switchFamily(item.familyId)
    // 服务端返回 MeResp：先更新上下文，再做 13 项重置 + 强制回时间轴。
    auth.applyMe(payload)
    useSwitchStore().resetAllForFamily(item.familyId)
    close()
    toast.info(`已切换到『${item.name}』`)
  } catch {
    // 失败：下拉保持展开、触发器文字不变更（新家庭名已在成功后才写入）。
    toast.danger('切换失败，请重试')
  } finally {
    switchingId.value = null
  }
}

function goOnboarding(intent: 'create' | 'join'): void {
  close()
  void router.push({ name: 'onboarding', query: { intent } })
}

function viewQueue(): void {
  blockedHint.value = false
  emit('viewQueue')
}
</script>

<template>
  <div ref="rootRef" class="bee-family-switcher" :class="{ 'is-compact': compact || isMobile }">
    <button
      ref="triggerRef"
      class="bee-family-switcher__trigger"
      :class="{ 'is-blocked': isBlocked, 'is-open': open }"
      type="button"
      :aria-disabled="isBlocked ? 'true' : undefined"
      :aria-expanded="open"
      aria-haspopup="menu"
      aria-label="切换家庭"
      @click="toggle"
    >
      <span class="bee-family-switcher__name" :title="currentName">{{ displayName }}</span>
      <BeeIcon v-if="isBlocked" name="upload" :size="12" />
      <BeeIcon v-else name="chevron-down" :size="16" class="bee-family-switcher__chevron" />
    </button>

    <!-- 阻断态内联提示条：N>0 出现，点击外部或 N 归 0 时收起 -->
    <div v-if="blockedHint" class="bee-family-switcher__blocked" role="status" aria-live="polite">
      <p class="bee-family-switcher__blocked-text">
        还有 {{ activeCount }} 张照片正在上传，请等待完成后再切换家庭
      </p>
      <button class="bee-family-switcher__blocked-link" type="button" @click="viewQueue">查看上传队列</button>
    </div>

    <Transition name="bee-switcher">
      <div v-if="open" class="bee-family-switcher__panel" role="menu" aria-label="家庭列表">
        <div class="bee-family-switcher__current">
          <span class="bee-family-switcher__current-name" :title="currentName">{{ currentName }}</span>
          <BeeTag v-if="currentRole" :variant="tagVariant(currentRole)" size="sm">
            {{ roleLabel(currentRole) }}
          </BeeTag>
        </div>
        <div class="bee-family-switcher__list">
          <button
            v-for="item in families"
            :key="item.familyId"
            class="bee-family-switcher__option"
            :class="{ 'is-active': item.isActive }"
            type="button"
            role="menuitem"
            :disabled="switchingId !== null"
            @click="selectFamily(item)"
          >
            <span class="bee-family-switcher__option-name" :title="item.name">{{ item.name }}</span>
            <BeeTag :variant="tagVariant(item.role)" size="sm">{{ roleLabel(item.role) }}</BeeTag>
            <span v-if="switchingId === item.familyId" class="bee-family-switcher__spinner" aria-hidden="true" />
            <BeeIcon v-else-if="item.isActive" name="check" :size="16" class="bee-family-switcher__check" />
          </button>
          <p v-if="families.length === 0" class="bee-family-switcher__empty">暂无家庭</p>
        </div>
        <div class="bee-family-switcher__footer">
          <button class="bee-family-switcher__footer-item" type="button" role="menuitem" @click="goOnboarding('create')">
            <BeeIcon name="plus" :size="16" />创建家庭
          </button>
          <button class="bee-family-switcher__footer-item" type="button" role="menuitem" @click="goOnboarding('join')">
            <BeeIcon name="user" :size="16" />加入家庭
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.bee-family-switcher {
  position: relative;
  flex: none;
  width: 200px;
}

.bee-family-switcher.is-compact {
  width: 140px;
}

.bee-family-switcher__trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  width: 100%;
  height: 36px;
  padding: 0 12px;
  border-radius: var(--r-full);
  background: var(--bg-sunken);
  color: var(--text-primary);
  font-size: var(--fs-body);
  font-weight: 500;
  transition: opacity var(--dur-base) var(--ease-out);
}

.bee-family-switcher__name {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-family-switcher__chevron {
  color: var(--text-tertiary);
  transition: transform var(--dur-base) var(--ease-out);
}

.bee-family-switcher__trigger.is-open .bee-family-switcher__chevron {
  transform: rotate(180deg);
}

/* C-06：仍可点击（用于解释原因），不做真 disabled */
.bee-family-switcher__trigger.is-blocked {
  opacity: 0.45;
  cursor: not-allowed;
}

.bee-family-switcher__blocked {
  position: absolute;
  top: 44px;
  left: 0;
  z-index: var(--z-topbar-dropdown);
  width: 260px;
  padding: var(--sp-3);
  border-radius: var(--r-md);
  background: var(--color-warning-bg);
  box-shadow: var(--shadow-md);
}

.bee-family-switcher__blocked-text {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-warning);
}

.bee-family-switcher__blocked-link {
  margin-top: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--honey-700);
}

.bee-family-switcher__blocked-link:hover {
  text-decoration: underline;
}

.bee-family-switcher__panel {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  z-index: var(--z-topbar-dropdown);
  width: 260px;
  max-height: 280px;
  padding: var(--sp-1);
  background: var(--bg-elevated);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-md);
  overflow-y: auto;
}

.bee-family-switcher__current {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  height: 40px;
  padding: 0 12px;
  margin-bottom: var(--sp-1);
  border-bottom: 1px solid var(--border-subtle);
}

.bee-family-switcher__current-name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-family-switcher__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.bee-family-switcher__option {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;
  height: 48px;
  padding: 0 12px;
  border-radius: var(--r-sm);
  text-align: left;
}

.bee-family-switcher__option:hover:not(:disabled) {
  background: var(--honey-50);
}

.bee-family-switcher__option.is-active {
  background: var(--honey-100);
  color: var(--text-brand);
}

.bee-family-switcher__option-name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-body);
  font-weight: 500;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-family-switcher__check {
  color: var(--honey-600);
  flex: none;
}

.bee-family-switcher__spinner {
  width: 16px;
  height: 16px;
  flex: none;
  border: 2px solid var(--honey-200);
  border-top-color: var(--honey-500);
  border-radius: var(--r-full);
  animation: bee-spin 900ms linear infinite;
}

.bee-family-switcher__empty {
  padding: var(--sp-4);
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  text-align: center;
}

.bee-family-switcher__footer {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: var(--sp-1);
  padding-top: var(--sp-1);
  border-top: 1px solid var(--border-subtle);
}

.bee-family-switcher__footer-item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  height: 36px;
  padding: 0 12px;
  border-radius: var(--r-sm);
  font-size: var(--fs-body);
  color: var(--text-secondary);
  text-align: left;
}

.bee-family-switcher__footer-item:hover {
  background: var(--honey-50);
  color: var(--text-primary);
}

.bee-switcher-enter-active,
.bee-switcher-leave-active {
  transition: opacity var(--dur-base) var(--ease-out);
}

.bee-switcher-enter-from,
.bee-switcher-leave-to {
  opacity: 0;
}

/* <768：底部选择面板，高 ≤60vh，顶部圆角 */
@media (max-width: 767px) {
  .bee-family-switcher__panel {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    top: auto;
    width: auto;
    max-height: 60vh;
    border-radius: var(--r-xl) var(--r-xl) 0 0;
    box-shadow: var(--shadow-lg);
  }

  .bee-family-switcher__blocked {
    width: 260px;
  }
}
</style>
