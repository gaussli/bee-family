<script setup lang="ts">
/**
 * 头像。成人成员用圆形；宝宝用尖顶六边形（`clip-path` 双层嵌套实现外框）。
 *
 * 无图时显示昵称首字：成人 `--warm-200` 底 + `--text-primary` 字；
 * 宝宝 `--honey-100` 底 + `--honey-700` 字。
 */
import { computed, ref, watch } from 'vue'

interface Props {
  src?: string | null
  name: string
  shape?: 'circle' | 'hex'
  size?: number
  ring?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  src: null,
  shape: 'circle',
  size: 40,
  ring: false,
})

const failed = ref<boolean>(false)

watch(
  () => props.src,
  () => {
    failed.value = false
  },
)

const initial = computed<string>(() => {
  const trimmed = props.name.trim()
  return trimmed.length > 0 ? trimmed.slice(0, 1).toUpperCase() : '?'
})

const showImage = computed<boolean>(() => !!props.src && !failed.value)

const fontSize = computed<string>(() => `${Math.round(props.size * 0.42)}px`)
</script>

<template>
  <span
    class="bee-avatar"
    :class="[`bee-avatar--${shape}`, { 'bee-avatar--ring': ring }]"
    :style="{ width: `${size}px`, height: `${size}px` }"
    :title="name"
  >
    <img
      v-if="showImage"
      class="bee-avatar__img"
      :src="src ?? ''"
      :alt="name"
      loading="lazy"
      @error="failed = true"
    />
    <span v-else class="bee-avatar__initial" :style="{ fontSize }">{{ initial }}</span>
  </span>
</template>

<style scoped>
.bee-avatar {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  overflow: hidden;
  background: var(--warm-200);
  color: var(--text-primary);
  user-select: none;
}

.bee-avatar--circle {
  border-radius: var(--r-full);
}

.bee-avatar--hex {
  background: var(--honey-100);
  color: var(--honey-700);
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
}

.bee-avatar--ring {
  box-shadow: 0 0 0 2px var(--bg-surface);
}

.bee-avatar--hex.bee-avatar--ring {
  box-shadow: none;
  outline: 2px solid var(--honey-200);
  outline-offset: -4px;
}

.bee-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.bee-avatar__initial {
  font-weight: 600;
  line-height: 1;
}
</style>
