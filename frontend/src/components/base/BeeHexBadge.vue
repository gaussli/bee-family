<script setup lang="ts">
/**
 * 六边形徽章。品牌母题的最小载体，用于里程碑、照片数、宝宝头像替代位。
 *
 * `size` 为外接圆直径；高度按 1 : 1.1547 计算（正六边形尖顶比例）。
 */
import { computed } from 'vue'
import BeeIcon from './BeeIcon.vue'
import type { IconName } from './icon-paths'

interface Props {
  size?: number
  variant?: 'honey' | 'coral' | 'outline'
  icon?: IconName
}

const props = withDefaults(defineProps<Props>(), {
  size: 32,
  variant: 'honey',
})

const iconSize = computed<number>(() => Math.round(props.size * 0.5))
</script>

<template>
  <span
    class="bee-hex"
    :class="`bee-hex--${variant}`"
    :style="{ width: `${size}px`, height: `${Math.round(size * 1.1547)}px` }"
  >
    <span class="bee-hex__inner">
      <BeeIcon v-if="icon" :name="icon" :size="iconSize" />
      <slot v-else />
    </span>
  </span>
</template>

<style scoped>
.bee-hex {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
}

.bee-hex__inner {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.bee-hex--honey {
  background: var(--honey-100);
  color: var(--text-brand);
}

.bee-hex--coral {
  background: var(--accent-coral);
  color: var(--text-inverse);
}

.bee-hex--outline {
  background: transparent;
  color: var(--honey-400);
  border: 0;
  position: relative;
}

.bee-hex--outline::after {
  content: '';
  position: absolute;
  inset: 2px;
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
  border: 1.5px solid currentColor;
  background: transparent;
}
</style>
