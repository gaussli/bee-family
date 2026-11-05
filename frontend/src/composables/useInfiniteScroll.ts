import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

/**
 * 无限滚动哨兵。
 *
 * <p>把一个不可见的 `div` 放到列表末尾，进入视口（或距视口 `rootMargin` 内）时触发加载。
 * 回调内部由调用方通过 `canLoad()` 自行决定是否真的发请求（`hasMore` / `loading` 判断），
 * 避免重复请求。</p>
 *
 * @param canLoad 是否允许触发（通常返回 `hasMore.value && !loading.value`）
 * @param onLoad 触发时执行的回调
 * @param rootMargin 预加载距离，默认 `400px`（02 文档 §4.20 时间轴规格）
 * @param root 滚动容器，缺省为 viewport
 */
export function useInfiniteScroll(
  canLoad: () => boolean,
  onLoad: () => void,
  rootMargin = '400px',
  root?: Ref<HTMLElement | null>,
): {
  sentinel: Ref<HTMLElement | null>
  observe: () => void
  disconnect: () => void
} {
  const sentinel = ref<HTMLElement | null>(null)
  let observer: IntersectionObserver | null = null

  function disconnect(): void {
    observer?.disconnect()
    observer = null
  }

  function observe(): void {
    disconnect()
    const el = sentinel.value
    if (!el || typeof IntersectionObserver === 'undefined') return

    observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0]
        if (!entry?.isIntersecting) return
        if (!canLoad()) return
        onLoad()
      },
      { root: root?.value ?? null, rootMargin, threshold: 0 },
    )
    observer.observe(el)
  }

  onMounted(observe)
  onBeforeUnmount(disconnect)

  // 哨兵通常放在 `v-if` / `v-else` 分支里（首屏骨架 vs 列表）：组件 mounted 时元素还不存在，
  // 此时 observe() 会拿到 null 直接返回，之后再也没人观察它 —— 无限滚动就此失效。
  // 用 watch + flush:'post' 等元素真正挂载后补一次观察。
  watch(
    sentinel,
    (el) => {
      if (el) observe()
    },
    { flush: 'post' },
  )

  return { sentinel, observe, disconnect }
}
