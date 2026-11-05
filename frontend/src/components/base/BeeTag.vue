<script setup lang="ts">
/**
 * 标签 Chip。四种视觉变体：中性 / 蜜金高亮 / 描边筛选器 / 珊瑚里程碑。
 * 可关闭变体在右侧渲染 `×`，可点击变体输出 `click` 事件。
 */
import BeeIcon from './BeeIcon.vue'
import type { IconName } from './icon-paths'

interface Props {
  variant?: 'default' | 'honey' | 'outline' | 'coral'
  size?: 'sm' | 'md'
  closable?: boolean
  clickable?: boolean
  icon?: IconName
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
  size: 'md',
  closable: false,
  clickable: false,
})

const emit = defineEmits<{ close: []; click: [] }>()

function onClose(event: MouseEvent): void {
  event.stopPropagation()
  emit('close')
}

function onClick(): void {
  if (props.clickable) emit('click')
}
</script>

<template>
  <component
    :is="clickable ? 'button' : 'span'"
    class="bee-tag"
    :class="[`bee-tag--${variant}`, `bee-tag--${size}`, { 'bee-tag--clickable': clickable }]"
    :type="clickable ? 'button' : undefined"
    @click="onClick"
  >
    <BeeIcon v-if="icon" :name="icon" :size="12" />
    <span class="bee-tag__text"><slot /></span>
    <button
      v-if="closable"
      class="bee-tag__close"
      type="button"
      aria-label="移除"
      @click="onClose"
    >
      <BeeIcon name="close" :size="10" :stroke-width="2" />
    </button>
  </component>
</template>

<style scoped>
.bee-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  font-family: var(--font-sans);
  font-size: var(--fs-caption);
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  max-width: 100%;
}

.bee-tag--md {
  height: 24px;
  padding: 0 var(--sp-2);
}

.bee-tag--sm {
  height: 20px;
  padding: 0 var(--sp-1) 0 6px;
  font-size: 11px;
}

.bee-tag__text {
  overflow: hidden;
  text-overflow: ellipsis;
}

.bee-tag--default {
  background: var(--warm-100);
  color: var(--text-secondary);
}

.bee-tag--honey {
  background: var(--honey-100);
  color: var(--text-brand);
}

.bee-tag--outline {
  background: transparent;
  border-color: var(--border-default);
  color: var(--text-secondary);
}

.bee-tag--coral {
  background: var(--accent-coral-bg);
  color: var(--accent-coral);
}

.bee-tag--clickable {
  cursor: pointer;
  transition:
    background-color var(--dur-fast) var(--ease-out),
    border-color var(--dur-fast) var(--ease-out);
}

.bee-tag--clickable:hover {
  filter: brightness(0.97);
}

.bee-tag--outline.bee-tag--clickable:hover {
  border-color: var(--honey-300);
  color: var(--text-primary);
}

.bee-tag--clickable:focus-visible {
  outline: 2px solid var(--honey-500);
  outline-offset: 2px;
}

.bee-tag__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  margin-right: -2px;
  border-radius: var(--r-full);
  color: var(--text-tertiary);
  transition: color var(--dur-fast) var(--ease-out);
}

.bee-tag__close:hover {
  color: var(--color-danger);
}
</style>
