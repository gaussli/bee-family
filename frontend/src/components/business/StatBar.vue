<script setup lang="ts">
/**
 * 概览统计条（02 文档 §5.2）。
 *
 * <p>四等分、无卡片背景，列间 1px 竖线；数字用 `--font-display` 32/700，
 * 移动端降为 2×2。数据来自 `GET /api/stats/overview`。</p>
 */
import { computed } from 'vue'
import { formatNumber } from '@/utils/format'
import type { Overview } from '@/types/models'

interface Props {
  stats: Overview
  compact?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  compact: false,
})

const cells = computed<{ key: string; value: number; label: string }[]>(() => [
  { key: 'photo', value: props.stats.photoCount, label: '照片' },
  { key: 'album', value: props.stats.albumCount, label: '相册' },
  { key: 'milestone', value: props.stats.milestoneCount, label: '里程碑' },
  { key: 'age', value: props.stats.earliestBabyAgeDays ?? 0, label: '宝宝天' },
])
</script>

<template>
  <section class="bee-statbar" :class="{ 'bee-statbar--compact': compact }" aria-label="家庭概览">
    <div v-for="cell in cells" :key="cell.key" class="bee-statbar__cell">
      <strong class="bee-statbar__value">{{ formatNumber(cell.value) }}</strong>
      <span class="bee-statbar__label">{{ cell.label }}</span>
    </div>
  </section>
</template>

<style scoped>
.bee-statbar {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  height: 88px;
  border-bottom: 1px solid var(--border-subtle);
}

.bee-statbar__cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border-left: 1px solid var(--border-subtle);
}

.bee-statbar__cell:first-child {
  border-left: 0;
}

.bee-statbar__value {
  font-family: var(--font-display);
  font-size: 32px;
  line-height: 1.15;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.bee-statbar__label {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.bee-statbar--compact {
  height: 72px;
}

.bee-statbar--compact .bee-statbar__value {
  font-size: 24px;
}

@media (max-width: 767px) {
  .bee-statbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    height: 140px;
  }

  .bee-statbar__cell:nth-child(odd) {
    border-left: 0;
  }

  .bee-statbar__cell:nth-child(n + 3) {
    border-top: 1px solid var(--border-subtle);
  }

  .bee-statbar__value {
    font-size: 26px;
  }
}
</style>
