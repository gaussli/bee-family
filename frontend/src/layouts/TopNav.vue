<script setup lang="ts">
/**
 * 顶部导航（≥768 显示；docs/06 §3、§4）。
 *
 * <p>固定 5 项、64px sticky 全宽（`padding: 0 var(--container-pad)`，不套 1200 居中）；
 * 当前态 `--text-brand` 600 + 底部 2px `--honey-500` 下划线；全顶栏仅 1 个
 * `aria-current="page"`；`←`/`→` roving tabindex（到边界停住、不循环）；`Esc` 关下拉。</p>
 *
 * <p>与 <768 的 TabBar 由 AppShell 的 `v-if` 互斥挂载（同一时刻只存在一套主导航），
 * CSS 媒体查询作为第二道保险。SideNav 永久移除，不再有汉堡抽屉。</p>
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BeeAvatar from '@/components/base/BeeAvatar.vue'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import FamilySwitcher from '@/components/business/FamilySwitcher.vue'
import type { IconName } from '@/components/base/icon-paths'
import { useAuthStore } from '@/stores/auth'
import { useFamilyStore } from '@/stores/family'
import { useSwitchStore } from '@/stores/switch'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useToast } from '@/composables/useToast'

const emit = defineEmits<{ upload: []; viewQueue: [] }>()

interface NavItem {
  to: string
  label: string
  icon: IconName
}

/** 顺序与 03 §9.2 路由表、02 §4.1 TabBar 完全一致（D-8：相册复用既有图标）。 */
const NAV_ITEMS: NavItem[] = [
  { to: '/timeline', label: '时间轴', icon: 'grid' },
  { to: '/albums', label: '相册', icon: 'camera' },
  { to: '/growth', label: '成长记录', icon: 'baby' },
  { to: '/family', label: '家庭成员', icon: 'user' },
  { to: '/settings', label: '设置', icon: 'settings' },
]

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const familyStore = useFamilyStore()
const switchStore = useSwitchStore()
const toast = useToast()
const { width } = useBreakpoint()

const focusIndex = ref<number>(0)
const navRefs = ref<(HTMLElement | null)[]>([])
const menuOpen = ref<boolean>(false)
const menuRef = ref<HTMLElement | null>(null)
const searchOverlayOpen = ref<boolean>(false)
const searchInputRef = ref<HTMLInputElement | null>(null)
const overlayInputRef = ref<HTMLInputElement | null>(null)
const searchIconRef = ref<HTMLButtonElement | null>(null)
const avatarRef = ref<HTMLButtonElement | null>(null)

const isCompactSearch = computed<boolean>(() => width.value < 1024)

/** 深链归一到 5 项之一；兜底 /timeline，保证全顶栏恰好 1 个 aria-current。 */
const activePath = computed<string>(() => {
  const path = route.path
  if (path.startsWith('/albums')) return '/albums'
  if (path.startsWith('/growth') || path.startsWith('/baby')) return '/growth'
  if (path.startsWith('/family')) return '/family'
  if (path.startsWith('/settings')) return '/settings'
  return '/timeline'
})

const pendingCount = computed<number>(() =>
  Math.max(familyStore.pendingReviewCount, auth.currentPendingReviewCount),
)
const pendingText = computed<string>(() => (pendingCount.value > 9 ? '9+' : String(pendingCount.value)))
const displayName = computed<string>(() => auth.nickname || '我')

function isActive(to: string): boolean {
  return activePath.value === to
}

function setNavRef(el: unknown, index: number): void {
  navRefs.value[index] = (el as HTMLElement | null) ?? null
}

function onNavFocus(index: number): void {
  focusIndex.value = index
}

function activate(index: number): void {
  const item = NAV_ITEMS[index]
  if (item) void router.push(item.to)
}

function onNavKeydown(event: KeyboardEvent, index: number): void {
  if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
    event.preventDefault()
    const delta = event.key === 'ArrowRight' ? 1 : -1
    const next = Math.min(Math.max(index + delta, 0), NAV_ITEMS.length - 1)
    focusIndex.value = next
    navRefs.value[next]?.focus()
    return
  }
  if (event.key === ' ' || event.key === 'Spacebar') {
    event.preventDefault()
    activate(index)
  }
}

function submitSearch(): void {
  const query = switchStore.searchKeyword.trim()
  if (!query) return
  searchOverlayOpen.value = false
  void router.push({ name: 'search', query: { q: query } })
}

