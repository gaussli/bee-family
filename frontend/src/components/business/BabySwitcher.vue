<script setup lang="ts">
/**
 * 宝宝切换器（02 文档 §5.6）。
 *
 * <p>胶囊容器（`--bg-sunken`，padding 4）内放若干选项，选中项白底 + 阴影。
 * 每个选项带 20px 六边形头像。`allowAdd` 时末尾追加「+ 新增」入口。</p>
 */
import BeeAvatar from '@/components/base/BeeAvatar.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import { fileUrl } from '@/utils/fileUrl'
import type { Baby } from '@/types/models'

interface Props {
  babies: Baby[]
  modelValue: number
  allowAdd?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  allowAdd: false,
})

const emit = defineEmits<{ 'update:modelValue': [id: number]; add: [] }>()

function select(id: number): void {
  if (id === props.modelValue) return
  emit('update:modelValue', id)
}

function avatarOf(baby: Baby): string | null {
  return baby.avatarUrl ? fileUrl(baby.avatarUrl, 'thumb') : null
}
</script>

<template>
  <div class="bee-baby-switcher" role="tablist" aria-label="选择宝宝">
    <button
      v-for="baby in babies"
      :key="baby.id"
      class="bee-baby-switcher__item"
      :class="{ 'is-active': baby.id === modelValue }"
      type="button"
      role="tab"
      :aria-selected="baby.id === modelValue"
      @click="select(baby.id)"
    >
      <BeeAvatar :src="avatarOf(baby)" :name="baby.name" shape="hex" :size="20" />
      <span class="bee-baby-switcher__label">{{ baby.name }}</span>
    </button>

    <button v-if="allowAdd" class="bee-baby-switcher__add" type="button" @click="emit('add')">
      <BeeIcon name="plus" :size="14" />
      新增
    </button>
  </div>
</template>

<style scoped>
.bee-baby-switcher {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 4px;
  border-radius: var(--r-full);
  background: var(--bg-sunken);
  overflow-x: auto;
  max-width: 100%;
  scrollbar-width: none;
}

.bee-baby-switcher::-webkit-scrollbar {
  display: none;
}

.bee-baby-switcher__item {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  height: 32px;
  padding: 0 14px;
  border-radius: var(--r-full);
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-secondary);
  white-space: nowrap;
  transition:
    background-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out),
    box-shadow var(--dur-fast) var(--ease-out);
}

.bee-baby-switcher__item:hover {
  color: var(--text-primary);
}

.bee-baby-switcher__item.is-active {
  background: var(--bg-elevated);
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}

.bee-baby-switcher__add {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-full);
  font-size: var(--fs-body);
  color: var(--text-tertiary);
  white-space: nowrap;
}

.bee-baby-switcher__add:hover {
  color: var(--text-brand);
  background: var(--honey-50);
}
</style>
