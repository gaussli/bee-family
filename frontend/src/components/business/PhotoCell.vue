<script setup lang="ts">
/**
 * 照片单元（02 文档 §4.10）。
 *
 * <p>固定 1:1，圆角 6（网格密集时避免缝隙过大）。悬停显示蒙层与勾选框；
 * 选中时蒙层加深 + 内描边 + 勾选框填充蜜金。</p>
 */
import { computed } from 'vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeImage from '@/components/base/BeeImage.vue'
import { fileUrl } from '@/utils/fileUrl'
import type { Photo } from '@/types/models'

interface Props {
  photo: Photo
  index: number
  selectable?: boolean
  selected?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  selectable: false,
  selected: false,
})

const emit = defineEmits<{ open: [index: number]; toggle: [id: number] }>()

const thumbUrl = computed<string>(() => fileUrl(props.photo.thumbKey, 'thumb'))

function onClick(): void {
  if (props.selectable) {
    emit('toggle', props.photo.id)
    return
  }
  emit('open', props.index)
}
</script>

<template>
  <button
    class="bee-photo-cell"
    :class="{ 'is-selected': selected, 'is-selectable': selectable }"
    type="button"
    :aria-label="photo.title ?? `照片 ${index + 1}`"
    :aria-pressed="selectable ? selected : undefined"
    @click="onClick"
  >
    <BeeImage
      v-if="thumbUrl"
      class="bee-photo-cell__image"
      :src="thumbUrl"
      :alt="photo.title ?? '照片'"
      ratio="1/1"
      radius="0"
      fit="cover"
    />
    <span v-else class="bee-photo-cell__fallback">
      <BeeIcon name="image-broken" :size="20" />
    </span>

    <span class="bee-photo-cell__scrim" aria-hidden="true" />

    <span v-if="selectable" class="bee-photo-cell__check" :class="{ 'is-on': selected }" aria-hidden="true">
      <BeeIcon v-if="selected" name="check" :size="12" :stroke-width="2.5" />
    </span>

    <span v-if="photo.title" class="bee-photo-cell__caption">{{ photo.title }}</span>
  </button>
</template>

<style scoped>
.bee-photo-cell {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: var(--r-sm);
  overflow: hidden;
  background: var(--warm-100);
  content-visibility: auto;
  contain-intrinsic-size: 180px;
}

.bee-photo-cell:hover .bee-photo-cell__scrim {
  opacity: 1;
}

.bee-photo-cell.is-selected .bee-photo-cell__scrim {
  opacity: 1;
  background: color-mix(in srgb, var(--warm-900) 32%, transparent);
}

.bee-photo-cell.is-selected {
  box-shadow: inset 0 0 0 2px var(--honey-500);
}

.bee-photo-cell:focus-visible {
  outline: 2px solid var(--honey-500);
  outline-offset: 2px;
}

.bee-photo-cell__fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--text-tertiary);
}

.bee-photo-cell__scrim {
  position: absolute;
  inset: 0;
  opacity: 0;
  background: var(--overlay-photo-hover);
  transition: opacity var(--dur-base) var(--ease-out);
  pointer-events: none;
}

.bee-photo-cell__check {
  position: absolute;
  top: 8px;
  left: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--r-sm);
  border: 1.5px solid var(--text-inverse);
  background: color-mix(in srgb, var(--warm-900) 20%, transparent);
  color: var(--text-inverse);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.bee-photo-cell__check.is-on {
  background: var(--honey-500);
  border-color: var(--honey-500);
}

.bee-photo-cell__caption {
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: 8px;
  color: var(--text-inverse);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  text-align: left;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  opacity: 0;
  transition: opacity var(--dur-base) var(--ease-out);
}

.bee-photo-cell:hover .bee-photo-cell__caption {
  opacity: 1;
}

/* 底部渐变遮罩：仅在 hover 时出现 */
.bee-photo-cell:hover::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 40px;
  background: var(--overlay-photo);
  pointer-events: none;
}
</style>
