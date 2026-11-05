/**
 * 防抖：返回一个带 cancel 的包装函数。
 *
 * @param fn    原函数
 * @param delay 延迟毫秒
 */
export function debounce<T extends (...args: never[]) => void>(
  fn: T,
  delay = 300,
): ((...args: Parameters<T>) => void) & { cancel: () => void } {
  let timer: number | undefined
  const wrapped = (...args: Parameters<T>): void => {
    if (timer !== undefined) {
      window.clearTimeout(timer)
    }
    timer = window.setTimeout(() => {
      timer = undefined
      fn(...args)
    }, delay)
  }
  wrapped.cancel = (): void => {
    if (timer !== undefined) {
      window.clearTimeout(timer)
      timer = undefined
    }
  }
  return wrapped
}
