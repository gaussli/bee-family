<script setup lang="ts">
/**
 * 里程碑卡（02 文档 §4.14）。
 *
 * <p>左侧 3px 珊瑚竖条 + 六边形徽章 + 标题/日期/描述 + 可选 72×72 配图。
 * `editable` 时悬停浮出编辑与删除，移动端常显。</p>
 */
import { computed } from 'vue'
import BeeHexBadge from '@/components/base/BeeHexBadge.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeImage from '@/components/base/BeeImage.vue'
import { fileUrl } from '@/utils/fileUrl'
import { formatDate } from '@/utils/format'
import type { Milestone } from '@/types/models'

interface Props {
  milestone: Milestone
  editable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  editable: false,
})

const emit = defineEmits<{
  edit: [milestone: Milestone]
  remove: [milestone: Milestone]
  openPhoto: [photoId: number]
}>()

const thumbUrl = computed<string>(() => fileUrl(props.milestone.photoThumbKey, 'thumb'))

const dateText = computed<string>(() => {
  const base = formatDate(props.milestone.achievedAt)
  return props.milestone.ageText ? `${base} · ${props.milestone.ageText}` : base
})

function onEdit(): void {
  emit('edit', props.milestone)
}

function onRemove(): void {
  emit('remove', props.milestone)
}

function onPhoto(): void {
  if (props.milestone.photoId !== null) emit('openPhoto', props.milestone.photoId)
}
</script>

<template>
  <article class="bee-milestone-card">
    <span class="bee-milestone-card__accent" aria-hidden="true" />
    <BeeHexBadge :size="32" variant="coral" icon="heart" />

    <div class="bee-milestone-card__main">
      <h3 class="bee-milestone-card__title">{{ milestone.title }}</h3>
      <p class="bee-milestone-card__date">{{ dateText }}</p>
      <p v-if="milestone.description" class="bee-milestone-card__desc">{{ milestone.description }}</p>
    </div>

    <button
      v-if="thumbUrl"
      class="bee-milestone-card__thumb"
      type="button"
      aria-label="查看里程碑配图"
      @click="onPhoto"
    >
      <BeeImage :src="thumbUrl" :alt="milestone.title" ratio="1/1" radius="var(--r-md)" />
    </button>

    <div v-if="editable" class="bee-milestone-card__actions">
      <button class="bee-milestone-card__action" type="button" aria-label="编辑里程碑" @click="onEdit">
        <BeeIcon name="edit" :size="14" />
      </button>
      <button class="bee-milestone-card__action" type="button" aria-label="删除里程碑" @click="onRemove">
        <BeeIcon name="trash" :size="14" />
      </button>
    </div>
  </article>
</template>

<style scoped>
.bee-milestone-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  min-height: 88px;
  padding: var(--sp-4) var(--sp-4) var(--sp-4) calc(var(--sp-4) + 3px);
  background: var(--bg-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-sm);
  transition: box-shadow var(--dur-base) var(--ease-out);
}

.bee-milestone-card:hover {
  box-shadow: var(--shadow-md);
}

.bee-milestone-card__accent {
  position: absolute;
  top: var(--sp-4);
  bottom: var(--sp-4);
  left: 0;
  width: 3px;
  border-radius: 2px;
  background: var(--accent-coral);
}

.bee-milestone-card__main {
  flex: 1;
  min-width: 0;
}

.bee-milestone-card__title {
  font-size: var(--fs-h4);
  line-height: var(--lh-h4);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-milestone-card__date {
  margin-top: 2px;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.bee-milestone-card__desc {
  margin-top: var(--sp-1);
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.bee-milestone-card__thumb {
  width: 72px;
  flex: none;
  border-radius: var(--r-md);
  overflow: hidden;
}

.bee-milestone-card__actions {
  position: absolute;
  top: var(--sp-2);
  right: var(--sp-2);
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity var(--dur-fast) var(--ease-out);
}

.bee-milestone-card:hover .bee-milestone-card__actions,
.bee-milestone-card:focus-within .bee-milestone-card__actions {
  opacity: 1;
}

.bee-milestone-card__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--r-sm);
  background: var(--bg-elevated);
  color: var(--text-tertiary);
  box-shadow: var(--shadow-sm);
}

.bee-milestone-card__action:hover {
  color: var(--text-brand);
}

@media (max-width: 767px) {
  .bee-milestone-card__actions {
    opacity: 1;
  }

  .bee-milestone-card__thumb {
    width: 56px;
  }
}
</style>
