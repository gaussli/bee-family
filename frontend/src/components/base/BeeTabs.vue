<script setup lang="ts">
/**
 * 标签页。三种变体：下划线（默认，页面级）、胶囊（筛选条）、分段控件（视图切换）。
 */
export interface BeeTabItem {
  key: string
  label: string
  count?: number
}

interface Props {
  modelValue: string
  items: BeeTabItem[]
  variant?: 'underline' | 'pill' | 'segment'
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'underline',
})

const emit = defineEmits<{ 'update:modelValue': [key: string] }>()

function select(item: BeeTabItem): void {
  if (item.key === props.modelValue) return
  emit('update:modelValue', item.key)
}
</script>

<template>
  <div class="bee-tabs" :class="`bee-tabs--${variant}`" role="tablist">
    <button
      v-for="item in items"
      :key="item.key"
      class="bee-tabs__item"
      :class="{ 'is-active': item.key === modelValue }"
      type="button"
      role="tab"
      :aria-selected="item.key === modelValue"
      @click="select(item)"
    >
      <span class="bee-tabs__label">{{ item.label }}</span>
      <span v-if="item.count !== undefined" class="bee-tabs__count">{{ item.count }}</span>
    </button>
  </div>
</template>

<style scoped>
.bee-tabs {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.bee-tabs::-webkit-scrollbar {
  display: none;
}

.bee-tabs__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: none;
  font-family: var(--font-sans);
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-secondary);
  transition:
    color var(--dur-fast) var(--ease-out),
    background-color var(--dur-fast) var(--ease-out);
}

.bee-tabs__item:focus-visible {
  outline: 2px solid var(--honey-500);
  outline-offset: 2px;
}

.bee-tabs__count {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

/* ---------- underline ---------- */
.bee-tabs--underline {
  gap: var(--sp-5);
  border-bottom: 1px solid var(--border-subtle);
}

.bee-tabs--underline .bee-tabs__item {
  height: 40px;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.bee-tabs--underline .bee-tabs__item:hover {
  color: var(--text-primary);
}

.bee-tabs--underline .bee-tabs__item.is-active {
  color: var(--text-brand);
  font-weight: 600;
  border-bottom-color: var(--honey-500);
}

/* ---------- pill ---------- */
.bee-tabs--pill {
  gap: var(--sp-2);
}

.bee-tabs--pill .bee-tabs__item {
  height: 32px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-full);
  background: var(--warm-100);
}

.bee-tabs--pill .bee-tabs__item:hover {
  background: var(--warm-200);
  color: var(--text-primary);
}

.bee-tabs--pill .bee-tabs__item.is-active {
  background: var(--honey-100);
  color: var(--text-brand);
}

/* ---------- segment ---------- */
.bee-tabs--segment {
  gap: 0;
  padding: 3px;
  background: var(--bg-sunken);
  border-radius: var(--r-md);
}

.bee-tabs--segment .bee-tabs__item {
  height: 30px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-sm);
}

.bee-tabs--segment .bee-tabs__item.is-active {
  background: var(--bg-elevated);
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}
</style>
