/**
 * objectURL 生命周期管理：创建即登记，组件卸载时统一释放，避免内存泄漏。
 */

const registry = new Set<string>()

/**
 * 创建并登记一个 objectURL。
 *
 * @param file 文件
 */
export function createObjectUrl(file: File): string {
  const url = URL.createObjectURL(file)
  registry.add(url)
  return url
}

/**
 * 释放单个 objectURL（已释放则忽略）。
 *
 * @param url 地址
 */
export function revokeObjectUrl(url: string | null | undefined): void {
  if (!url) {
    return
  }
  URL.revokeObjectURL(url)
  registry.delete(url)
}

/**
 * 释放全部登记的 objectURL（页面/队列销毁时调用）。
 */
export function revokeAllObjectUrls(): void {
  registry.forEach((url) => URL.revokeObjectURL(url))
  registry.clear()
}
