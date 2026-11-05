<script setup lang="ts">
/**
 * 侧边导航（≥768px 显示）。
 *
 * <p>三档形态：≥1024 完整 240px；768–1023 收窄为 72px 仅图标（CSS 通过
 * `--sidebar-w` 切换，文字用 `opacity/width` 隐藏）；<768 由 AppShell 换成抽屉。</p>
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import BeeIcon from '@/components/base/BeeIcon.vue'
import type { IconName } from '@/components/base/icon-paths'

interface NavLink {
  to: string
  label: string
  icon: IconName
}

const links: NavLink[] = [
  { to: '/timeline', label: '时间轴', icon: 'grid' },
  { to: '/albums', label: '相册', icon: 'camera' },
  { to: '/growth', label: '成长记录', icon: 'baby' },
  { to: '/family', label: '家庭成员', icon: 'user' },
]

const route = useRoute()

const activePath = computed<string>(() => {
  const matched = route.path
  if (matched.startsWith('/albums')) return '/albums'
  if (matched.startsWith('/baby')) return '/family'
  if (matched.startsWith('/growth')) return '/growth'
  if (matched.startsWith('/family')) return '/family'
  if (matched.startsWith('/timeline')) return '/timeline'
  return matched
})

defineProps<{ collapsed?: boolean }>()
</script>

<template>
  <aside class="bee-sidebar" :class="{ 'is-collapsed': collapsed }">
    <RouterLink to="/timeline" class="bee-sidebar__logo" aria-label="蜜记首页">
      <span class="bee-sidebar__mark" aria-hidden="true">
        <svg viewBox="0 0 32 32" width="22" height="22">
          <polygon
            points="16,3 27.3,9.5 27.3,22.5 16,29 4.7,22.5 4.7,9.5"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linejoin="round"
          />
          <polygon points="16,11 21,14 21,20 16,23 11,20 11,14" fill="currentColor" opacity=".28" />
        </svg>
      </span>
      <span class="bee-sidebar__brand">
        <strong>蜜记</strong>
        <small>Bee Family</small>
      </span>
    </RouterLink>

    <nav class="bee-sidebar__nav" aria-label="主导航">
      <p class="bee-sidebar__group-title">浏览</p>
      <RouterLink
        v-for="link in links"
        :key="link.to"
        class="bee-nav-item"
        :class="{ 'is-active': activePath === link.to }"
        :to="link.to"
        :title="link.label"
      >
        <span class="bee-nav-item__bar" aria-hidden="true" />
        <BeeIcon :name="link.icon" :size="18" />
        <span class="bee-nav-item__label">{{ link.label }}</span>
      </RouterLink>
    </nav>

    <div class="bee-sidebar__footer">
      <RouterLink
        class="bee-nav-item"
        :class="{ 'is-active': activePath === '/settings' }"
        to="/settings"
        title="设置"
      >
        <span class="bee-nav-item__bar" aria-hidden="true" />
        <BeeIcon name="settings" :size="18" />
        <span class="bee-nav-item__label">设置</span>
      </RouterLink>
    </div>
  </aside>
</template>

<style scoped>
.bee-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  flex: none;
  width: var(--sidebar-w);
  height: 100vh;
  padding: 0 var(--sp-3) var(--sp-4);
  background: var(--bg-surface);
  border-right: 1px solid var(--border-subtle);
  transition: width var(--dur-base) var(--ease-out);
}

.bee-sidebar__logo {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  height: var(--topbar-h);
  padding-left: var(--sp-2);
  flex: none;
}

.bee-sidebar__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: none;
  color: var(--honey-500);
}

.bee-sidebar__brand {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
  overflow: hidden;
  white-space: nowrap;
}

.bee-sidebar__brand strong {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: 0.02em;
}

.bee-sidebar__brand small {
  font-size: 11px;
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
}

.bee-sidebar__nav {
  flex: 1;
  overflow-y: auto;
  padding-top: var(--sp-4);
}

.bee-sidebar__group-title {
  padding: 0 var(--sp-3) var(--sp-2);
  font-size: var(--fs-caption);
  font-weight: 500;
  letter-spacing: 0.06em;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.bee-sidebar__footer {
  flex: none;
  padding-top: var(--sp-3);
  border-top: 1px solid var(--border-subtle);
}

.bee-nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  height: 40px;
  padding-left: var(--sp-3);
  padding-right: var(--sp-2);
  margin-bottom: 2px;
  border-radius: var(--r-md);
  color: var(--text-secondary);
  font-size: var(--fs-body);
  font-weight: 500;
  white-space: nowrap;
  transition:
    background-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.bee-nav-item:hover {
  background: var(--honey-50);
  color: var(--text-primary);
}

.bee-nav-item:focus-visible {
  outline: 2px solid var(--honey-400);
  outline-offset: 2px;
}

.bee-nav-item.is-active {
  background: var(--honey-100);
  color: var(--text-brand);
  font-weight: 600;
}

.bee-nav-item.is-active :deep(svg) {
  color: var(--honey-600);
}

.bee-nav-item__bar {
  position: absolute;
  left: 0;
  width: 3px;
  height: 16px;
  border-radius: 2px;
  background: transparent;
}

.bee-nav-item.is-active .bee-nav-item__bar {
  background: var(--honey-500);
}

.bee-nav-item__label {
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 768–1023：仅图标 */
@media (max-width: 1023px) and (min-width: 768px) {
  .bee-sidebar {
    padding: 0 var(--sp-2) var(--sp-4);
    align-items: center;
  }

  .bee-sidebar__logo {
    padding-left: 0;
    justify-content: center;
  }

  .bee-sidebar__brand,
  .bee-sidebar__group-title,
  .bee-nav-item__label {
    display: none;
  }

  .bee-nav-item {
    width: 40px;
    padding: 0;
    justify-content: center;
  }

  .bee-nav-item__bar {
    left: -8px;
  }

  .bee-sidebar__footer {
    width: 100%;
  }
}

@media (max-width: 767px) {
  .bee-sidebar {
    display: none;
  }
}
</style>
