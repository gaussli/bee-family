<script setup lang="ts">
/**
 * 上传浮层（右下角，宽 360）。
 *
 * <p>关闭浮层**不会中断上传**：组件用 `v-show` 常驻，队列实例跟着组件生命周期走。
 * 失败项可单条重试；全部完成时给出成功提示并自动收起。</p>
 *
 * <p>注意后端行为：`/photos/upload` 恒返回 200，失败明细在 `failures[]`，
 * 因此这里只依赖 `useUploadQueue` 解析后的 task 状态，绝不按 HTTP 状态判错。</p>
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeFileDrop from '@/components/base/BeeFileDrop.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import UploadQueue from './UploadQueue.vue'
import { useUploadQueue } from '@/composables/useUploadQueue'
import { useToast } from '@/composables/useToast'
import type { Photo } from '@/types/models'

interface Props {
  albumId?: number | null
  open: boolean
}

const props = withDefaults(defineProps<Props>(), {
  albumId: null,
})

const emit = defineEmits<{ close: []; uploaded: [photo: Photo] }>()

const toast = useToast()

const queue = useUploadQueue({
  onUploaded: (photo) => {
    uploadedThisRound.value += 1
    emit('uploaded', photo)
  },
  onFailed: (task) => {
    toast.danger(`${task.fileName}：${task.errorMessage ?? '上传失败'}`)
  },
  onSettled: () => {
    if (uploadedThisRound.value > 0) {
      toast.success(`已上传 ${uploadedThisRound.value} 张照片`)
      uploadedThisRound.value = 0
    }
  },
})

const uploadedThisRound = ref<number>(0)
const showDrop = ref<boolean>(false)

const hasTasks = computed<boolean>(() => queue.tasks.value.length > 0)

const title = computed<string>(() => {
  if (queue.isUploading.value) return '正在上传'
  if (queue.failedCount.value > 0) return '上传完成，部分失败'
  if (hasTasks.value) return '上传完成'
  return '上传照片'
})

const summaryText = computed<string>(() => {
  if (!hasTasks.value) return '拖拽或选择照片，支持 JPG / PNG / WebP，单张 ≤ 20MB'
  return `${queue.summary.value.done} / ${queue.summary.value.total} 已完成`
})

function onFiles(files: File[]): void {
  if (files.length === 0) return
  queue.addFiles(files, props.albumId)
  showDrop.value = false
}

function onClose(): void {
  emit('close')
}

function onClear(): void {
  queue.clearFinished()
}

watch(
  () => props.open,
  (value) => {
    if (value) showDrop.value = true
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  queue.dispose()
})

/**
 * 暴露给父级：拖拽整页投放时由父级把文件递进来（避免再做一套隐藏 input）。
 */
defineExpose({
  addFiles: (files: File[]): void => {
    if (files.length === 0) return
    queue.addFiles(files, props.albumId)
  },
})
</script>

<template>
  <div v-show="open" class="bee-upload-panel" role="dialog" aria-label="上传照片">
    <header class="bee-upload-panel__head">
      <div class="bee-upload-panel__head-main">
        <h2 class="bee-upload-panel__title">{{ title }}</h2>
        <p class="bee-upload-panel__summary">{{ summaryText }}</p>
      </div>
      <button class="bee-upload-panel__close" type="button" aria-label="收起上传面板" @click="onClose">
        <BeeIcon name="close" :size="16" />
      </button>
    </header>

    <div class="bee-upload-panel__body">
      <BeeFileDrop v-if="showDrop || !hasTasks" @files="onFiles" />

      <UploadQueue
        v-if="hasTasks"
        :tasks="queue.tasks.value"
        :summary="queue.summary.value"
        @retry="queue.retry"
        @cancel="queue.cancel"
      />

      <div v-if="hasTasks" class="bee-upload-panel__foot">
        <BeeButton variant="ghost" size="sm" @click="onClear">清除已完成</BeeButton>
        <BeeButton
          v-if="queue.isUploading.value"
          variant="ghost"
          size="sm"
          @click="queue.cancelAll"
        >
          全部取消
        </BeeButton>
        <BeeButton v-else variant="secondary" size="sm" @click="showDrop = true">继续添加</BeeButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bee-upload-panel {
  position: fixed;
  right: var(--sp-6);
  bottom: var(--sp-6);
  z-index: 950;
  display: flex;
  flex-direction: column;
  width: 360px;
  max-height: min(70vh, 560px);
  background: var(--bg-elevated);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.bee-upload-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-4) var(--sp-4) var(--sp-3);
  border-bottom: 1px solid var(--border-subtle);
}

.bee-upload-panel__head-main {
  min-width: 0;
}

.bee-upload-panel__title {
  font-size: var(--fs-body-lg);
  line-height: var(--lh-body-lg);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-upload-panel__summary {
  margin-top: 2px;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.bee-upload-panel__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: var(--r-sm);
  color: var(--text-tertiary);
}

.bee-upload-panel__close:hover {
  background: var(--warm-100);
  color: var(--text-primary);
}

.bee-upload-panel__body {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-4);
  overflow-y: auto;
}

.bee-upload-panel__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}

@media (max-width: 767px) {
  .bee-upload-panel {
    right: var(--sp-3);
    left: var(--sp-3);
    bottom: calc(var(--tabbar-h) + var(--sp-3));
    width: auto;
    max-height: 60vh;
  }
}
</style>
