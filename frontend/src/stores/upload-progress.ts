import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * 全站在途上传注册表（docs/07 §10.4）。
 *
 * <p>单一真源：`activeCount` 供家庭切换器判定阻断态（C-06：N>0 即阻断），
 * `cancelAll()` 供切换家庭时集中取消在途业务请求（§10.3 第 6 项）。
 * 所有上传队列实例（AppShell / 相册详情）都把 AbortController 注册到这里。</p>
 */
export const useUploadProgressStore = defineStore('upload-progress', () => {
  const activeIds = ref<Set<number>>(new Set<number>())
  const controllers = new Map<number, AbortController>()

  /** queued + uploading 的任务数。 */
  const activeCount = computed<number>(() => activeIds.value.size)

  function begin(id: number): void {
    if (activeIds.value.has(id)) return
    const next = new Set(activeIds.value)
    next.add(id)
    activeIds.value = next
  }

  function end(id: number): void {
    if (!activeIds.value.has(id)) return
    const next = new Set(activeIds.value)
    next.delete(id)
    activeIds.value = next
  }

  function registerController(id: number, controller: AbortController): void {
    controllers.set(id, controller)
  }

  function unregisterController(id: number): void {
    controllers.delete(id)
  }

  /**
   * 取消所有在途请求并清空阻断态。
   * 各队列的 `catch` 会把对应任务标为失败并调用 `end()`，最终 N 归 0。
   */
  function cancelAll(): void {
    for (const controller of controllers.values()) controller.abort()
    controllers.clear()
    activeIds.value = new Set<number>()
  }

  function reset(): void {
    controllers.clear()
    activeIds.value = new Set<number>()
  }

  return {
    activeIds,
    activeCount,
    begin,
    end,
    registerController,
    unregisterController,
    cancelAll,
    reset,
  }
})
