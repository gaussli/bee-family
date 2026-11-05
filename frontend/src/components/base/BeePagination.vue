<script setup lang="ts">
/**
 * 分页器。`page` 为 **0 基**页码（与后端 `PageResponse.page` 一致），
 * 组件内部换算成 1 基展示，避免调用方到处 `+1 / -1`。
 */
import { computed } from 'vue'
import BeeIcon from './BeeIcon.vue'

interface Props {
  page: number
  totalPages: number
  maxButtons?: number
}

const props = withDefaults(defineProps<Props>(), {
  maxButtons: 7,
})

const emit = defineEmits<{ 'update:page': [page: number] }>()

/** 页码令牌：具体页码或省略号占位。 */
type PageToken = number | 'gap'

const tokens = computed<PageToken[]>(() => {
  const total = props.totalPages
  const current = props.page
  if (total <= props.maxButtons) {
    return Array.from({ length: total }, (_, i) => i)
  }

  // 除首尾两个固定页外，中间窗口大小
  const windowSize = Math.max(props.maxButtons - 2, 3)
  const half = Math.floor(windowSize / 2)

  let start = current - half
  let end = start + windowSize - 1

  if (start < 1) {
    start = 1
    end = windowSize
  }
  if (end > total - 2) {
    end = total - 2
    start = end - windowSize + 1
  }

  const result: PageToken[] = [0]
  if (start > 1) result.push('gap')
  for (let i = start; i <= end; i += 1) result.push(i)
  if (end < total - 2) result.push('gap')
  result.push(total - 1)
  return result
})

function go(page: number): void {
  if (page < 0 || page >= props.totalPages || page === props.page) return
  emit('update:page', page)
}
</script>

<template>
  <nav v-if="totalPages > 1" class="bee-pagination" aria-label="分页">
    <button
      class="bee-pagination__btn bee-pagination__btn--arrow"
      type="button"
      :disabled="page === 0"
      aria-label="上一页"
      @click="go(page - 1)"
    >
      <BeeIcon name="chevron-left" :size="16" />
    </button>

    <template v-for="(token, index) in tokens" :key="`${token}-${index}`">
      <span v-if="token === 'gap'" class="bee-pagination__gap" aria-hidden="true">…</span>
      <button
        v-else
        class="bee-pagination__btn"
        :class="{ 'is-active': token === page }"
        type="button"
        :aria-current="token === page ? 'page' : undefined"
        @click="go(token)"
      >
        {{ token + 1 }}
      </button>
    </template>

    <button
      class="bee-pagination__btn bee-pagination__btn--arrow"
      type="button"
      :disabled="page >= totalPages - 1"
      aria-label="下一页"
      @click="go(page + 1)"
    >
      <BeeIcon name="chevron-right" :size="16" />
    </button>
  </nav>
</template>

<style scoped>
.bee-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-1);
  padding: var(--sp-6) 0;
}

.bee-pagination__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 var(--sp-1);
  border-radius: var(--r-sm);
  font-size: var(--fs-body);
  font-variant-numeric: tabular-nums;
  color: var(--text-secondary);
  transition:
    background-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.bee-pagination__btn:hover:not(:disabled):not(.is-active) {
  background: var(--warm-100);
  color: var(--text-primary);
}

.bee-pagination__btn.is-active {
  background: var(--honey-500);
  color: var(--text-inverse);
  font-weight: 600;
}

.bee-pagination__btn:disabled {
  color: var(--text-disabled);
  cursor: not-allowed;
}

.bee-pagination__gap {
  padding: 0 4px;
  color: var(--text-tertiary);
  font-size: var(--fs-caption);
}
</style>
