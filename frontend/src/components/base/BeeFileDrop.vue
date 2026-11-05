<script setup lang="ts">
/**
 * 拖放 / 点击选择文件。
 *
 * 默认渲染为一块虚线投放区（可被 `default` 插槽替换成自定义触发器）。
 * 拖拽进入时叠加高亮边框，离开时复原（用计数器避免子元素冒泡导致抖动）。
 */
import { ref } from 'vue'
import BeeIcon from './BeeIcon.vue'

interface Props {
  accept?: string
  multiple?: boolean
  disabled?: boolean
  overlayText?: string
}

const props = withDefaults(defineProps<Props>(), {
  accept: 'image/jpeg,image/png,image/webp',
  multiple: true,
  disabled: false,
  overlayText: '松开即可上传',
})

const emit = defineEmits<{ files: [files: File[]] }>()

const inputRef = ref<HTMLInputElement | null>(null)
const dragging = ref<boolean>(false)
let dragDepth = 0

function openPicker(): void {
  if (props.disabled) return
  inputRef.value?.click()
}

function onInputChange(event: Event): void {
  const input = event.target as HTMLInputElement
  emitFiles(input.files)
  // 允许重复选中同一个文件
  input.value = ''
}

function emitFiles(list: FileList | null): void {
  if (!list || list.length === 0) return
  const files = Array.from(list)
  emit('files', props.multiple ? files : files.slice(0, 1))
}

function onDragEnter(event: DragEvent): void {
  if (props.disabled) return
  event.preventDefault()
  dragDepth += 1
  dragging.value = true
}

function onDragOver(event: DragEvent): void {
  if (props.disabled) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
}

function onDragLeave(): void {
  if (props.disabled) return
  dragDepth = Math.max(dragDepth - 1, 0)
  if (dragDepth === 0) dragging.value = false
}

function onDrop(event: DragEvent): void {
  if (props.disabled) return
  event.preventDefault()
  dragDepth = 0
  dragging.value = false
  emitFiles(event.dataTransfer?.files ?? null)
}
</script>

<template>
  <div
    class="bee-filedrop"
    :class="{ 'is-dragging': dragging, 'is-disabled': disabled }"
    role="button"
    :tabindex="disabled ? -1 : 0"
    :aria-disabled="disabled"
    @click="openPicker"
    @keydown.enter.prevent="openPicker"
    @keydown.space.prevent="openPicker"
    @dragenter="onDragEnter"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
  >
    <slot>
      <div class="bee-filedrop__inner">
        <BeeIcon name="upload" :size="28" />
        <p class="bee-filedrop__title">点击选择照片，或拖拽到这里上传</p>
        <p class="bee-filedrop__hint">支持 JPG / PNG / WebP，单张不超过 20MB</p>
      </div>
    </slot>

    <span v-if="dragging" class="bee-filedrop__overlay">{{ overlayText }}</span>

    <input
      ref="inputRef"
      class="bee-filedrop__input"
      type="file"
      :accept="accept"
      :multiple="multiple"
      :disabled="disabled"
      @change="onInputChange"
    />
  </div>
</template>

<style scoped>
.bee-filedrop {
  position: relative;
  display: block;
  border: 1.5px dashed var(--border-strong);
  border-radius: var(--r-lg);
  background: var(--bg-sunken);
  transition:
    border-color var(--dur-base) var(--ease-out),
    background-color var(--dur-base) var(--ease-out);
  cursor: pointer;
}

.bee-filedrop:hover:not(.is-disabled) {
  border-color: var(--honey-300);
  background: var(--honey-50);
}

.bee-filedrop:focus-visible {
  outline: 2px solid var(--honey-500);
  outline-offset: 2px;
}

.bee-filedrop.is-dragging {
  border-color: var(--honey-500);
  background: var(--honey-50);
}

.bee-filedrop.is-disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.bee-filedrop__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-1);
  padding: var(--sp-10) var(--sp-6);
  text-align: center;
  color: var(--text-tertiary);
}

.bee-filedrop__title {
  margin-top: var(--sp-2);
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-primary);
}

.bee-filedrop__hint {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.bee-filedrop__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-lg);
  background: var(--honey-100);
  color: var(--text-brand);
  font-size: var(--fs-body-lg);
  font-weight: 600;
  pointer-events: none;
}

.bee-filedrop__input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}
</style>
