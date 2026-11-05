<script setup lang="ts">
/**
 * 通用卡片。默认使用 `--shadow-sm` 而不加边框（与阴影二选一）；
 * `hoverable` 时上浮 2px 并加深阴影，`clickable` 时整卡可聚焦可点击。
 */
interface Props {
  padding?: 'none' | 'sm' | 'md' | 'lg'
  hoverable?: boolean
  clickable?: boolean
  bordered?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  padding: 'md',
  hoverable: false,
  clickable: false,
  bordered: false,
})

const emit = defineEmits<{ click: [] }>()

function onClick(): void {
  if (props.clickable) emit('click')
}

function onKeydown(event: KeyboardEvent): void {
  if (!props.clickable) return
  if (event.key !== 'Enter' && event.key !== ' ') return
  event.preventDefault()
  emit('click')
}
</script>

<template>
  <div
    class="bee-card"
    :class="[
      `bee-card--pad-${padding}`,
      { 'bee-card--hoverable': hoverable || clickable, 'bee-card--bordered': bordered, 'bee-card--clickable': clickable },
    ]"
    :role="clickable ? 'button' : undefined"
    :tabindex="clickable ? 0 : undefined"
    @click="onClick"
    @keydown="onKeydown"
  >
    <header v-if="$slots.header" class="bee-card__header">
      <slot name="header" />
    </header>
    <div class="bee-card__body">
      <slot />
    </div>
    <footer v-if="$slots.footer" class="bee-card__footer">
      <slot name="footer" />
    </footer>
  </div>
</template>

<style scoped>
.bee-card {
  background: var(--bg-surface);
  border-radius: var(--r-lg);
  border: 1px solid transparent;
  box-shadow: var(--shadow-sm);
  transition:
    transform var(--dur-base) var(--ease-out),
    box-shadow var(--dur-base) var(--ease-out);
}

.bee-card--bordered {
  border-color: var(--border-subtle);
  box-shadow: none;
}

.bee-card--hoverable:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.bee-card--clickable {
  cursor: pointer;
}

.bee-card--clickable:focus-visible {
  outline: 2px solid var(--honey-500);
  outline-offset: 3px;
}

.bee-card--pad-none {
  padding: 0;
}

.bee-card--pad-sm {
  padding: var(--sp-4);
}

.bee-card--pad-md {
  padding: var(--sp-6);
}

.bee-card--pad-lg {
  padding: var(--sp-8);
}

.bee-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  font-size: var(--fs-h4);
  line-height: var(--lh-h4);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-card__header + .bee-card__body {
  margin-top: var(--sp-4);
}

.bee-card__footer {
  margin-top: var(--sp-5);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--border-subtle);
}
</style>
