import { nextTick, onBeforeUnmount, watch, type Ref } from 'vue'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

/**
 * 模态类浮层的焦点陷阱。
 *
 * <p>激活时：记录当前焦点 → 把焦点移到容器内第一个（或 `initialSelector` 指定的）可聚焦元素
 * → 拦截 `Tab` / `Shift+Tab` 在容器内循环。失活时把焦点还原到打开前的元素。</p>
 *
 * @param container 浮层根元素
 * @param active 是否激活（通常绑定 `open`）
 * @param initialSelector 打开时优先聚焦的选择器，缺省为第一个可聚焦元素
 */
export function useFocusTrap(
  container: Ref<HTMLElement | null>,
  active: Ref<boolean>,
  initialSelector = '',
): void {
  let lastActive: HTMLElement | null = null

  function focusable(): HTMLElement[] {
    const root = container.value
    if (!root) return []
    return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
      (el) => el.offsetParent !== null || el === document.activeElement,
    )
  }

  function focusInitial(): void {
    const root = container.value
    if (!root) return
    const preferred = initialSelector ? root.querySelector<HTMLElement>(initialSelector) : null
    const target = preferred ?? focusable()[0] ?? root
    target.focus({ preventScroll: true })
  }

  function onKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Tab') return
    const items = focusable()
    if (items.length === 0) {
      event.preventDefault()
      return
    }
    const first = items[0]
    const last = items[items.length - 1]
    if (!first || !last) return
    const current = document.activeElement as HTMLElement | null
    if (event.shiftKey && (current === first || !current || !container.value?.contains(current))) {
      event.preventDefault()
      last.focus()
      return
    }
    if (!event.shiftKey && current === last) {
      event.preventDefault()
      first.focus()
    }
  }

  function detach(): void {
    document.removeEventListener('keydown', onKeydown, true)
    if (lastActive && document.contains(lastActive)) {
      lastActive.focus({ preventScroll: true })
    }
    lastActive = null
  }

  watch(
    active,
    (value) => {
      if (value) {
        lastActive = document.activeElement as HTMLElement | null
        document.addEventListener('keydown', onKeydown, true)
        void nextTick(focusInitial)
      } else {
        detach()
      }
    },
    { immediate: true },
  )

  onBeforeUnmount(detach)
}
