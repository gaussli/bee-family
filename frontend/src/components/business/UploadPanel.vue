<script setup lang="ts">
/**
 * 上传浮层（右下角，宽 360；docs/06 §9.1 / §9.2）。
 *
 * <p>关闭浮层**不会中断上传**：组件用 `v-show` 常驻，队列实例跟着组件生命周期走。</p>
 *
 * <p>后端 `/photos/upload` 恒返回 200，成败在 `successes[] / failures[]`；
 * 失败文案逐字取 `failures[].message`，前端不按 code 生成本地文案。
 * HEIC 失败只做聚合 warning Toast + 条目内「重试」，不弹全局 Modal。</p>
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeFileDrop from '@/components/base/BeeFileDrop.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import UploadQueue from './UploadQueue.vue'
import { useUploadQueue } from '@/composables/useUploadQueue'
import { useSwitchStore } from '@/stores/switch'
import { useToast } from '@/composables/useToast'
import { GIF_REJECT_TEXT, HEIC_COPY_TEXT, SUPPORTED_FORMAT_TEXT } from '@/utils/validate'
import type { Photo } from '@/types/models'

interface Props {
  albumId?: number | null
  open: boolean
}

const props = withDefaults(defineProps<Props>(), { albumId: null })
const emit = defineEmits<{ close: []; uploaded: [photo: Photo] }>()

const toast = useToast()
const switchStore = useSwitchStore()

const uploadedThisRound = ref<number>(0)
const heicSuccessThisRound = ref<number>(0)
const heicFailedThisRound = ref<number>(0)
const showDrop = ref<boolean>(false)
const highlighted = ref<boolean>(false)

function onGifRejected(count: number): void {
  toast.warning(count === 1 ? GIF_REJECT_TEXT : `${count} 个 GIF 文件暂不支持，已跳过`)
}

const queue = useUploadQueue({
  onUploaded: (photo) => {
    uploadedThisRound.value += 1
    if (photo.sourceFormat === 'heic' || (photo.displayKey !== null && photo.displayKey !== undefined)) {
      heicSuccessThisRound.value += 1
    }
    emit('uploaded', photo)
  },
  onFailed: (task) => {
    if (task.isHeic) {
      heicFailedThisRound.value += 1
      return // 聚合 Toast 在 onSettled 统一给出，不逐条弹
    }
    toast.danger(`${task.fileName}：${task.errorMessage ?? '上传失败'}`)
  },
  onSettled: () => {
    if (uploadedThisRound.value > 0) {
      const prefix =
        heicSuccessThisRound.value > 0
          ? `已上传 ${uploadedThisRound.value} 张照片（含 ${heicSuccessThisRound.value} 张 HEIC），${HEIC_COPY_TEXT}`
          : `已上传 ${uploadedThisRound.value} 张照片`
      toast.success(prefix)
    }
    if (heicFailedThisRound.value > 0) {
      toast.warning(`${heicFailedThisRound.value} 张 HEIC 无法解析，已跳过`)
    }
    uploadedThisRound.value = 0
    heicSuccessThisRound.value = 0
    heicFailedThisRound.value = 0
  },
  onRejectedGif: onGifRejected,
})

const hasTasks = computed<boolean>(() => queue.tasks.value.length > 0)

const title = computed<string>(() => {
  if (queue.isUploading.value) return '正在上传'
  if (queue.failedCount.value > 0) return '上传完成，部分失败'
  if (hasTasks.value) return '上传完成'
  return '上传照片'
})

const summaryText = computed<string>(() => {
  if (!hasTasks.value) return `拖拽或选择照片，${SUPPORTED_FORMAT_TEXT}，单张 ≤ 20MB`
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

// 「查看上传队列」：聚焦浮层并高亮一次（200ms 动画由 CSS 自行结束）。
watch(
  () => switchStore.queueFocusNonce,
  () => {
    highlighted.value = true
  },
)

onBeforeUnmount(() => {
  queue.dispose()
})

/**
 * 暴露给父级：整页拖拽投放时由父级把文件递进来（避免再做一套隐藏 input）。
 */
defineExpose({
  addFiles: (files: File[]): void => {
    if (files.length === 0) return
    queue.addFiles(files, props.albumId)
  },
})
</script>

<template>
  <div
    v-show="open"
    class="bee-upload-panel"
    :class="{ 'is-highlighted': highlighted }"
    role="dialog"
    aria-label="上传照片"
    @animationend="highlighted = false"
  >
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
      <template v-if="showDrop || !hasTasks">
        <BeeFileDrop @files="onFiles" @rejected="onGifRejected" />
        <p class="bee-upload-panel__heic-hint">
          iPhone 的 HEIC 照片可直接上传，系统会自动转成通用格式用于浏览；下载时可选原始 HEIC 文件。
        </p>
      </template>

      <UploadQueue
        v-if="hasTasks"
        :tasks="queue.tasks.value"
        :summary="queue.summary.value"
        @retry="queue.retry"
        @cancel="queue.cancel"
      />

      <div v-if="hasTasks" class="bee-upload-panel__foot">
        <BeeButton variant="ghost" size="sm" @click="onClear">清除已完成</BeeButton>
        <BeeButton v-if="queue.isUploading.value" variant="ghost" size="sm" @click="queue.cancelAll">
          全部取消
        </BeeButton>
        <BeeButton v-else variant="secondary" size="sm" @click="showDrop = true">继续添加</BeeButton>
      </div>

      <!-- 浮层底部常驻说明（不随 Toast 消失），明示原图保留而非被压缩覆盖 -->
      <p class="bee-upload-panel__note">
        {{ HEIC_COPY_TEXT }}；HEIC 会生成 1600px 展示副本用于浏览，原图完整保留
      </p>
    </div>
  </div>
</template>

<style scoped>
.bee-upload-panel {
  position: fixed;
  right: var(--sp-6);
  bottom: var(--sp-6);
  z-index: var(--z-upload-panel);
  display: flex;
  flex-direction: column;
  width: 360px;
  max-height: min(70vh, 560px);
  background: var(--bg-elevated);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.bee-upload-panel.is-highlighted {
  animation: bee-queue-flash var(--dur-base) var(--ease-out);
}

@keyframes bee-queue-flash {
  0%,
  100% {
    box-shadow: var(--shadow-lg);
  }
  50% {
    box-shadow: 0 0 0 3px var(--honey-400), var(--shadow-lg);
  }
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

.bee-upload-panel__heic-hint {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-secondary);
}

.bee-upload-panel__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}

.bee-upload-panel__note {
  padding-top: var(--sp-2);
  border-top: 1px solid var(--border-subtle);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
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
