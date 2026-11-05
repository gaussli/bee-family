<script setup lang="ts">
/**
 * 搜索结果分组（02 文档 §5.9）。
 *
 * <p>标题 14/600 + 计数 + 右侧「查看全部 →」。内容由 `default` 插槽提供，
 * 保证照片网格、相册卡、里程碑行三种形态复用同一套标题样式。</p>
 */
interface Props {
  title: string
  total: number
  hasMore: boolean
}

withDefaults(defineProps<Props>(), {
  hasMore: false,
})

const emit = defineEmits<{ more: [] }>()
</script>

<template>
  <section class="bee-search-group">
    <header class="bee-search-group__head">
      <h2 class="bee-search-group__title">
        {{ title }}
        <span class="bee-search-group__count">· {{ total }}</span>
      </h2>
      <button v-if="hasMore" class="bee-search-group__more" type="button" @click="emit('more')">
        查看全部 →
      </button>
    </header>
    <div class="bee-search-group__body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.bee-search-group + .bee-search-group {
  margin-top: var(--sp-8);
}

.bee-search-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  margin-bottom: var(--sp-3);
}

.bee-search-group__title {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-search-group__count {
  font-weight: 500;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.bee-search-group__more {
  font-size: 13px;
  font-weight: 500;
  color: var(--honey-600);
  border-radius: var(--r-sm);
  padding: 2px var(--sp-1);
}

.bee-search-group__more:hover {
  color: var(--honey-700);
  background: var(--honey-50);
}

.bee-search-group__body {
  min-width: 0;
}
</style>
