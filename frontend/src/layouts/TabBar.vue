<script setup lang="ts">
/**
 * 移动端底部 TabBar（<768px）。5 个一级入口，高 56px，
 * 选中项用蜜金色图标 + `--text-brand` 文字。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import BeeIcon from '@/components/base/BeeIcon.vue'
import type { IconName } from '@/components/base/icon-paths'

interface TabLink {
  to: string
  label: string
  icon: IconName
}

const tabs: TabLink[] = [
  { to: '/timeline', label: '时间轴', icon: 'grid' },
  { to: '/albums', label: '相册', icon: 'camera' },
  { to: '/growth', label: '成长', icon: 'baby' },
  { to: '/family', label: '家人', icon: 'user' },
  { to: '/settings', label: '设置', icon: 'settings' },
]

const route = useRoute()

const activePath = computed<string>(() => {
  const path = route.path
  if (path.startsWith('/albums')) return '/albums'
  if (path.startsWith('/growth') || path.startsWith('/baby')) return '/growth'
  if (path.startsWith('/family')) return '/family'
  if (path.startsWith('/settings')) return '/settings'
  return '/timeline'
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
    >
      <BeeIcon :name="tab.icon" :size="20" />
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
  background: color-mix(in srgb, var(--bg-surface) 94%, transparent);
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--border-subtle);
}

.bee-tabbar__item {
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

.bee-tabbar__item.is-active :deep(svg) {
  color: var(--honey-600);
}

.bee-tabbar__label {
  font-size: 11px;
  line-height: 1;
  font-weight: 500;
}

@media (max-width: 767px) {
  .bee-tabbar {
    display: flex;
  }
}
</style>
