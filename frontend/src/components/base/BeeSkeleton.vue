<script setup lang="ts">
/**
 * 骨架屏。四种形态：`block` 基础块、`card` 相册卡、`grid` 照片网格、`timeline` 时间轴。
 *
 * 仅用于首屏与分页加载；`<300ms` 的局部更新不叠加骨架，避免闪烁。
 */
interface Props {
  variant: 'block' | 'card' | 'grid' | 'timeline'
  count?: number
  columns?: number
  height?: string
  width?: string
  radius?: string
}

withDefaults(defineProps<Props>(), {
  count: 1,
  columns: 5,
  height: '16px',
  width: '100%',
  radius: 'var(--r-sm)',
})

function range(n: number): number[] {
  return Array.from({ length: n }, (_, i) => i)
}
</script>

<template>
  <div v-if="variant === 'block'" class="bee-skeleton-block-wrap">
    <span
      v-for="i in range(count)"
      :key="i"
      class="bee-skeleton"
      :style="{ height, width, borderRadius: radius }"
    />
  </div>

  <div v-else-if="variant === 'card'" class="bee-skeleton-cards">
    <div v-for="i in range(count)" :key="i" class="bee-skeleton-card">
      <span class="bee-skeleton bee-skeleton-card__cover" />
      <span class="bee-skeleton bee-skeleton-card__line" style="width: 70%" />
      <span class="bee-skeleton bee-skeleton-card__line" style="width: 40%" />
    </div>
  </div>

  <div
    v-else-if="variant === 'grid'"
    class="bee-skeleton-grid"
    :style="{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }"
  >
    <span v-for="i in range(count)" :key="i" class="bee-skeleton bee-skeleton-grid__cell" />
  </div>

  <div v-else class="bee-skeleton-timeline">
    <div v-for="i in range(count)" :key="i" class="bee-skeleton-timeline__row">
      <span class="bee-skeleton bee-skeleton-timeline__dot" />
      <span class="bee-skeleton bee-skeleton-timeline__block" />
    </div>
  </div>
</template>

<style scoped>
.bee-skeleton {
  display: block;
  background: var(--warm-100);
  border-radius: var(--r-sm);
  animation: bee-skeleton-pulse 1.4s var(--ease-in-out) infinite;
}

@keyframes bee-skeleton-pulse {
  0%,
  100% {
    opacity: 0.5;
  }
  50% {
    opacity: 1;
  }
}

.bee-skeleton-block-wrap {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.bee-skeleton-cards {
  display: grid;
  gap: var(--grid-gutter);
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
}

.bee-skeleton-card {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  padding-bottom: var(--sp-4);
  background: var(--bg-surface);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.bee-skeleton-card__cover {
  aspect-ratio: 4 / 3;
  border-radius: 0;
}

.bee-skeleton-card__line {
  height: 12px;
  margin: 0 var(--sp-4);
}

.bee-skeleton-grid {
  display: grid;
  gap: var(--sp-2);
}

.bee-skeleton-grid__cell {
  aspect-ratio: 1 / 1;
  border-radius: var(--r-sm);
}

.bee-skeleton-timeline {
  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
}

.bee-skeleton-timeline__row {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-4);
}

.bee-skeleton-timeline__dot {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: var(--r-full);
}

.bee-skeleton-timeline__block {
  flex: 1;
  aspect-ratio: 16 / 10;
  border-radius: var(--r-lg);
}
</style>
