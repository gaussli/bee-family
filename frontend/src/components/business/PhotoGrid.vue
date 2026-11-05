<script setup lang="ts">
/**
 * 照片网格（02 文档 §4.10 / §4.20）。
 *
 * <p>列数 3 / 4 / 5 由 CSS 断点决定，gap 4 / 8 / 12 同步变化。
 * 底部哨兵由父级通过 `useInfiniteScroll` 驱动（父级掌握分页状态），
 * 本组件只负责渲染与「加载更多」的视觉占位。</p>
 */
import { computed } from 'vue'
import PhotoCell from './PhotoCell.vue'
import BeeSkeleton from '@/components/base/BeeSkeleton.vue'
import type { Photo } from '@/types/models'

interface Props {
  photos: Photo[]
  selectionMode?: boolean
  selected?: Set<number>
  loadingMore?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  selectionMode: false,
  selected: undefined,
  loadingMore: false,
})

const emit = defineEmits<{ open: [index: number]; toggle: [id: number] }>()

/** 超过 200 项时才启用 `content-visibility`（少量图片时反而增加开销）。 */
const heavy = computed<boolean>(() => props.photos.length > 200)

function isSelected(id: number): boolean {
  return props.selected?.has(id) ?? false
}
</script>

<template>
  <div class="bee-photo-grid" :class="{ 'is-heavy': heavy }">
    <PhotoCell
      v-for="(photo, index) in photos"
      :key="photo.id"
      :photo="photo"
      :index="index"
      :selectable="selectionMode"
      :selected="isSelected(photo.id)"
      @open="emit('open', $event)"
      @toggle="emit('toggle', $event)"
    />
  </div>

  <BeeSkeleton v-if="loadingMore" class="bee-photo-grid__more" variant="grid" :count="10" :columns="5" />
</template>

<style scoped>
.bee-photo-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
}

.bee-photo-grid__more {
  margin-top: var(--sp-2);
}

@media (min-width: 768px) {
  .bee-photo-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8px;
  }
}

@media (min-width: 1280px) {
  .bee-photo-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 12px;
  }
}
</style>
