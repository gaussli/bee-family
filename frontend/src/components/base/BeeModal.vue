<script setup lang="ts">
/**
 * 模态框。
 *
 * 结构：头部（标题 + 关闭）→ 主体（可滚动）→ 底部操作区。
 * 行为：`Esc` 关闭、点遮罩关闭（`closeOnOverlay`）、焦点陷阱、打开时锁滚动。
 * `danger` 变体在头部左侧加 3px 危险竖条，主操作自动降级为 `danger` 按钮。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import BeeButton from './BeeButton.vue'
import BeeIcon from './BeeIcon.vue'
import { useFocusTrap } from '@/composables/useFocusTrap'

interface Props {
  open: boolean
  title?: string
  size?: 'sm' | 'md' | 'lg'
  danger?: boolean
  closeOnOverlay?: boolean
  loading?: boolean
  confirmText?: string
  cancelText?: string
  hideFooter?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  size: 'md',
  danger: false,
  closeOnOverlay: true,
  loading: false,
  confirmText: '确定',
  cancelText: '取消',
  hideFooter: false,
})

const emit = defineEmits<{ 'update:open': [value: boolean]; confirm: []; cancel: [] }>()

const dialogRef = ref<HTMLElement | null>(null)
const isOpen = computed<boolean>(() => props.open)

useFocusTrap(dialogRef, isOpen, '.bee-modal__close')

let previousOverflow = ''

function close(): void {
  emit('update:open', false)
}

function onCancel(): void {
  emit('cancel')
  close()
}

function onConfirm(): void {
  emit('confirm')
}

function onOverlayClick(): void {
  if (props.closeOnOverlay && !props.loading) close()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.open && !props.loading) close()
}

watch(
  () => props.open,
  (value) => {
    if (typeof document === 'undefined') return
    if (value) {
      previousOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      document.addEventListener('keydown', onKeydown)
    } else {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', onKeydown)
    }
  },
)

onBeforeUnmount(() => {
  if (typeof document === 'undefined') return
  document.removeEventListener('keydown', onKeydown)
  if (props.open) {
    document.body.style.overflow = previousOverflow
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="bee-modal">
      <div v-if="open" class="bee-modal" role="presentation" @click.self="onOverlayClick">
        <div
          ref="dialogRef"
          class="bee-modal__dialog"
          :class="[`bee-modal__dialog--${size}`, { 'is-danger': danger }]"
          role="dialog"
          aria-modal="true"
          :aria-label="title || undefined"
          tabindex="-1"
        >
          <span v-if="danger" class="bee-modal__accent" aria-hidden="true" />

          <header class="bee-modal__header">
            <h2 class="bee-modal__title">{{ title }}</h2>
            <button class="bee-modal__close" type="button" aria-label="关闭" @click="close">
              <BeeIcon name="close" :size="18" />
            </button>
          </header>

          <div class="bee-modal__body">
            <slot />
          </div>

          <footer v-if="!hideFooter" class="bee-modal__footer">
            <slot name="footer">
              <BeeButton variant="ghost" :disabled="loading" @click="onCancel">{{ cancelText }}</BeeButton>
              <BeeButton
                :variant="danger ? 'danger' : 'primary'"
                :loading="loading"
                @click="onConfirm"
              >
                {{ confirmText }}
              </BeeButton>
            </slot>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.bee-modal {
  position: fixed;
  inset: 0;
  z-index: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-4);
  background: var(--overlay-scrim);
  backdrop-filter: blur(2px);
}

.bee-modal__dialog {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: calc(100vh - var(--sp-16));
  background: var(--bg-elevated);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.bee-modal__dialog--sm {
  max-width: 400px;
}

.bee-modal__dialog--md {
  max-width: 520px;
}

.bee-modal__dialog--lg {
  max-width: 720px;
}

.bee-modal__dialog:focus {
  outline: none;
}

.bee-modal__accent {
  position: absolute;
  top: 0;
  left: 0;
  width: 3px;
  height: 100%;
  background: var(--color-danger);
}

.bee-modal__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: 20px var(--sp-6) 0;
}

.bee-modal__title {
  font-family: var(--font-display);
  font-size: var(--fs-h3);
  line-height: var(--lh-h3);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-modal__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin: -4px -6px 0 0;
  border-radius: var(--r-md);
  color: var(--text-tertiary);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.bee-modal__close:hover {
  background: var(--warm-100);
  color: var(--text-primary);
}

.bee-modal__body {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-5) var(--sp-6);
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
}

.bee-modal__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--sp-2);
  padding: var(--sp-5) var(--sp-6);
  border-top: 1px solid var(--border-subtle);
}

/* ---------- 过渡 ---------- */
.bee-modal-enter-active,
.bee-modal-leave-active {
  transition: opacity var(--dur-base) var(--ease-out);
}

.bee-modal-enter-active .bee-modal__dialog,
.bee-modal-leave-active .bee-modal__dialog {
  transition:
    opacity var(--dur-slow) var(--ease-out),
    transform var(--dur-slow) var(--ease-out);
}

.bee-modal-enter-from,
.bee-modal-leave-to {
  opacity: 0;
}

.bee-modal-enter-from .bee-modal__dialog,
.bee-modal-leave-to .bee-modal__dialog {
  opacity: 0;
  transform: scale(0.97) translateY(8px);
}

@media (max-width: 767px) {
  .bee-modal {
    align-items: flex-end;
    padding: 0;
  }

  .bee-modal__dialog {
    max-width: none;
    border-radius: var(--r-xl) var(--r-xl) 0 0;
    max-height: 92vh;
  }

  .bee-modal-enter-from .bee-modal__dialog,
  .bee-modal-leave-to .bee-modal__dialog {
    transform: translateY(24px);
  }
}
</style>
