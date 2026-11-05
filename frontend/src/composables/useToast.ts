import { useToastStore, type ToastAction, type ToastType } from '@/stores/ui-toast'

/**
 * Toast 的便捷入口，抹平「store 实例」与「调用点」之间的样板代码。
 *
 * <p>用法：`const toast = useToast(); toast.success('已保存')`。</p>
 */
export function useToast(): {
  success: (message: string, action?: ToastAction) => number
  warning: (message: string, action?: ToastAction) => number
  danger: (message: string, action?: ToastAction) => number
  info: (message: string, action?: ToastAction) => number
  push: (type: ToastType, message: string, action?: ToastAction) => number
  dismiss: (id: number) => void
} {
  const store = useToastStore()

  return {
    success: (message, action) => store.success(message, action),
    warning: (message, action) => store.warning(message, action),
    danger: (message, action) => store.danger(message, action),
    info: (message, action) => store.info(message, action),
    push: (type, message, action) => store.push({ type, message, action }),
    dismiss: (id) => store.dismiss(id),
  }
}
