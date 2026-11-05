import { computed, onScopeDispose, ref, type ComputedRef, type Ref } from 'vue'

/** 断点（与 `02-ui-design.md` §7 一致）。 */
export const BREAKPOINT_MOBILE = 768
export const BREAKPOINT_DESKTOP = 1024

/** 模块级共享宽度，避免每个组件各挂一个 resize 监听。 */
const viewportWidth = ref<number>(typeof window === 'undefined' ? 1440 : window.innerWidth)
let listeners = 0

function update(): void {
  viewportWidth.value = window.innerWidth
}

function subscribe(): void {
  if (typeof window === 'undefined') return
  listeners += 1
  if (listeners === 1) {
    window.addEventListener('resize', update, { passive: true })
    window.addEventListener('orientationchange', update)
  }
  onScopeDispose(() => {
    listeners -= 1
    if (listeners <= 0) {
      listeners = 0
      window.removeEventListener('resize', update)
      window.removeEventListener('orientationchange', update)
    }
  })
}

/**
 * 响应式断点判断。
 *
 * @returns `isMobile`(<768)、`isTablet`(768–1023)、`isDesktop`(≥1024) 与原始 `width`
 */
export function useBreakpoint(): {
  width: Readonly<Ref<number>>
  isMobile: ComputedRef<boolean>
  isTablet: ComputedRef<boolean>
  isDesktop: ComputedRef<boolean>
} {
  subscribe()

  return {
    width: viewportWidth,
    isMobile: computed(() => viewportWidth.value < BREAKPOINT_MOBILE),
    isTablet: computed(
      () => viewportWidth.value >= BREAKPOINT_MOBILE && viewportWidth.value < BREAKPOINT_DESKTOP,
    ),
    isDesktop: computed(() => viewportWidth.value >= BREAKPOINT_DESKTOP),
  }
}
