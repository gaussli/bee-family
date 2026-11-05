<script setup lang="ts">
/**
 * 线性图标组件。所有图标统一 24×24 坐标系、`stroke` 绘制、圆角端点，
 * 通过 `size` 等比缩放，`strokeWidth` 随之反向补偿以保持视觉粗细一致。
 */
import { computed } from 'vue'
import { ICON_PATHS, type IconName } from './icon-paths'

interface Props {
  name: IconName
  size?: number
  strokeWidth?: number
}

const props = withDefaults(defineProps<Props>(), {
  size: 18,
  strokeWidth: 1.5,
})

const paths = computed<readonly string[]>(() => ICON_PATHS[props.name] ?? [])

/** 小尺寸图标适当加粗描边，避免视觉过淡。 */
const resolvedStroke = computed<number>(() => {
  if (props.size >= 32) return props.strokeWidth * 0.9
  if (props.size <= 14) return props.strokeWidth * 1.15
  return props.strokeWidth
})
</script>

<template>
  <svg
    class="bee-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    :stroke-width="resolvedStroke"
    stroke="currentColor"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>

<style scoped>
.bee-icon {
  display: inline-block;
  flex: none;
  vertical-align: middle;
}
</style>
