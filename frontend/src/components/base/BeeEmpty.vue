<script setup lang="ts">
/**
 * 空状态。插画区固定 160×160：蜂巢底纹（3 行 × 4 列六边形）+ 中央线性图标。
 * `compact` 用于卡片内部等受限高度场景（插画降为 96×96）。
 */
import { computed } from 'vue'
import BeeButton from './BeeButton.vue'
import BeeIcon from './BeeIcon.vue'
import type { IconName } from './icon-paths'

interface Props {
  /** v1.1 扩展 3 个无家庭引导变体；原有 5 变体不变（docs/06 §2.2）。 */
  variant?:
    | 'no-photo'
    | 'no-album'
    | 'no-result'
    | 'no-growth'
    | 'error'
    | 'no-family'
    | 'join-pending'
    | 'join-rejected'
  title: string
  description?: string
  actionText?: string
  compact?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'no-photo',
  description: '',
  actionText: '',
  compact: false,
})

const emit = defineEmits<{ action: [] }>()

const ICONS: Record<NonNullable<Props['variant']>, IconName> = {
  'no-photo': 'camera',
  'no-album': 'grid',
  'no-result': 'search',
  'no-growth': 'baby',
  error: 'image-broken',
  'no-family': 'hex',
  'join-pending': 'hex',
  'join-rejected': 'info',
}

/** 蜂巢底纹的 12 个六边形坐标（相对 160×160）。 */
const CELLS = computed<{ x: number; y: number }[]>(() => {
  const result: { x: number; y: number }[] = []
  for (let row = 0; row < 3; row += 1) {
    for (let col = 0; col < 4; col += 1) {
      result.push({ x: 26 + col * 36 + (row % 2 === 0 ? 0 : 18), y: 34 + row * 46 })
    }
  }
  return result
})

const icon = computed<IconName>(() => ICONS[props.variant])
</script>

<template>
  <div class="bee-empty" :class="{ 'bee-empty--compact': compact, 'bee-empty--error': variant === 'error' }">
    <div class="bee-empty__art" aria-hidden="true">
      <svg viewBox="0 0 160 160" width="100%" height="100%">
        <polygon
          v-for="(cell, i) in CELLS"
          :key="i"
          :points="`${cell.x},${cell.y - 9} ${cell.x + 8},${cell.y - 4.5} ${cell.x + 8},${cell.y + 4.5} ${cell.x},${cell.y + 9} ${cell.x - 8},${cell.y + 4.5} ${cell.x - 8},${cell.y - 4.5}`"
          fill="var(--honey-50)"
        />
      </svg>
      <span class="bee-empty__icon">
        <BeeIcon :name="icon" :size="48" :stroke-width="1.5" />
      </span>
    </div>

    <h3 class="bee-empty__title">{{ title }}</h3>
    <p v-if="description" class="bee-empty__desc">{{ description }}</p>
    <BeeButton
      v-if="actionText"
      class="bee-empty__action"
      :variant="variant === 'error' ? 'secondary' : 'primary'"
      @click="emit('action')"
    >
      {{ actionText }}
    </BeeButton>
  </div>
</template>

<style scoped>
.bee-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--sp-12) var(--sp-4);
  text-align: center;
}

.bee-empty--compact {
  padding: var(--sp-6) var(--sp-4);
}

.bee-empty__art {
  position: relative;
  width: 160px;
  height: 160px;
}

.bee-empty--compact .bee-empty__art {
  width: 96px;
  height: 96px;
}

.bee-empty__icon {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--honey-300);
}

.bee-empty--error .bee-empty__icon {
  color: var(--color-danger);
}

.bee-empty--compact .bee-empty__icon :deep(svg) {
  width: 32px;
  height: 32px;
}

.bee-empty__title {
  margin-top: var(--sp-5);
  font-family: var(--font-display);
  font-size: var(--fs-h3);
  line-height: var(--lh-h3);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-empty--compact .bee-empty__title {
  margin-top: var(--sp-3);
  font-family: var(--font-sans);
  font-size: var(--fs-body-lg);
  line-height: var(--lh-body-lg);
}

.bee-empty__desc {
  margin-top: var(--sp-2);
  max-width: 320px;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
}

.bee-empty__action {
  margin-top: var(--sp-5);
}
</style>
