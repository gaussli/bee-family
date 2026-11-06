<script setup lang="ts">
/**
 * 应用根组件。
 *
 * <p>根据 `route.meta.layout` 决定是否套用 AppShell（顶栏 / TabBar + 内容区）。
 * Toast 宿主挂在这里，保证任何页面（含登录页 / 无家庭引导页）都能弹提示。</p>
 *
 * <p>切换家庭后 `familyEpoch` 递增，`RouterView :key` 随之变化，强制重挂载
 * 所有视图与图片组件（docs/07 §10.3 第 9 / 12 项）。</p>
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'
import BeeToastHost from '@/components/base/BeeToastHost.vue'
import { useSwitchStore } from '@/stores/switch'

const route = useRoute()
const switchStore = useSwitchStore()

const isAppLayout = computed<boolean>(() => route.meta.layout === 'app')
const remountKey = computed<number>(() => switchStore.familyEpoch)
</script>

<template>
  <AppShell v-if="isAppLayout">
    <RouterView :key="remountKey" />
  </AppShell>
  <RouterView v-else :key="remountKey" />
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