function openSearchOverlay(): void {
  searchOverlayOpen.value = true
  void nextTick(() => overlayInputRef.value?.focus())
}

function closeSearchOverlay(): void {
  searchOverlayOpen.value = false
  searchIconRef.value?.focus()
}

function goAccountSettings(): void {
  menuOpen.value = false
  void router.push({ name: 'settings-account' })
}

async function logout(): Promise<void> {
  menuOpen.value = false
  await auth.logout()
  toast.success('已退出登录')
  void router.replace({ name: 'login' })
}

function onDocumentPointerDown(event: PointerEvent): void {
  if (!menuOpen.value) return
  const root = menuRef.value
  if (root && !root.contains(event.target as Node)) menuOpen.value = false
}

function isTypingTarget(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null
  if (!el) return false
  const tag = el.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || el.isContentEditable === true
}

function onDocumentKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    if (searchOverlayOpen.value) closeSearchOverlay()
    if (menuOpen.value) {
      menuOpen.value = false
      avatarRef.value?.focus()
    }
    return
  }
  const shortcut = event.key === '/' || ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k')
  if (!shortcut || isTypingTarget(event.target)) return
  event.preventDefault()
  if (isCompactSearch.value) openSearchOverlay()
  else searchInputRef.value?.focus()
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onDocumentKeydown)
})
</script>

<template>
  <header class="bee-topnav">
    <!-- Logo 区：整区可点，回 /timeline -->
    <RouterLink to="/timeline" class="bee-topnav__logo" aria-label="蜜记首页">
      <span class="bee-topnav__mark" aria-hidden="true">
        <BeeIcon name="hex" :size="32" :stroke-width="1.4" />
      </span>
      <span class="bee-topnav__brand">
        <strong>蜜记</strong>
        <small>Bee Family</small>
      </span>
    </RouterLink>

    <span class="bee-topnav__divider" aria-hidden="true" />

    <FamilySwitcher @view-queue="emit('viewQueue')" />

    <!-- 一级导航：固定 5 项，roving tabindex 整区只占 1 个 Tab 停靠点 -->
    <nav class="bee-topnav__nav" role="navigation" aria-label="主导航">
      <RouterLink
        v-for="(item, index) in NAV_ITEMS"
        :key="item.to"
        :ref="(el) => setNavRef(el, index)"
        class="bee-topnav__nav-item"
        :class="{ 'is-active': isActive(item.to) }"
        :to="item.to"
        :tabindex="focusIndex === index ? 0 : -1"
        :aria-current="isActive(item.to) ? 'page' : undefined"
        @focus="onNavFocus(index)"
        @keydown="onNavKeydown($event, index)"
      >
        <BeeIcon :name="item.icon" :size="16" />
        <span class="bee-topnav__nav-label">{{ item.label }}</span>
        <span
          v-if="item.to === '/family' && auth.canReview && pendingCount > 0"
          class="bee-topnav__badge"
          aria-label="待审申请"
        >
          {{ pendingText }}
        </span>
      </RouterLink>
    </nav>

    <div class="bee-topnav__user">
      <form v-if="!isCompactSearch" class="bee-topnav__search" role="search" @submit.prevent="submitSearch">
        <BeeIcon class="bee-topnav__search-icon" name="search" :size="16" />
        <input
          ref="searchInputRef"
          v-model="switchStore.searchKeyword"
          class="bee-topnav__search-input"
          type="search"
          placeholder="搜索照片、相册、里程碑"
          aria-label="全局搜索"
        />
        <button
          v-if="switchStore.searchKeyword"
          class="bee-topnav__search-clear"
          type="button"
          aria-label="清空"
          @click="switchStore.clearSearch()"
        >
          <BeeIcon name="close" :size="14" />
        </button>
      </form>

      <button
        v-else
        ref="searchIconRef"
        class="bee-topnav__square"
        type="button"
        aria-label="搜索"
        :aria-expanded="searchOverlayOpen"
        @click="openSearchOverlay"
      >
        <BeeIcon name="search" :size="18" />
      </button>

      <BeeButton class="bee-topnav__upload" variant="primary" size="md" @click="emit('upload')">
        <template #icon><BeeIcon name="plus" :size="16" /></template>
        <span class="bee-topnav__upload-text">上传照片</span>
      </BeeButton>

      <div ref="menuRef" class="bee-topnav__avatar-wrap">
        <button
          ref="avatarRef"
          class="bee-topnav__avatar"
          type="button"
          :aria-expanded="menuOpen"
          aria-haspopup="menu"
          aria-label="用户菜单"
          @click="menuOpen = !menuOpen"
        >
          <BeeAvatar :src="auth.user?.avatarUrl ?? null" :name="displayName" :size="36" />
        </button>
        <div v-if="menuOpen" class="bee-topnav__menu" role="menu">
          <button class="bee-topnav__menu-item" type="button" role="menuitem" @click="goAccountSettings">
            <BeeIcon name="settings" :size="16" />账号设置
          </button>
          <button class="bee-topnav__menu-item" type="button" role="menuitem" @click="logout">
            <BeeIcon name="logout" :size="16" />退出登录
          </button>
        </div>
      </div>
    </div>

    <!-- 768–1023 搜索浮层：top 64px、全宽输入框、Esc 关闭 -->
    <Transition name="bee-search-overlay">
      <div v-if="searchOverlayOpen && isCompactSearch" class="bee-topnav__search-overlay">
        <form class="bee-topnav__search-overlay-form" role="search" @submit.prevent="submitSearch">
          <BeeIcon name="search" :size="18" />
          <input
            ref="overlayInputRef"
            v-model="switchStore.searchKeyword"
            class="bee-topnav__search-overlay-input"
            type="search"
            placeholder="搜索照片、相册、里程碑"
            aria-label="全局搜索"
            @keydown.esc.prevent="closeSearchOverlay"
          />
          <button class="bee-topnav__search-overlay-close" type="button" aria-label="关闭搜索" @click="closeSearchOverlay">
            <BeeIcon name="close" :size="16" />
          </button>
        </form>
      </div>
    </Transition>
  </header>
