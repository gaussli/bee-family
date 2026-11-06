<script setup lang="ts">
/**
 * 上传队列列表（docs/06 §9.1 / §9.2）。
 *
 * <p>HEIC 四态：① 识别中（骨架呼吸、无格式 Chip）→ ② 转码中（斜纹不确定进度条）
 * → ③ 成功（明示 1600px 展示副本，条目 72px）→ ④ 失败（服务端原文 + 重试，条目 96–116px）。
 * 非 HEIC 沿用常规态。队列里不存在任何 GIF 分支（GIF 在选择阶段已被拦截）。</p>
 *
 * <p>失败文案逐字取 `task.errorMessage`（服务端 `failures[].message`），
 * 前端不按 code 生成本地文案。</p>
 */
import { computed, ref } from 'vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeTag from '@/components/base/BeeTag.vue'
import { HEIC_COPY_TEXT } from '@/utils/validate'
import { formatBytes } from '@/utils/format'
import type { UploadPhase, UploadSummary, UploadTask } from '@/types/upload'

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

/** 状态行文案：四态优先，失败态用服务端原文。 */
function statusText(task: UploadTask): string {
  if (task.phase === 'failed') return task.errorMessage ?? '上传失败'
  if (task.phase === 'done') {
    return task.isHeic ? `已上传 · ${HEIC_COPY_TEXT}` : '已上传'
  }
  if (task.phase === 'detecting') return '正在识别照片格式…'
  if (task.phase === 'transcoding') return '正在转换 HEIC 为通用格式…'
  if (task.phase === 'queued') return '等待上传'
  return `正在上传… ${task.progress}%`
}

function isIndeterminate(task: UploadTask): boolean {
  return task.phase === 'transcoding'
}

function showBar(task: UploadTask): boolean {
  return task.phase === 'transcoding' || task.phase === 'uploading' || task.phase === 'done'
}

function barWidth(task: UploadTask): string {
  if (task.phase === 'done') return '100%'
  if (task.phase === 'transcoding') return '100%'
  return `${task.progress}%`
}

function phaseClass(task: UploadTask): string {
  const phase: UploadPhase = task.phase
  return `is-phase-${phase}`
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
      <li
        v-for="task in visibleTasks"
        :key="task.id"
        class="bee-upload-queue__item"
        :class="[phaseClass(task), { 'is-heic': task.isHeic }]"
      >
        <!-- HEIC 不使用本地预览：40×40 蜜金底 + mono「HEIC」占位块 -->
        <span v-if="task.isHeic" class="bee-upload-queue__thumb bee-upload-queue__thumb--heic" aria-hidden="true">
          HEIC
        </span>
        <span v-else class="bee-upload-queue__thumb">
          <img :src="task.previewUrl" :alt="task.fileName" />
        </span>

        <div class="bee-upload-queue__main">
          <div class="bee-upload-queue__row">
            <span class="bee-upload-queue__name" :title="task.fileName">{{ task.fileName }}</span>
            <BeeTag
              v-if="task.isHeic && task.phase !== 'detecting'"
              class="bee-upload-queue__format"
              variant="outline"
              size="sm"
            >
              HEIC
            </BeeTag>
            <span class="bee-upload-queue__status" :class="{ 'is-long': task.phase === 'done' && task.isHeic }">
              {{ statusText(task) }}
            </span>
          </div>
          <div v-if="showBar(task)" class="bee-upload-queue__bar">
            <span
              class="bee-upload-queue__bar-fill"
              :class="{
                'is-error': task.phase === 'failed',
                'is-done': task.phase === 'done',
                'is-indeterminate': isIndeterminate(task),
              }"
              :style="{ width: barWidth(task) }"
            />
          </div>
        </div>

        <button
          v-if="task.phase === 'failed'"
          class="bee-upload-queue__retry"
          type="button"
          @click="emit('retry', task.id)"
        >
          重试
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
        <BeeIcon v-else-if="task.phase === 'done'" class="bee-upload-queue__done" name="check" :size="15" />
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
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
  min-height: 56px;
  padding: var(--sp-2);
  border-radius: var(--r-sm);
  background: var(--bg-sunken);
}

/* ① 识别中：40×40 骨架呼吸；尚未显示 HEIC 占位块与格式 Chip */
.bee-upload-queue__item.is-phase-detecting .bee-upload-queue__thumb {
  background: var(--warm-100);
  animation: bee-breathe 2.4s var(--ease-in-out) infinite;
}

.bee-upload-queue__item.is-phase-detecting .bee-upload-queue__thumb img {
  opacity: 0.35;
}

/* ② 转码中：左侧旋转环 */
.bee-upload-queue__item.is-phase-transcoding::before {
  content: '';
  position: absolute;
  left: 2px;
  top: 22px;
  width: 12px;
  height: 12px;
  border: 2px solid var(--honey-200);
  border-top-color: var(--honey-500);
  border-radius: var(--r-full);
  animation: bee-spin 900ms linear infinite;
}

/* ③ 成功：状态行允许折 2 行，条目高度自适应至 72px */
.bee-upload-queue__item.is-phase-done.is-heic {
  min-height: 72px;
}

/* ④ 失败：左 3px 危险竖条 + 危险底，高度自适应 96–116px */
.bee-upload-queue__item.is-phase-failed {
  min-height: 96px;
  border-left: 3px solid var(--color-danger);
  background: var(--color-danger-bg);
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

.bee-upload-queue__thumb--heic {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--honey-50);
  color: var(--text-brand);
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.bee-upload-queue__main {
  flex: 1;
  min-width: 0;
}

.bee-upload-queue__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.bee-upload-queue__name {
  min-width: 0;
  max-width: 100%;
  font-size: 12px;
  color: var(--text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-upload-queue__format {
  flex: none;
  height: 20px;
  font-size: 11px;
}

.bee-upload-queue__status {
  margin-left: auto;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.bee-upload-queue__status.is-long {
  flex-basis: 100%;
  text-align: left;
  margin-left: 0;
  color: var(--color-success);
}

.is-phase-transcoding .bee-upload-queue__status {
  color: var(--text-secondary);
}

.is-phase-failed .bee-upload-queue__status {
  margin-left: 0;
  flex-basis: 100%;
  white-space: normal;
  color: var(--text-secondary);
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

/* ② 不确定进度：斜纹循环，颜色只复用既有 token */
.bee-upload-queue__bar-fill.is-indeterminate {
  background: repeating-linear-gradient(
    -45deg,
    var(--honey-500) 0,
    var(--honey-500) 6px,
    var(--honey-200) 6px,
    var(--honey-200) 12px
  );
  animation: bee-stripes var(--dur-base) linear infinite;
}

@keyframes bee-stripes {
  to {
    background-position: 24px 0;
  }
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

.bee-upload-queue__retry {
  position: absolute;
  right: var(--sp-2);
  bottom: var(--sp-2);
  font-size: 12px;
  color: var(--honey-700);
}

.bee-upload-queue__retry:hover {
  text-decoration: underline;
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
