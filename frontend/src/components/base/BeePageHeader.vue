<script setup lang="ts">
/**
 * 页面标题区。标题 + 可选计数徽章 + 右侧操作区，另有可选描述行。
 * 移动端标题降为 `--fs-h2`，操作区由使用方决定是否只留图标。
 */
interface Props {
  title: string
  count?: number | null
  description?: string
}

withDefaults(defineProps<Props>(), {
  count: null,
  description: '',
})
</script>

<template>
  <div class="bee-page-header">
    <div class="bee-page-header__row">
      <div class="bee-page-header__heading">
        <h1 class="bee-page-header__title">{{ title }}</h1>
        <span v-if="count !== null" class="bee-page-header__count">{{ count }}</span>
        <slot />
      </div>
      <div v-if="$slots.actions" class="bee-page-header__actions">
        <slot name="actions" />
      </div>
    </div>
    <p v-if="description" class="bee-page-header__desc">{{ description }}</p>
  </div>
</template>

<style scoped>
.bee-page-header {
  margin-bottom: var(--sp-6);
}

.bee-page-header__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  min-height: 64px;
}

.bee-page-header__heading {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-width: 0;
}

.bee-page-header__title {
  font-family: var(--font-display);
  font-size: var(--fs-h1);
  line-height: var(--lh-h1);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bee-page-header__count {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 var(--sp-2);
  border-radius: var(--r-full);
  background: var(--warm-100);
  color: var(--text-secondary);
  font-size: var(--fs-caption);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.bee-page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex: none;
}

.bee-page-header__desc {
  margin-top: var(--sp-1);
  max-width: 640px;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
}

@media (max-width: 767px) {
  .bee-page-header__row {
    min-height: 48px;
    margin-bottom: var(--sp-2);
  }

  .bee-page-header__title {
    font-size: var(--fs-h2);
    line-height: var(--lh-h2);
  }

  .bee-page-header__actions :deep(.bee-btn__label) {
    display: none;
  }

  .bee-page-header__actions :deep(.bee-btn) {
    width: 40px;
    padding: 0;
  }
}
</style>
