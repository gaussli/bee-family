<script setup lang="ts">
/**
 * 顶栏。左：移动端汉堡 + 当前页标题；中：全局搜索；右：上传按钮 + 用户菜单。
 *
 * <p>搜索框只做「跳转到 /search?q=」的入口，真正的搜索、防抖与高亮在 `SearchView` 内完成，
 * 避免顶栏在任意页面都挂一个搜索请求。</p>
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BeeAvatar from '@/components/base/BeeAvatar.vue'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

const emit = defineEmits<{ upload: []; openNav: [] }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

const keyword = ref<string>('')
const menuOpen = ref<boolean>(false)
const menuRef = ref<HTMLElement | null>(null)

const pageTitle = computed<string>(() => route.meta.title ?? '')
const displayName = computed<string>(() => auth.nickname || '我')

function submitSearch(): void {
  const q = keyword.value.trim()
  if (!q) return
  void router.push({ name: 'search', query: { q } })
}

function openMenu(): void {
  menuOpen.value = !menuOpen.value
}

function goSettings(): void {
  menuOpen.value = false
  void router.push({ name: 'settings' })
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

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') menuOpen.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <header class="bee-topbar">
    <button class="bee-topbar__hamburger" type="button" aria-label="打开导航" @click="emit('openNav')">
      <BeeIcon name="menu" :size="20" />
    </button>

    <span class="bee-topbar__title">{{ pageTitle }}</span>

    <form class="bee-topbar__search" role="search" @submit.prevent="submitSearch">
      <BeeIcon class="bee-topbar__search-icon" name="search" :size="16" />
      <input
        v-model="keyword"
        class="bee-topbar__search-input"
        type="search"
        placeholder="搜索照片、相册、里程碑"
        aria-label="全局搜索"
      />
      <button v-if="keyword" class="bee-topbar__search-clear" type="button" aria-label="清空" @click="keyword = ''">
        <BeeIcon name="close" :size="14" />
      </button>
    </form>

    <div class="bee-topbar__actions">
      <BeeButton class="bee-topbar__upload" variant="primary" size="md" @click="emit('upload')">
        <template #icon><BeeIcon name="plus" :size="16" /></template>
        <span class="bee-topbar__upload-text">上传照片</span>
      </BeeButton>

      <div ref="menuRef" class="bee-topbar__user">
        <button
          class="bee-topbar__avatar"
          type="button"
          :aria-expanded="menuOpen"
          aria-haspopup="menu"
          aria-label="用户菜单"
          @click="openMenu"
        >
          <BeeAvatar :src="auth.user?.avatarUrl ?? null" :name="displayName" :size="32" />
        </button>

        <div v-if="menuOpen" class="bee-topbar__menu" role="menu">
          <div class="bee-topbar__menu-head">
            <strong>{{ displayName }}</strong>
            <small>{{ auth.user?.email }}</small>
          </div>
          <button class="bee-topbar__menu-item" type="button" role="menuitem" @click="goSettings">
            <BeeIcon name="settings" :size="16" />
            设置
          </button>
          <button class="bee-topbar__menu-item" type="button" role="menuitem" @click="logout">
            <BeeIcon name="logout" :size="16" />
            退出登录
          </button>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.bee-topbar {
  position: sticky;
  top: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  height: var(--topbar-h);
  padding: 0 var(--container-pad);
  background: color-mix(in srgb, var(--bg-page) 86%, transparent);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-subtle);
}

.bee-topbar__hamburger {
  display: none;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin-left: calc(var(--sp-3) * -1);
  border-radius: var(--r-md);
  color: var(--text-secondary);
}

.bee-topbar__hamburger:hover {
  background: var(--warm-100);
  color: var(--text-primary);
}

.bee-topbar__title {
  display: none;
  font-size: var(--fs-body-lg);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-topbar__search {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 320px;
  max-width: 400px;
  height: 36px;
  padding: 0 var(--sp-3);
  background: var(--bg-sunken);
  border: 1px solid transparent;
  border-radius: var(--r-full);
  transition:
    background-color var(--dur-fast) var(--ease-out),
    border-color var(--dur-fast) var(--ease-out);
}

.bee-topbar__search:focus-within {
  background: var(--bg-elevated);
  border-color: var(--border-focus);
  box-shadow: var(--shadow-focus);
}

.bee-topbar__search-icon {
  color: var(--text-tertiary);
  flex: none;
}

.bee-topbar__search-input {
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

.bee-topbar__search-input::placeholder {
  color: var(--text-tertiary);
}

.bee-topbar__search-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--r-full);
  color: var(--text-tertiary);
}

.bee-topbar__search-clear:hover {
  background: var(--warm-200);
  color: var(--text-primary);
}

.bee-topbar__actions {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-left: auto;
}

.bee-topbar__user {
  position: relative;
}

.bee-topbar__avatar {
  display: inline-flex;
  border-radius: var(--r-full);
  transition: box-shadow var(--dur-fast) var(--ease-out);
}

.bee-topbar__avatar:hover {
  box-shadow: 0 0 0 2px var(--honey-200);
}

.bee-topbar__menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 200px;
  padding: var(--sp-1);
  background: var(--bg-elevated);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-md);
}

.bee-topbar__menu-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--sp-2) var(--sp-3);
  border-bottom: 1px solid var(--border-subtle);
  margin-bottom: var(--sp-1);
}

.bee-topbar__menu-head strong {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-topbar__menu-head small {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
}

.bee-topbar__menu-item {
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

.bee-topbar__menu-item:hover {
  background: var(--honey-50);
}

@media (max-width: 1023px) {
  .bee-topbar {
    gap: var(--sp-3);
  }

  .bee-topbar__search {
    width: 220px;
  }
}

@media (max-width: 767px) {
  .bee-topbar {
    padding: 0 var(--sp-4);
    gap: var(--sp-2);
  }

  .bee-topbar__hamburger {
    display: inline-flex;
  }

  .bee-topbar__title {
    display: block;
  }

  .bee-topbar__search {
    display: none;
  }

  .bee-topbar__upload {
    width: 40px;
    padding: 0;
  }

  .bee-topbar__upload-text {
    display: none;
  }
}
</style>
