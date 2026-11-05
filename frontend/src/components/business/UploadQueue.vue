<script setup lang="ts">
/**
 * 上传队列列表（02 文档 §4.17 / 上传浮层内）。
 *
 * <p>单条：40×40 本地缩略图 + 文件名 + 3px 进度条 + 状态文案；
 * 失败态给「重试」，进行中给「取消」。最多展示 4 条，其余折叠成一行汇总。</p>
 */
import { computed, ref } from 'vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import { formatBytes } from '@/utils/format'
import type { UploadSummary, UploadTask } from '@/types/upload'

interface Props {
  tasks: UploadTask[]
  summary: UploadSummary
}

const props = defineProps<Props>()

const emit = defineEmits<{ retry: [id: number]; cancel: [id: number] }>()

const expanded = ref<boolean>(false)

const COLLAPSED_LIMIT = 4

const visibleTasks = computed<UploadTask[]>(() =>
  expanded.value ? props.tasks : props.tasks.slice(0, COLLAPSED_LIMIT),
)

const hiddenCount = computed<number>(() => Math.max(props.tasks.length - COLLAPSED_LIMIT, 0))

const overallPercent = computed<number>(() => {
  if (props.summary.total === 0) return 0
  return Math.round((props.summary.done / props.summary.total) * 100)
})

function statusText(task: UploadTask): string {
  if (task.status === 'queued') return '等待中'
  if (task.status === 'uploading') return `${task.progress}%`
  if (task.status === 'done') return '已完成'
  return task.errorMessage ?? '失败'
}
</script>

<template>
  <div class="bee-upload-queue">
    <div class="bee-upload-queue__overall">
      <span class="bee-upload-queue__overall-text">
        已完成 {{ summary.done }} / {{ summary.total }}
        <template v-if="summary.bytesPerSec > 0"> · {{ formatBytes(summary.bytesPerSec) }}/s</template>
      </span>
      <span class="bee-upload-queue__overall-percent">{{ overallPercent }}%</span>
    </div>

    <ul class="bee-upload-queue__list">
      <li v-for="task in visibleTasks" :key="task.id" class="bee-upload-queue__item" :class="`is-${task.status}`">
        <span class="bee-upload-queue__thumb">
          <img :src="task.previewUrl" :alt="task.fileName" />
        </span>

        <div class="bee-upload-queue__main">
          <div class="bee-upload-queue__row">
            <span class="bee-upload-queue__name" :title="task.fileName">{{ task.fileName }}</span>
            <span class="bee-upload-queue__status">{{ statusText(task) }}</span>
          </div>
          <div class="bee-upload-queue__bar">
            <span
              class="bee-upload-queue__bar-fill"
              :class="{ 'is-error': task.status === 'error', 'is-done': task.status === 'done' }"
              :style="{ width: `${task.status === 'done' ? 100 : task.progress}%` }"
            />
          </div>
        </div>

        <button
          v-if="task.status === 'error'"
          class="bee-upload-queue__action"
          type="button"
          title="重试"
          aria-label="重试"
          @click="emit('retry', task.id)"
        >
          <BeeIcon name="refresh" :size="15" />
        </button>
        <button
          v-else-if="task.status === 'uploading' || task.status === 'queued'"
          class="bee-upload-queue__action"
          type="button"
          title="取消"
          aria-label="取消上传"
          @click="emit('cancel', task.id)"
        >
          <BeeIcon name="close" :size="14" />
        </button>
        <BeeIcon v-else class="bee-upload-queue__done" name="check" :size="15" />
      </li>
    </ul>

    <button v-if="hiddenCount > 0" class="bee-upload-queue__toggle" type="button" @click="expanded = !expanded">
      {{ expanded ? '收起' : `还有 ${hiddenCount} 个文件` }}
    </button>
  </div>
</template>

<style scoped>
.bee-upload-queue {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.bee-upload-queue__overall {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.bee-upload-queue__overall-percent {
  color: var(--text-secondary);
  font-weight: 500;
}

.bee-upload-queue__list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.bee-upload-queue__item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-2);
  border-radius: var(--r-sm);
  background: var(--bg-sunken);
}

.bee-upload-queue__thumb {
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: var(--r-sm);
  overflow: hidden;
  background: var(--warm-200);
}

.bee-upload-queue__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.bee-upload-queue__main {
  flex: 1;
  min-width: 0;
}

.bee-upload-queue__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}

.bee-upload-queue__name {
  font-size: 12px;
  color: var(--text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-upload-queue__status {
  flex: none;
  font-size: 11px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.is-error .bee-upload-queue__status {
  color: var(--color-danger);
}

.bee-upload-queue__bar {
  height: 3px;
  margin-top: 5px;
  border-radius: var(--r-full);
  background: var(--warm-200);
  overflow: hidden;
}

.bee-upload-queue__bar-fill {
  display: block;
  height: 100%;
  width: 0;
  border-radius: var(--r-full);
  background: var(--honey-500);
  transition: width var(--dur-base) var(--ease-out);
}

.bee-upload-queue__bar-fill.is-error {
  background: var(--color-danger);
}

.bee-upload-queue__bar-fill.is-done {
  background: var(--color-success);
}

.bee-upload-queue__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex: none;
  border-radius: var(--r-sm);
  color: var(--text-tertiary);
}

.bee-upload-queue__action:hover {
  background: var(--warm-200);
  color: var(--text-primary);
}

.bee-upload-queue__done {
  color: var(--color-success);
  flex: none;
}

.bee-upload-queue__toggle {
  align-self: flex-start;
  font-size: var(--fs-caption);
  color: var(--text-brand);
  padding: 2px 0;
}

.bee-upload-queue__toggle:hover {
  text-decoration: underline;
}
</style>
