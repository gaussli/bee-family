<script setup lang="ts">
/**
 * 移动端精简顶栏（<768，docs/06 §4.2 / §4.3）。
 *
 * <p>仅保留：24px 六边形 Logo、≤140px 家庭切换器（名 4 字 + chevron）、
 * 32px 搜索图标、32px 头像。**不含**任何导航项、不含上传按钮、不含竖向分隔线、
 * 不含汉堡抽屉入口；5 个一级入口全部由底部 `TabBar` 承载。</p>
 *
 * <p>与本组件互斥的 ≥768 `TopNav` 由 `AppShell` 的 `v-if` 卸载；此处
 * `@media (min-width: 768px) { display: none }` 是第二道保险，保证 XOR。</p>
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BeeAvatar from '@/components/base/BeeAvatar.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import FamilySwitcher from '@/components/business/FamilySwitcher.vue'
import { useAuthStore } from '@/stores/auth'
import { useSwitchStore } from '@/stores/switch'
import { useToast } from '@/composables/useToast'

const emit = defineEmits<{ viewQueue: [] }>()

const router = useRouter()
const auth = useAuthStore()
const switchStore = useSwitchStore()
const toast = useToast()

const searchOpen = ref<boolean>(false)
const searchInputRef = ref<HTMLInputElement | null>(null)
const searchIconRef = ref<HTMLButtonElement | null>(null)
const menuOpen = ref<boolean>(false)
const menuRef = ref<HTMLElement | null>(null)
const avatarRef = ref<HTMLButtonElement | null>(null)

const displayName = computed<string>(() => auth.nickname || '我')

function openSearch(): void {
  searchOpen.value = true
  void nextTick(() => searchInputRef.value?.focus())
}

function closeSearch(): void {
  searchOpen.value = false
  searchIconRef.value?.focus()
}

function submitSearch(): void {
  const query = switchStore.searchKeyword.trim()
  if (!query) return
  searchOpen.value = false
  void router.push({ name: 'search', query: { q: query } })
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

function onDocumentKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape') return
  if (searchOpen.value) closeSearch()
  if (menuOpen.value) {
    menuOpen.value = false
    avatarRef.value?.focus()
  }
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
  <header class="bee-mobilebar">
    <RouterLink to="/timeline" class="bee-mobilebar__logo" aria-label="蜜记首页">
      <BeeIcon name="hex" :size="24" :stroke-width="1.4" />
    </RouterLink>

    <FamilySwitcher compact @view-queue="emit('viewQueue')" />

    <button
      ref="searchIconRef"
      class="bee-mobilebar__square"
      type="button"
      aria-label="搜索"
      :aria-expanded="searchOpen"
      @click="openSearch"
    >
      <BeeIcon name="search" :size="18" />
    </button>

    <div ref="menuRef" class="bee-mobilebar__avatar-wrap">
      <button
        ref="avatarRef"
        class="bee-mobilebar__avatar"
        type="button"
        :aria-expanded="menuOpen"
        aria-haspopup="menu"
        aria-label="用户菜单"
        @click="menuOpen = !menuOpen"
      >
        <BeeAvatar :src="auth.user?.avatarUrl ?? null" :name="displayName" :size="32" />
      </button>
      <div v-if="menuOpen" class="bee-mobilebar__menu" role="menu">
        <button class="bee-mobilebar__menu-item" type="button" role="menuitem" @click="goAccountSettings">
          <BeeIcon name="settings" :size="16" />账号设置
        </button>
        <button class="bee-mobilebar__menu-item" type="button" role="menuitem" @click="logout">
          <BeeIcon name="logout" :size="16" />退出登录
        </button>
      </div>
    </div>

    <!-- <768 全宽搜索浮层：top 64px、Esc 关闭 -->
    <Transition name="bee-mobile-search">
      <div v-if="searchOpen" class="bee-mobilebar__search-overlay">
        <form class="bee-mobilebar__search-form" role="search" @submit.prevent="submitSearch">
          <BeeIcon name="search" :size="18" />
          <input
            ref="searchInputRef"
            v-model="switchStore.searchKeyword"
            class="bee-mobilebar__search-input"
            type="search"
            placeholder="搜索照片、相册、里程碑"
            aria-label="全局搜索"
            @keydown.esc.prevent="closeSearch"
          />
          <button class="bee-mobilebar__search-close" type="button" aria-label="关闭搜索" @click="closeSearch">
            <BeeIcon name="close" :size="16" />
          </button>
        </form>
      </div>
    </Transition>
  </header>
</template>

<style scoped>
.bee-mobilebar {
  position: sticky;
  top: 0;
  z-index: var(--z-topbar);
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  width: 100%;
  height: var(--topbar-h);
  flex: none;
  padding: 0 var(--container-pad);
  background: var(--bg-topbar);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-subtle);
}

/* 互斥保险：≥768 由 AppShell 卸载本组件，CSS 再兜一层 */
@media (min-width: 768px) {
  .bee-mobilebar {
    display: none;
  }
}

.bee-mobilebar__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 28px;
  flex: none;
  color: var(--honey-500);
}

.bee-mobilebar__square {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: none;
  margin-left: auto;
  border-radius: var(--r-md);
  color: var(--text-secondary);
}

.bee-mobilebar__square:hover {
  background: var(--honey-50);
  color: var(--text-primary);
}

.bee-mobilebar__avatar-wrap {
  position: relative;
  flex: none;
}

.bee-mobilebar__avatar {
  display: inline-flex;
  border-radius: var(--r-full);
}

.bee-mobilebar__menu {
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

.bee-mobilebar__menu-item {
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

.bee-mobilebar__menu-item:hover {
  background: var(--honey-50);
}

.bee-mobilebar__search-overlay {
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

.bee-mobilebar__search-form {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  height: 100%;
  padding: 0 var(--sp-4);
  color: var(--text-tertiary);
}

.bee-mobilebar__search-input {
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

.bee-mobilebar__search-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: var(--r-sm);
  color: var(--text-tertiary);
}

.bee-mobile-search-enter-active,
.bee-mobile-search-leave-active {
  transition: opacity var(--dur-base) var(--ease-out);
}

.bee-mobile-search-enter-from,
.bee-mobile-search-leave-to {
  opacity: 0;
}
</style>
