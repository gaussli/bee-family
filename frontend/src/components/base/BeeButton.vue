<script setup lang="ts">
/**
 * 按钮。四种变体 × 三种尺寸，支持图标槽、图标按钮、加载态与全宽。
 *
 * 加载态保留文字、用旋转环替换左侧图标，并阻断点击（`pointer-events: none`
 * 同时作用在原生 `pointer-events` 与 `disabled` 上，确保不会重复提交）。
 */
interface Props {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
  disabled?: boolean
  block?: boolean
  iconOnly?: boolean
  type?: 'button' | 'submit'
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'primary',
  size: 'md',
  loading: false,
  disabled: false,
  block: false,
  iconOnly: false,
  type: 'button',
})

const emit = defineEmits<{ click: [event: MouseEvent] }>()

function onClick(event: MouseEvent): void {
  if (props.loading || props.disabled) {
    event.preventDefault()
    event.stopPropagation()
    return
  }
  emit('click', event)
}
</script>

<template>
  <button
    class="bee-btn"
    :class="[
      `bee-btn--${variant}`,
      `bee-btn--${size}`,
      {
        'bee-btn--block': block,
        'bee-btn--icon': iconOnly,
        'is-loading': loading,
      },
    ]"
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading ? 'true' : undefined"
    @click="onClick"
  >
    <span v-if="loading" class="bee-btn__spinner" aria-hidden="true" />
    <template v-else-if="iconOnly">
      <span class="bee-btn__icon"><slot /></span>
    </template>
    <template v-else>
      <span v-if="$slots.icon" class="bee-btn__icon" aria-hidden="true">
        <slot name="icon" />
      </span>
      <span class="bee-btn__label"><slot /></span>
    </template>
  </button>
</template>

<style scoped>
.bee-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  border: 1px solid transparent;
  border-radius: var(--r-md);
  font-family: var(--font-sans);
  font-weight: 500;
  white-space: nowrap;
  user-select: none;
  transition:
    background-color var(--dur-base) var(--ease-out),
    border-color var(--dur-base) var(--ease-out),
    color var(--dur-base) var(--ease-out),
    box-shadow var(--dur-base) var(--ease-out);
}

.bee-btn:focus-visible {
  outline: none;
  box-shadow: var(--shadow-focus);
}

.bee-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.bee-btn.is-loading {
  pointer-events: none;
  opacity: 0.75;
}

/* ---------- 尺寸 ---------- */
.bee-btn--sm {
  height: 32px;
  padding: 0 var(--sp-3);
  font-size: 13px;
  border-radius: var(--r-sm);
}

.bee-btn--md {
  height: 40px;
  padding: 0 var(--sp-4);
  font-size: var(--fs-body);
}

.bee-btn--lg {
  height: 48px;
  padding: 0 var(--sp-6);
  font-size: var(--fs-body-lg);
}

.bee-btn--sm .bee-btn__icon :deep(svg) {
  width: 14px;
  height: 14px;
}

.bee-btn--md .bee-btn__icon :deep(svg) {
  width: 16px;
  height: 16px;
}

.bee-btn--lg .bee-btn__icon :deep(svg) {
  width: 18px;
  height: 18px;
}

/* ---------- 图标按钮 ---------- */
.bee-btn--icon.bee-btn--sm {
  width: 32px;
  padding: 0;
}

.bee-btn--icon.bee-btn--md {
  width: 36px;
  padding: 0;
}

.bee-btn--icon.bee-btn--lg {
  width: 44px;
  padding: 0;
}

/* ---------- 变体 ---------- */
.bee-btn--primary {
  background: var(--honey-500);
  color: var(--text-inverse);
}

.bee-btn--primary:hover:not(:disabled) {
  background: var(--honey-400);
}

.bee-btn--primary:active:not(:disabled) {
  background: var(--honey-600);
}

.bee-btn--secondary {
  background: var(--bg-surface);
  color: var(--text-primary);
  border-color: var(--border-strong);
}

.bee-btn--secondary:hover:not(:disabled) {
  background: var(--honey-50);
  border-color: var(--honey-300);
}

.bee-btn--secondary:active:not(:disabled) {
  background: var(--honey-100);
}

.bee-btn--ghost {
  background: transparent;
  color: var(--text-secondary);
}

.bee-btn--ghost:hover:not(:disabled) {
  background: var(--warm-100);
  color: var(--text-primary);
}

.bee-btn--ghost:active:not(:disabled) {
  background: var(--warm-200);
}

.bee-btn--danger {
  background: var(--color-danger);
  color: var(--text-inverse);
}

.bee-btn--danger:hover:not(:disabled) {
  background: var(--accent-coral);
}

.bee-btn--danger:active:not(:disabled) {
  background: var(--honey-800);
}

.bee-btn--block {
  display: flex;
  width: 100%;
}

.bee-btn__icon,
.bee-btn__label {
  display: inline-flex;
  align-items: center;
}

.bee-btn__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: var(--r-full);
  animation: bee-btn-spin 900ms linear infinite;
}

@keyframes bee-btn-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 767px) {
  .bee-btn--md {
    height: 40px;
  }
}
</style>
