<script setup lang="ts">
/**
 * 时间轴月份分组。
 *
 * <p>月份标题 `sticky top: var(--topbar-h)`，背景用页面色 92% + 8px 模糊，
 * 让下方的照片在滚动时"穿过"标题而不刺眼。脊柱线由本组件绘制一次，
 * 保证跨条目连续（画在条目上会因为 gap 断开）。</p>
 */
import TimelineItem from './TimelineItem.vue'
import type { TimelineGroup as TimelineGroupModel } from '@/types/timeline'

interface Props {
  group: TimelineGroupModel
}

defineProps<Props>()

const emit = defineEmits<{ openPhoto: [photoId: number]; openMilestone: [milestoneId: number] }>()
</script>

<template>
  <section class="bee-timeline-group" :aria-label="group.label">
    <h2 class="bee-timeline-group__label">
      <span class="bee-timeline-group__title">{{ group.label }}</span>
      <span class="bee-timeline-group__rule" aria-hidden="true" />
    </h2>

    <div class="bee-timeline-group__items">
      <span class="bee-timeline-group__spine" aria-hidden="true" />
      <TimelineItem
        v-for="(item, index) in group.items"
        :key="`${item.type}-${item.id}`"
        :item="item"
        :index="index"
        @open-photo="emit('openPhoto', $event)"
        @open-milestone="emit('openMilestone', $event)"
      />
    </div>
  </section>
</template>

<style scoped>
.bee-timeline-group + .bee-timeline-group {
  margin-top: var(--sp-8);
}

.bee-timeline-group__label {
  position: sticky;
  top: var(--topbar-h);
  z-index: 10;
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  height: 44px;
  margin-bottom: var(--sp-4);
  background: color-mix(in srgb, var(--bg-page) 92%, transparent);
  backdrop-filter: blur(8px);
}

.bee-timeline-group__title {
  font-family: var(--font-display);
  font-size: 20px;
  line-height: 1;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
}

.bee-timeline-group__rule {
  flex: 1;
  height: 1px;
  background: var(--border-subtle);
}

.bee-timeline-group__items {
  position: relative;
}

.bee-timeline-group__spine {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 79px;
  width: 1px;
  background: var(--warm-300);
}

@media (max-width: 767px) {
  .bee-timeline-group__label {
    top: var(--topbar-h);
    height: 36px;
    margin-bottom: var(--sp-2);
  }

  .bee-timeline-group__title {
    font-size: var(--fs-body-lg);
    font-weight: 600;
  }

  .bee-timeline-group__spine {
    display: none;
  }
}
</style>
