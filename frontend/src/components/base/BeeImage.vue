<script setup lang="ts">
/**
 * 图片。所有业务图片必须经由本组件渲染，统一承担三件事：
 *
 * 1. **懒加载**：`IntersectionObserver`（`rootMargin: 200px`）进入视口才设 `src`；
 * 2. **占位与淡入**：加载期显示 `--warm-100` 呼吸底纹，`onload` 后 300ms 淡入；
 * 3. **失败兜底**：破损图标 + 「重新加载」，点击重试会带随机参数绕过缓存。
 *
 * `alt` 为必填（PRD §10 可访问性要求）。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import BeeIcon from './BeeIcon.vue'

interface Props {
  src?: string | null
  alt: string
  ratio?: string
  fit?: 'cover' | 'contain'
  lazy?: boolean
  radius?: string
}

const props = withDefaults(defineProps<Props>(), {
  src: null,
  ratio: '1/1',
  fit: 'cover',
  lazy: true,
  radius: 'var(--r-sm)',
})

const emit = defineEmits<{ load: []; error: [] }>()

const rootRef = ref<HTMLElement | null>(null)
const visible = ref<boolean>(false)
const loaded = ref<boolean>(false)
const failed = ref<boolean>(false)
const retrySeed = ref<number>(0)

let observer: IntersectionObserver | null = null

/** 拼接重试参数：同一 key 再次请求时绕过浏览器与网关缓存。 */
const resolvedSrc = computed<string>(() => {
  if (!props.src) return ''
  if (retrySeed.value === 0) return props.src
  const joiner = props.src.includes('?') ? '&' : '?'
  return `${props.src}${joiner}retry=${retrySeed.value}`
})

const shouldLoad = computed<boolean>(() => !!resolvedSrc.value && (visible.value || !props.lazy))

function observe(): void {
  if (!props.lazy || typeof IntersectionObserver === 'undefined') {
    visible.value = true
    return
  }
  const el = rootRef.value
  if (!el) {
    visible.value = true
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      const entry = entries[0]
      if (entry?.isIntersecting) {
        visible.value = true
        observer?.disconnect()
        observer = null
      }
    },
    { rootMargin: '200px' },
  )
  observer.observe(el)
}

function dispose(): void {
  observer?.disconnect()
  observer = null
}

function onLoad(): void {
  loaded.value = true
  failed.value = false
  emit('load')
}

function onError(): void {
  failed.value = true
  loaded.value = false
  emit('error')
}

function retry(event: MouseEvent): void {
  event.stopPropagation()
  failed.value = false
  loaded.value = false
  retrySeed.value = Date.now()
}

watch(
  () => props.src,
  () => {
    loaded.value = false
    failed.value = false
    retrySeed.value = 0
    dispose()
    if (!visible.value) observe()
  },
)

onMounted(observe)
onBeforeUnmount(dispose)
</script>

<template>
  <div
    ref="rootRef"
    class="bee-image"
    :style="{ aspectRatio: ratio, borderRadius: radius }"
  >
    <img
      v-if="shouldLoad && !failed"
      class="bee-image__img"
      :class="{ 'is-loaded': loaded }"
      :src="resolvedSrc"
      :alt="alt"
      :style="{ objectFit: fit }"
      decoding="async"
      @load="onLoad"
      @error="onError"
    />

    <div v-if="!loaded && !failed" class="bee-image__placeholder" aria-hidden="true" />

    <button v-if="failed" class="bee-image__error" type="button" @click="retry">
      <BeeIcon name="image-broken" :size="20" />
      <span class="bee-image__error-text">加载失败，点击重试</span>
    </button>
  </div>
</template>

<style scoped>
.bee-image {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--warm-100);
}

.bee-image__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  transition: opacity 300ms var(--ease-out);
}

.bee-image__img.is-loaded {
  opacity: 1;
}

.bee-image__placeholder {
  position: absolute;
  inset: 0;
  background: var(--warm-100);
  animation: bee-image-breathe 1.8s var(--ease-in-out) infinite;
}

@keyframes bee-image-breathe {
  0%,
  100% {
    opacity: 0.65;
  }
  50% {
    opacity: 1;
  }
}

.bee-image__error {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-1);
  padding: var(--sp-2);
  color: var(--text-tertiary);
  background: var(--warm-100);
  text-align: center;
}

.bee-image__error:hover {
  color: var(--text-secondary);
}

.bee-image__error-text {
  font-size: 11px;
  line-height: 1.3;
}
</style>
