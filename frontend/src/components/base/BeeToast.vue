<script setup lang="ts">
/**
 * 单条 Toast。左侧 3px 语义色竖条 + 语义图标 + 文字 + 可选动作按钮。
 */
import BeeIcon from './BeeIcon.vue'
import type { IconName } from './icon-paths'
import type { ToastItem } from '@/stores/ui-toast'

interface Props {
  toast: ToastItem
}

const props = defineProps<Props>()

const emit = defineEmits<{ dismiss: [id: number] }>()

const ICONS: Record<ToastItem['type'], IconName> = {
  success: 'check',
  warning: 'info',
  danger: 'info',
  info: 'info',
}

function onAction(): void {
  props.toast.action?.handler()
  emit('dismiss', props.toast.id)
}
</script>

<template>
  <div class="bee-toast" :class="`bee-toast--${toast.type}`" role="status" aria-live="polite">
    <span class="bee-toast__accent" aria-hidden="true" />
    <BeeIcon class="bee-toast__icon" :name="ICONS[toast.type]" :size="16" />
    <p class="bee-toast__message">{{ toast.message }}</p>
    <button v-if="toast.action" class="bee-toast__action" type="button" @click="onAction">
      {{ toast.action.label }}
    </button>
    <button class="bee-toast__close" type="button" aria-label="关闭提示" @click="emit('dismiss', toast.id)">
      <BeeIcon name="close" :size="14" />
    </button>
  </div>
</template>

<style scoped>
.bee-toast {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-height: 48px;
  max-width: 360px;
  padding: var(--sp-3) var(--sp-4) var(--sp-3) var(--sp-5);
  background: var(--bg-elevated);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.bee-toast__accent {
  position: absolute;
  left: 0;
  top: 0;
  width: 3px;
  height: 100%;
}

.bee-toast--success .bee-toast__accent {
  background: var(--color-success);
}

.bee-toast--warning .bee-toast__accent {
  background: var(--color-warning);
}

.bee-toast--danger .bee-toast__accent {
  background: var(--color-danger);
}

.bee-toast--info .bee-toast__accent {
  background: var(--color-info);
}

.bee-toast__icon {
  flex: none;
}

.bee-toast--success .bee-toast__icon {
  color: var(--color-success);
}

.bee-toast--warning .bee-toast__icon {
  color: var(--color-warning);
}

.bee-toast--danger .bee-toast__icon {
  color: var(--color-danger);
}

.bee-toast--info .bee-toast__icon {
  color: var(--color-info);
}

.bee-toast__message {
  flex: 1;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  font-weight: 500;
  color: var(--text-primary);
  word-break: break-word;
}

.bee-toast__action {
  flex: none;
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-brand);
  padding: 0 var(--sp-1);
  border-radius: var(--r-sm);
}

.bee-toast__action:hover {
  color: var(--honey-600);
}

.bee-toast__close {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--r-sm);
  color: var(--text-tertiary);
}

.bee-toast__close:hover {
  background: var(--warm-100);
  color: var(--text-primary);
}
</style>
