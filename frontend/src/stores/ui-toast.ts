import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export type ToastType = 'success' | 'warning' | 'danger' | 'info'

export interface ToastAction {
  label: string
  handler: () => void
}

export interface ToastItem {
  id: number
  type: ToastType
  message: string
  action?: ToastAction
}

/** 同时最多展示 3 条（02 文档 §4.17） */
const MAX_TOASTS = 3
/** 自动消失时间 */
export const TOAST_DURATION = 3500

/**
 * 全局 Toast 队列。
 *
 * <p>只存内存；`BeeToastHost` 负责渲染，任何组件通过 `useToast()` 推送。</p>
 */
export const useToastStore = defineStore('toast', () => {
  const list = ref<ToastItem[]>([])
  const timers = new Map<number, number>()
  let seed = 0

  const count = computed(() => list.value.length)

  function clearTimer(id: number): void {
    const timer = timers.get(id)
    if (timer !== undefined) {
      window.clearTimeout(timer)
      timers.delete(id)
    }
  }

  function dismiss(id: number): void {
    clearTimer(id)
    list.value = list.value.filter((item) => item.id !== id)
  }

  function push(payload: { type: ToastType; message: string; action?: ToastAction }): number {
    seed += 1
    const id = seed
    // 超出上限时挤掉最旧的一条
    while (list.value.length >= MAX_TOASTS) {
      const oldest = list.value[0]
      if (!oldest) {
        break
      }
      dismiss(oldest.id)
    }
    const item: ToastItem = { id, type: payload.type, message: payload.message, action: payload.action }
    list.value = [...list.value, item]
    timers.set(
      id,
      window.setTimeout(() => dismiss(id), TOAST_DURATION),
    )
    return id
  }

  const success = (message: string, action?: ToastAction): number => push({ type: 'success', message, action })
  const warning = (message: string, action?: ToastAction): number => push({ type: 'warning', message, action })
  const danger = (message: string, action?: ToastAction): number => push({ type: 'danger', message, action })
  const info = (message: string, action?: ToastAction): number => push({ type: 'info', message, action })

  function reset(): void {
    timers.forEach((timer) => window.clearTimeout(timer))
    timers.clear()
    list.value = []
  }

  return { list, count, push, dismiss, success, warning, danger, info, reset }
})
