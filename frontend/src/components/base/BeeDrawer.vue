<script setup lang="ts">
/**
 * 抽屉。移动端侧栏、筛选面板复用。
 *
 * `side="right"` 时从右侧滑入（宽 `width`）；`side="bottom"` 时从底部滑入（高 80vh）。
 */
import { computed, onBeforeUnmount, ref, watch, type CSSProperties } from 'vue'
import BeeButton from './BeeButton.vue'
import BeeIcon from './BeeIcon.vue'
import { useFocusTrap } from '@/composables/useFocusTrap'

interface Props {
  open: boolean
  title?: string
  width?: number
  side?: 'right' | 'bottom'
  loading?: boolean
  confirmText?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  width: 400,
  side: 'right',
  loading: false,
  confirmText: '确定',
})

const emit = defineEmits<{ 'update:open': [value: boolean]; confirm: [] }>()

const panelRef = ref<HTMLElement | null>(null)
const isOpen = computed<boolean>(() => props.open)

useFocusTrap(panelRef, isOpen, '.bee-drawer__close')

const panelStyle = computed<CSSProperties>(() =>
  props.side === 'right' ? { width: `${props.width}px` } : { height: '80vh' },
)

function close(): void {
  emit('update:open', false)
}

function onConfirm(): void {
  emit('confirm')
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.open && !props.loading) close()
}

let previousOverflow = ''

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
  if (props.open) document.body.style.overflow = previousOverflow
})
</script>

<template>
  <Teleport to="body">
    <Transition :name="`bee-drawer-${side}`">
      <div v-if="open" class="bee-drawer" role="presentation" @click.self="close">
        <div
          ref="panelRef"
          class="bee-drawer__panel"
          :class="`bee-drawer__panel--${side}`"
          :style="panelStyle"
          role="dialog"
          aria-modal="true"
          :aria-label="title || undefined"
          tabindex="-1"
        >
          <header class="bee-drawer__header">
            <h2 class="bee-drawer__title">{{ title }}</h2>
            <button class="bee-drawer__close" type="button" aria-label="关闭" @click="close">
              <BeeIcon name="close" :size="18" />
            </button>
          </header>

          <div class="bee-drawer__body">
            <slot />
          </div>

          <footer v-if="$slots.footer" class="bee-drawer__footer">
            <slot name="footer">
              <BeeButton variant="ghost" @click="close">取消</BeeButton>
              <BeeButton :loading="loading" @click="onConfirm">{{ confirmText }}</BeeButton>
            </slot>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.bee-drawer {
  position: fixed;
  inset: 0;
  z-index: 910;
  display: flex;
  background: var(--overlay-scrim);
}

.bee-drawer__panel {
  display: flex;
  flex-direction: column;
  background: var(--bg-elevated);
  box-shadow: var(--shadow-lg);
}

.bee-drawer__panel--right {
  margin-left: auto;
  height: 100%;
  border-radius: var(--r-xl) 0 0 var(--r-xl);
}

.bee-drawer__panel--bottom {
  margin-top: auto;
  width: 100%;
  border-radius: var(--r-xl) var(--r-xl) 0 0;
}

.bee-drawer__panel:focus {
  outline: none;
}

.bee-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-5) var(--sp-6) var(--sp-3);
}

.bee-drawer__title {
  font-family: var(--font-display);
  font-size: var(--fs-h3);
  line-height: var(--lh-h3);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-drawer__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--r-md);
  color: var(--text-tertiary);
}

.bee-drawer__close:hover {
  background: var(--warm-100);
  color: var(--text-primary);
}

.bee-drawer__body {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-2) var(--sp-6) var(--sp-6);
}

.bee-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
  padding: var(--sp-4) var(--sp-6);
  border-top: 1px solid var(--border-subtle);
}

.bee-drawer-right-enter-active,
.bee-drawer-right-leave-active,
.bee-drawer-bottom-enter-active,
.bee-drawer-bottom-leave-active {
  transition: opacity var(--dur-base) var(--ease-out);
}

.bee-drawer-right-enter-active .bee-drawer__panel--right,
.bee-drawer-right-leave-active .bee-drawer__panel--right,
.bee-drawer-bottom-enter-active .bee-drawer__panel--bottom,
.bee-drawer-bottom-leave-active .bee-drawer__panel--bottom {
  transition: transform var(--dur-slow) var(--ease-out);
}

.bee-drawer-right-enter-from,
.bee-drawer-right-leave-to,
.bee-drawer-bottom-enter-from,
.bee-drawer-bottom-leave-to {
  opacity: 0;
}

.bee-drawer-right-enter-from .bee-drawer__panel--right,
.bee-drawer-right-leave-to .bee-drawer__panel--right {
  transform: translateX(100%);
}

.bee-drawer-bottom-enter-from .bee-drawer__panel--bottom,
.bee-drawer-bottom-leave-to .bee-drawer__panel--bottom {
  transform: translateY(100%);
}
</style>
