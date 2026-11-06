<script setup lang="ts">
/**
 * 移动端底部 TabBar（<768，docs/06 §4.2 / §4.3）。
 *
 * <p>5 个一级入口、图标 20px、文字 10px、高 56px + safe-area，规格完全不变。
 * 激活项 `--honey-600` 图标 + `--text-brand` 文字 + 顶部 2px 蜜金短线。
 * 待审角标为 8×8 圆点（无数字），仅 owner + admin 可见（D-2）。</p>
 *
 * <p>与 ≥768 的 `TopNav` 由 `AppShell` 的 `v-if` 互斥挂载；CSS 里
 * `@media (min-width: 768px)` 兜底隐藏，保证同一时刻只存在一套主导航。</p>
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import BeeIcon from '@/components/base/BeeIcon.vue'
import type { IconName } from '@/components/base/icon-paths'
import { useAuthStore } from '@/stores/auth'
import { useFamilyStore } from '@/stores/family'

interface TabLink {
  to: string
  label: string
  icon: IconName
}

/** 顺序与 03 §9.2 路由表、顶部导航 5 项完全一致。 */
const tabs: TabLink[] = [
  { to: '/timeline', label: '时间轴', icon: 'grid' },
  { to: '/albums', label: '相册', icon: 'camera' },
  { to: '/growth', label: '成长', icon: 'baby' },
  { to: '/family', label: '家人', icon: 'user' },
  { to: '/settings', label: '设置', icon: 'settings' },
]

const route = useRoute()
const auth = useAuthStore()
const familyStore = useFamilyStore()

const activePath = computed<string>(() => {
  const path = route.path
  if (path.startsWith('/albums')) return '/albums'
  if (path.startsWith('/growth') || path.startsWith('/baby')) return '/growth'
  if (path.startsWith('/family')) return '/family'
  if (path.startsWith('/settings')) return '/settings'
  return '/timeline'
})

/** 8×8 圆点：owner + admin 才渲染（member 隐藏），pending 为 0 不渲染。 */
const showPendingDot = computed<boolean>(() => {
  if (!auth.canReview) return false
  return Math.max(familyStore.pendingReviewCount, auth.currentPendingReviewCount) > 0
})
</script>

<template>
  <nav class="bee-tabbar" aria-label="底部导航">
    <RouterLink
      v-for="tab in tabs"
      :key="tab.to"
      class="bee-tabbar__item"
      :class="{ 'is-active': activePath === tab.to }"
      :to="tab.to"
      :aria-current="activePath === tab.to ? 'page' : undefined"
    >
      <span class="bee-tabbar__icon-wrap">
        <BeeIcon :name="tab.icon" :size="20" />
        <span v-if="tab.to === '/family' && showPendingDot" class="bee-tabbar__dot" aria-label="待审申请" />
      </span>
      <span class="bee-tabbar__label">{{ tab.label }}</span>
    </RouterLink>
  </nav>
</template>

<style scoped>
.bee-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 45;
  display: none;
  height: var(--tabbar-h);
  padding-bottom: env(safe-area-inset-bottom, 0);
  background: var(--bg-topbar);
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--border-subtle);
}

@media (max-width: 767px) {
  .bee-tabbar {
    display: flex;
  }
}

/* 互斥保险：≥768 由 AppShell 卸载本组件 */
@media (min-width: 768px) {
  .bee-tabbar {
    display: none;
  }
}

.bee-tabbar__item {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--text-tertiary);
  transition: color var(--dur-fast) var(--ease-out);
}

.bee-tabbar__item.is-active {
  color: var(--text-brand);
}

.bee-tabbar__item.is-active::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  width: 20px;
  height: 2px;
  transform: translateX(-50%);
  background: var(--honey-500);
}

.bee-tabbar__item.is-active :deep(svg) {
  color: var(--honey-600);
}

.bee-tabbar__icon-wrap {
  position: relative;
  display: inline-flex;
}

.bee-tabbar__dot {
  position: absolute;
  top: -2px;
  right: -5px;
  width: 8px;
  height: 8px;
  border-radius: var(--r-full);
  background: var(--accent-coral);
}

.bee-tabbar__label {
  font-size: 10px;
  line-height: 1;
  font-weight: 500;
}
</style>
