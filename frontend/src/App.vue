<script setup lang="ts">
/**
 * 应用根组件。
 *
 * <p>根据 `route.meta.layout` 决定是否套用 AppShell（侧栏 + 顶栏 + 移动端 TabBar）。
 * Toast 宿主挂在这里，保证任何页面（含登录页）都能弹提示。</p>
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'
import BeeToastHost from '@/components/base/BeeToastHost.vue'

const route = useRoute()

const isAppLayout = computed<boolean>(() => route.meta.layout === 'app')
</script>

<template>
  <AppShell v-if="isAppLayout">
    <RouterView />
  </AppShell>
  <RouterView v-else />
  <BeeToastHost />
</template>

<style>
/* 页面切换过渡：仅透明度，避免布局抖动 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity var(--dur-base) var(--ease-out);
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}
</style>