</template>

<style scoped>
.bee-topnav {
  position: sticky;
  top: 0;
  z-index: var(--z-topbar);
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  width: 100%;
  height: var(--topbar-h);
  padding: 0 var(--container-pad);
  background: var(--bg-topbar);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-subtle);
}

/* ---------- Logo ---------- */
.bee-topnav__logo {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 120px;
  height: var(--topbar-h);
  flex: none;
}

.bee-topnav__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 37px;
  flex: none;
  color: var(--honey-500);
}

.bee-topnav__brand {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
  overflow: hidden;
  white-space: nowrap;
}

.bee-topnav__brand strong {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 600;
  color: var(--text-brand);
}

.bee-topnav__brand small {
  font-size: 11px;
  font-weight: 400;
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
}

.bee-topnav__divider {
  width: 1px;
  height: 24px;
  flex: none;
  background: var(--border-default);
  margin: 0 var(--sp-4);
}

/* ---------- 一级导航 ---------- */
.bee-topnav__nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: none;
  margin-left: var(--sp-6);
}

.bee-topnav__nav-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 40px;
  min-width: 84px;
  padding: 0 16px;
  border-radius: var(--r-md);
  color: var(--text-secondary);
  font-size: var(--fs-body);
  font-weight: 500;
  white-space: nowrap;
  transition:
    background-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.bee-topnav__nav-item:hover {
  background: var(--honey-50);
  color: var(--text-primary);
}

.bee-topnav__nav-item:active {
  background: var(--honey-100);
  transform: scale(0.98);
}

.bee-topnav__nav-item:focus-visible {
  /* docs/06 §3.4 / 02 §4.2：box-shadow 聚焦环 + 2px 偏移；禁止 outline: none */
  outline: 2px solid var(--honey-400);
  outline-offset: 2px;
  box-shadow: 0 0 0 2px var(--honey-400);
}

.bee-topnav__nav-item.is-active {
  color: var(--text-brand);
  font-weight: 600;
}

/* 当前态：底部 2px 蜜金下划线，宽 = 项宽 − 32px */
.bee-topnav__nav-item.is-active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 16px;
  right: 16px;
  height: 2px;
  background: var(--honey-500);
}

.bee-topnav__badge {
  position: absolute;
  top: -4px;
  right: 2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: var(--r-full);
  background: var(--accent-coral);
  color: var(--text-inverse);
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
}

/* ---------- 用户区 ---------- */
.bee-topnav__user {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
  flex: none;
}

.bee-topnav__search {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 320px;
  height: 40px;
  padding: 0 var(--sp-3);
  background: var(--bg-sunken);
  border: 1px solid var(--border-default);
  border-radius: var(--r-md);
}

.bee-topnav__search:focus-within {
  background: var(--bg-elevated);
  border-color: var(--border-focus);
  box-shadow: var(--shadow-focus);
}

