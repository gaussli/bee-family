<script setup lang="ts">
/**
 * 相册卡（核心组件，02 文档 §4.9）。
 *
 * <p>整卡为 `<RouterLink>`；悬停时封面轻微放大、浮出编辑/删除按钮，
 * 两个按钮必须 `@click.stop` 并 `preventDefault`，否则会触发路由跳转。</p>
 */
import { computed } from 'vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeImage from '@/components/base/BeeImage.vue'
import { fileUrl } from '@/utils/fileUrl'
import { formatDate } from '@/utils/format'
import type { Album } from '@/types/models'

interface Props {
  album: Album
  editable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  editable: false,
})

const emit = defineEmits<{ edit: [album: Album]; remove: [album: Album] }>()

const coverUrl = computed<string>(() => fileUrl(props.album.coverThumbKey, 'thumb'))

const meta = computed<string>(() => {
  const count = `${props.album.photoCount} 张`
  const date = props.album.latestTakenAt ?? props.album.createdAt
  return date ? `${count} · ${formatDate(date)}` : count
})

function onEdit(event: MouseEvent): void {
  event.preventDefault()
  event.stopPropagation()
  emit('edit', props.album)
}

function onRemove(event: MouseEvent): void {
  event.preventDefault()
  event.stopPropagation()
  emit('remove', props.album)
}
</script>

<template>
  <RouterLink class="bee-album-card" :to="`/albums/${album.id}`" :aria-label="`打开相册 ${album.name}`">
    <div class="bee-album-card__cover">
      <BeeImage
        v-if="coverUrl"
        :src="coverUrl"
        :alt="`${album.name} 封面`"
        ratio="4/3"
        radius="0"
        fit="cover"
      />
      <div v-else class="bee-album-card__placeholder">
        <BeeIcon name="hex" :size="40" :stroke-width="1.2" />
        <span class="bee-album-card__placeholder-text">还没有照片</span>
      </div>

      <span class="bee-album-card__count">
        <BeeIcon name="hex" :size="10" />
        {{ album.photoCount }}
      </span>

      <div v-if="editable" class="bee-album-card__actions">
        <button class="bee-album-card__action" type="button" aria-label="编辑相册" @click="onEdit">
          <BeeIcon name="edit" :size="15" />
        </button>
        <button class="bee-album-card__action" type="button" aria-label="删除相册" @click="onRemove">
          <BeeIcon name="trash" :size="15" />
        </button>
      </div>
    </div>

    <div class="bee-album-card__info">
      <h3 class="bee-album-card__name">{{ album.name }}</h3>
      <p class="bee-album-card__meta">{{ meta }}</p>
    </div>
  </RouterLink>
</template>

<style scoped>
.bee-album-card {
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  transition:
    transform var(--dur-base) var(--ease-out),
    box-shadow var(--dur-base) var(--ease-out);
}

.bee-album-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.bee-album-card:focus-visible {
  outline: 2px solid var(--honey-500);
  outline-offset: 3px;
}

.bee-album-card__cover {
  position: relative;
  overflow: hidden;
  background: var(--honey-50);
}

.bee-album-card__cover :deep(.bee-image) {
  transition: transform var(--dur-base) var(--ease-out);
}

.bee-album-card:hover .bee-album-card__cover :deep(.bee-image) {
  transform: scale(1.04);
}

.bee-album-card__placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-1);
  aspect-ratio: 4 / 3;
  color: var(--honey-300);
}

.bee-album-card__placeholder-text {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-album-card__count {
  position: absolute;
  top: var(--sp-3);
  left: var(--sp-3);
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  height: 22px;
  padding: 0 var(--sp-2);
  border-radius: var(--r-full);
  background: color-mix(in srgb, var(--warm-900) 55%, transparent);
  backdrop-filter: blur(6px);
  color: var(--text-inverse);
  font-size: var(--fs-caption);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.bee-album-card__actions {
  position: absolute;
  top: var(--sp-2);
  right: var(--sp-2);
  display: flex;
  gap: 4px;
  opacity: 0;
  transform: translateY(-2px);
  transition:
    opacity var(--dur-fast) var(--ease-out),
    transform var(--dur-fast) var(--ease-out);
}

.bee-album-card:hover .bee-album-card__actions,
.bee-album-card:focus-within .bee-album-card__actions {
  opacity: 1;
  transform: translateY(0);
}

.bee-album-card__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--r-sm);
  background: var(--bg-surface);
  color: var(--text-secondary);
  box-shadow: var(--shadow-sm);
}

.bee-album-card__action:hover {
  color: var(--text-brand);
  background: var(--honey-50);
}

.bee-album-card__info {
  padding: var(--sp-4);
}

.bee-album-card__name {
  font-size: var(--fs-h4);
  line-height: var(--lh-h4);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-album-card__meta {
  margin-top: var(--sp-1);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

@media (max-width: 767px) {
  .bee-album-card__cover :deep(.bee-image) {
    aspect-ratio: 16 / 10;
  }

  .bee-album-card__info {
    padding: var(--sp-3);
  }

  .bee-album-card__actions {
    opacity: 1;
    transform: none;
  }
}
</style>
