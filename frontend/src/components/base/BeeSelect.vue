<script setup lang="ts">
/**
 * 下拉选择。自建面板而非原生 `<select>`，以支持搜索、清空与统一视觉。
 *
 * 交互：点击触发器展开；点击外部 / `Esc` 收起；`↑`/`↓` 移动高亮项，`Enter` 选中。
 * `searchable` 时面板顶部渲染搜索框，并把关键词通过 `search` 事件抛出（由父级做前后端过滤均可）。
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import BeeIcon from './BeeIcon.vue'

export interface BeeSelectOption {
  label: string
  value: string | number
  disabled?: boolean
}

interface Props {
  modelValue: string | number | null
  options: BeeSelectOption[]
  label?: string
  placeholder?: string
  error?: string
  disabled?: boolean
  searchable?: boolean
  clearable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  label: '',
  placeholder: '请选择',
  error: '',
  disabled: false,
  searchable: false,
  clearable: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string | number | null]
  search: [keyword: string]
}>()

const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLButtonElement | null>(null)
const searchRef = ref<HTMLInputElement | null>(null)
const open = ref<boolean>(false)
const keyword = ref<string>('')
const activeIndex = ref<number>(-1)

const uid = `bee-select-${Math.random().toString(36).slice(2, 9)}`

const selectedOption = computed<BeeSelectOption | null>(
  () => props.options.find((item) => item.value === props.modelValue) ?? null,
)

const filteredOptions = computed<BeeSelectOption[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return props.options
  return props.options.filter((item) => item.label.toLowerCase().includes(kw))
})

const hasValue = computed<boolean>(() => props.modelValue !== null && props.modelValue !== undefined)

function toggle(): void {
  if (props.disabled) return
  if (open.value) {
    close()
    return
  }
  open.value = true
  activeIndex.value = props.options.findIndex((item) => item.value === props.modelValue)
  if (props.searchable) {
    void nextTick(() => searchRef.value?.focus())
  }
}

function close(): void {
  open.value = false
  keyword.value = ''
  activeIndex.value = -1
}

function select(option: BeeSelectOption): void {
  if (option.disabled) return
  emit('update:modelValue', option.value)
  close()
  triggerRef.value?.focus()
}

function clear(event: MouseEvent): void {
  event.stopPropagation()
  emit('update:modelValue', null)
}

function onSearchInput(event: Event): void {
  keyword.value = (event.target as HTMLInputElement).value
  emit('search', keyword.value)
  activeIndex.value = filteredOptions.value.length > 0 ? 0 : -1
}

function onKeydown(event: KeyboardEvent): void {
  if (props.disabled) return
  if (event.key === 'Escape') {
    close()
    return
  }
  if (!open.value) {
    if (event.key === 'Enter' || event.key === 'ArrowDown' || event.key === ' ') {
      event.preventDefault()
      toggle()
    }
    return
  }
  const list = filteredOptions.value
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    activeIndex.value = list.length === 0 ? -1 : (activeIndex.value + 1) % list.length
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    activeIndex.value = list.length === 0 ? -1 : (activeIndex.value - 1 + list.length) % list.length
  } else if (event.key === 'Enter') {
    event.preventDefault()
    const target = list[activeIndex.value]
    if (target) select(target)
  }
}

function onDocumentPointerDown(event: PointerEvent): void {
  if (!open.value) return
  const root = rootRef.value
  if (root && !root.contains(event.target as Node)) close()
}

watch(open, (value) => {
  if (value) {
    document.addEventListener('pointerdown', onDocumentPointerDown)
  } else {
    document.removeEventListener('pointerdown', onDocumentPointerDown)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
})
</script>

<template>
  <div ref="rootRef" class="bee-select" :class="{ 'is-open': open, 'is-disabled': disabled, 'is-error': !!error }">
    <label v-if="label" class="bee-select__label" :for="uid">{{ label }}</label>

    <div class="bee-select__control">
      <button
        :id="uid"
        ref="triggerRef"
        class="bee-select__trigger"
        type="button"
        role="combobox"
        :aria-expanded="open"
        aria-haspopup="listbox"
        :disabled="disabled"
        @click="toggle"
        @keydown="onKeydown"
      >
        <span class="bee-select__value" :class="{ 'is-placeholder': !selectedOption }">
          {{ selectedOption ? selectedOption.label : placeholder }}
        </span>
        <BeeIcon class="bee-select__chevron" name="chevron-down" :size="16" />
      </button>

      <button
        v-if="clearable && hasValue"
        class="bee-select__clear"
        type="button"
        aria-label="清空选择"
        @click="clear"
      >
        <BeeIcon name="close" :size="12" />
      </button>
    </div>

    <div v-if="open" class="bee-select__panel" role="listbox">
      <div v-if="searchable" class="bee-select__search">
        <BeeIcon name="search" :size="14" />
        <input
          ref="searchRef"
          class="bee-select__search-input"
          type="text"
          placeholder="搜索"
          :value="keyword"
          @input="onSearchInput"
        />
      </div>

      <ul class="bee-select__list">
        <li
          v-for="(option, index) in filteredOptions"
          :key="option.value"
          class="bee-select__option"
          :class="{
            'is-selected': option.value === modelValue,
            'is-active': index === activeIndex,
            'is-disabled': option.disabled,
          }"
          role="option"
          :aria-selected="option.value === modelValue"
          @click="select(option)"
          @mouseenter="activeIndex = index"
        >
          <span class="bee-select__option-label">{{ option.label }}</span>
          <BeeIcon v-if="option.value === modelValue" name="check" :size="14" />
        </li>
        <li v-if="filteredOptions.length === 0" class="bee-select__empty">没有匹配项</li>
      </ul>
    </div>

    <p v-if="error" class="bee-select__error">{{ error }}</p>
  </div>
</template>

<style scoped>
.bee-select {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.bee-select__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.bee-select__control {
  position: relative;
  display: flex;
  align-items: center;
}

.bee-select__trigger {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;
  height: 40px;
  padding: 0 40px 0 var(--sp-3);
  background: var(--bg-sunken);
  border: 1px solid var(--border-default);
  border-radius: var(--r-md);
  color: var(--text-primary);
  font-family: var(--font-sans);
  font-size: var(--fs-body);
  text-align: left;
  transition:
    border-color var(--dur-fast) var(--ease-out),
    background-color var(--dur-fast) var(--ease-out),
    box-shadow var(--dur-fast) var(--ease-out);
}

.bee-select__trigger:hover:not(:disabled) {
  border-color: var(--border-strong);
}

.bee-select__trigger:focus-visible {
  outline: none;
  background: var(--bg-elevated);
  border-color: var(--border-focus);
  box-shadow: var(--shadow-focus);
}

.bee-select.is-error .bee-select__trigger {
  border-color: var(--color-danger);
}

.bee-select__trigger:disabled {
  background: var(--warm-100);
  color: var(--text-disabled);
  cursor: not-allowed;
}

.bee-select__value {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-select__value.is-placeholder {
  color: var(--text-tertiary);
}

.bee-select__chevron {
  position: absolute;
  right: var(--sp-3);
  color: var(--text-tertiary);
  transition: transform var(--dur-base) var(--ease-out);
  pointer-events: none;
}

.bee-select.is-open .bee-select__chevron {
  transform: rotate(180deg);
}

.bee-select__clear {
  position: absolute;
  right: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: var(--r-full);
  color: var(--text-tertiary);
}

.bee-select__clear:hover {
  background: var(--warm-200);
  color: var(--text-primary);
}

.bee-select__panel {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 40;
  background: var(--bg-elevated);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-md);
  padding: var(--sp-1);
  max-height: 280px;
  overflow-y: auto;
}

.bee-select__search {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 0 var(--sp-2);
  height: 34px;
  margin-bottom: var(--sp-1);
  border-radius: var(--r-sm);
  background: var(--bg-sunken);
  color: var(--text-tertiary);
}

.bee-select__search-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  background: transparent;
  font-family: var(--font-sans);
  font-size: var(--fs-body);
  color: var(--text-primary);
  outline: none;
}

.bee-select__list {
  display: flex;
  flex-direction: column;
}

.bee-select__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  height: 36px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-sm);
  font-size: var(--fs-body);
  color: var(--text-primary);
  cursor: pointer;
}

.bee-select__option.is-active {
  background: var(--honey-50);
}

.bee-select__option.is-selected {
  background: var(--honey-100);
  color: var(--text-brand);
}

.bee-select__option.is-disabled {
  color: var(--text-disabled);
  cursor: not-allowed;
}

.bee-select__option-label {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-select__empty {
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-body);
  color: var(--text-tertiary);
}

.bee-select__error {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}
</style>
