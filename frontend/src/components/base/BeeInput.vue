<script setup lang="ts">
/**
 * 表单输入。单行 `<input>` 与多行 `<textarea>` 共用一个组件：
 * 传入 `rows > 0` 即切换为 textarea。
 *
 * 支持前置图标、错误态、提示文案与后缀插槽（如「显示密码」「计数」）。
 */
import { computed, onMounted, ref } from 'vue'
import BeeIcon from './BeeIcon.vue'
import type { IconName } from './icon-paths'

interface Props {
  modelValue: string | number | null
  type?: 'text' | 'email' | 'password' | 'number' | 'date' | 'datetime-local'
  label?: string
  placeholder?: string
  required?: boolean
  error?: string
  hint?: string
  disabled?: boolean
  icon?: IconName
  maxlength?: number
  rows?: number
  autofocus?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  type: 'text',
  label: '',
  placeholder: '',
  required: false,
  error: '',
  hint: '',
  disabled: false,
  rows: 0,
  maxlength: undefined,
  autofocus: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: string]; blur: [] }>()

const inputRef = ref<HTMLInputElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

/** 每个实例一个稳定 id，用于 label ↔ 控件关联与 aria-describedby。 */
const uid = `bee-input-${Math.random().toString(36).slice(2, 9)}`

const isTextarea = computed<boolean>(() => props.rows > 0)
const hasError = computed<boolean>(() => props.error.length > 0)
const describedBy = computed<string | undefined>(() => {
  if (hasError.value) return undefined
  return props.hint ? `${uid}-hint` : undefined
})

const currentValue = computed<string>(() =>
  props.modelValue === null || props.modelValue === undefined ? '' : String(props.modelValue),
)

function onInput(event: Event): void {
  const target = event.target as HTMLInputElement | HTMLTextAreaElement
  emit('update:modelValue', target.value)
}

function onBlur(): void {
  emit('blur')
}

onMounted(() => {
  if (!props.autofocus) return
  const el = isTextarea.value ? textareaRef.value : inputRef.value
  el?.focus()
})
</script>

<template>
  <div class="bee-field" :class="{ 'is-error': hasError, 'is-disabled': disabled }">
    <label v-if="label" class="bee-field__label" :for="uid">
      {{ label }}
      <span v-if="required" class="bee-field__req" aria-hidden="true">*</span>
    </label>

    <div class="bee-field__control" :class="{ 'has-icon': !!icon }">
      <BeeIcon v-if="icon" class="bee-field__icon" :name="icon" :size="16" />

      <textarea
        v-if="isTextarea"
        :id="uid"
        ref="textareaRef"
        class="bee-field__input bee-field__input--area"
        :value="currentValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :rows="rows"
        :maxlength="maxlength"
        :required="required"
        :aria-invalid="hasError ? 'true' : undefined"
        :aria-describedby="describedBy"
        @input="onInput"
        @blur="onBlur"
      />

      <input
        v-else
        :id="uid"
        ref="inputRef"
        class="bee-field__input"
        :type="type"
        :value="currentValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :maxlength="maxlength"
        :required="required"
        :aria-invalid="hasError ? 'true' : undefined"
        :aria-describedby="describedBy"
        @input="onInput"
        @blur="onBlur"
      />

      <span v-if="$slots.suffix" class="bee-field__suffix">
        <slot name="suffix" />
      </span>
    </div>

    <p v-if="hasError" class="bee-field__error">{{ error }}</p>
    <p v-else-if="hint" :id="`${uid}-hint`" class="bee-field__hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.bee-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.bee-field__label {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.bee-field__req {
  color: var(--color-danger);
}

.bee-field__control {
  position: relative;
  display: flex;
  align-items: center;
}

.bee-field__icon {
  position: absolute;
  left: var(--sp-3);
  color: var(--text-tertiary);
  pointer-events: none;
}

.bee-field__input {
  width: 100%;
  height: 40px;
  padding: 9px var(--sp-3);
  background: var(--bg-sunken);
  border: 1px solid var(--border-default);
  border-radius: var(--r-md);
  color: var(--text-primary);
  font-family: var(--font-sans);
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  transition:
    border-color var(--dur-fast) var(--ease-out),
    background-color var(--dur-fast) var(--ease-out),
    box-shadow var(--dur-fast) var(--ease-out);
}

.bee-field__input--area {
  height: auto;
  min-height: 88px;
  padding: 9px var(--sp-3);
  line-height: 1.55;
  resize: vertical;
}

.has-icon .bee-field__input {
  padding-left: 36px;
}

.bee-field__input::placeholder {
  color: var(--text-tertiary);
}

.bee-field__input:hover:not(:disabled) {
  border-color: var(--border-strong);
}

.bee-field__input:focus {
  outline: none;
  background: var(--bg-elevated);
  border-color: var(--border-focus);
  box-shadow: var(--shadow-focus);
}

.is-error .bee-field__input {
  border-color: var(--color-danger);
}

.is-error .bee-field__input:focus {
  box-shadow: 0 0 0 3px var(--color-danger-bg);
}

.bee-field__input:disabled {
  background: var(--warm-100);
  color: var(--text-disabled);
  cursor: not-allowed;
}

.bee-field__suffix {
  position: absolute;
  right: var(--sp-2);
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  color: var(--text-tertiary);
}

.bee-field__error,
.bee-field__hint {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
}

.bee-field__error {
  color: var(--color-danger);
}

.bee-field__hint {
  color: var(--text-tertiary);
}
</style>