.bee-topnav__search-icon {
  color: var(--text-tertiary);
  flex: none;
}

.bee-topnav__search-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  background: transparent;
  font-family: var(--font-sans);
  font-size: var(--fs-body);
  color: var(--text-primary);
  outline: none;
}

.bee-topnav__search-input::placeholder {
  color: var(--text-tertiary);
}

.bee-topnav__search-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--r-full);
  color: var(--text-tertiary);
}

.bee-topnav__square {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--r-md);
  color: var(--text-secondary);
}

.bee-topnav__square:hover {
  background: var(--honey-50);
  color: var(--text-primary);
}

.bee-topnav__upload {
  width: 112px;
}

.bee-topnav__avatar-wrap {
  position: relative;
}

.bee-topnav__avatar {
  display: inline-flex;
  border-radius: var(--r-full);
}

.bee-topnav__avatar:hover {
  box-shadow: 0 0 0 2px var(--honey-200);
}

.bee-topnav__menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  z-index: var(--z-topbar-dropdown);
  width: 180px;
  padding: var(--sp-1);
  background: var(--bg-elevated);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-md);
}

.bee-topnav__menu-item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;
  height: 36px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-sm);
  font-size: var(--fs-body);
  color: var(--text-primary);
  text-align: left;
}

.bee-topnav__menu-item:hover {
  background: var(--honey-50);
}

/* ---------- 搜索浮层（768–1023） ---------- */
.bee-topnav__search-overlay {
  position: absolute;
  top: var(--topbar-h);
  left: var(--container-pad);
  right: var(--container-pad);
  z-index: var(--z-search-overlay);
  height: 56px;
  background: var(--bg-elevated);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-lg);
}

.bee-topnav__search-overlay-form {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  height: 100%;
  padding: 0 var(--sp-4);
  color: var(--text-tertiary);
}

.bee-topnav__search-overlay-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  background: transparent;
  font-family: var(--font-sans);
  font-size: var(--fs-body);
  color: var(--text-primary);
  outline: none;
}

.bee-topnav__search-overlay-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--r-sm);
  color: var(--text-tertiary);
}

.bee-search-overlay-enter-active,
.bee-search-overlay-leave-active {
  transition: opacity var(--dur-base) var(--ease-out);
}

.bee-search-overlay-enter-from,
.bee-search-overlay-leave-to {
  opacity: 0;
}

/* ---------- 1024–1279 收缩（A-1：搜索 200 + 上传 40 图标） ---------- */
@media (max-width: 1279px) and (min-width: 1024px) {
  .bee-topnav {
    gap: var(--sp-3);
  }

  .bee-topnav__logo {
    width: 32px;
  }

  /* 收缩优先级：先隐藏 Logo 文字；5 项导航文字在全部 ≥768 档位都保留 */
  .bee-topnav__brand {
    display: none;
  }

  .bee-topnav__nav {
    margin-left: var(--sp-4);
  }

  .bee-topnav__nav-item {
    min-width: 76px;
    padding: 0 12px;
  }

  .bee-topnav__nav-item.is-active::after {
    left: 12px;
    right: 12px;
  }

  .bee-topnav__search {
    width: 200px;
  }

  .bee-topnav__upload {
    width: 40px;
    padding: 0;
  }

  .bee-topnav__upload-text {
    display: none;
  }
}

/* ---------- 768–1023 收缩 ---------- */
@media (max-width: 1023px) and (min-width: 768px) {
  .bee-topnav {
    gap: var(--sp-3);
  }

  .bee-topnav__logo {
    width: 24px;
  }

  .bee-topnav__mark :deep(svg) {
    width: 24px;
    height: 28px;
  }

  .bee-topnav__brand,
  .bee-topnav__divider {
    display: none;
  }

  .bee-topnav__nav {
    margin-left: var(--sp-2);
  }

  .bee-topnav__nav-item {
    min-width: 76px;
    padding: 0 12px;
  }

  .bee-topnav__nav-item.is-active::after {
    left: 12px;
    right: 12px;
  }

  .bee-topnav__upload {
    width: 36px;
    padding: 0;
  }

  .bee-topnav__upload-text {
    display: none;
  }

  .bee-topnav__avatar :deep(.bee-avatar) {
    width: 32px;
    height: 32px;
  }
}

/* <768：由 AppShell 的 v-if 卸载本组件，此处仅作 CSS 兜底，保证 XOR */
@media (max-width: 767px) {
  .bee-topnav {
    display: none;
  }
}
</style>
