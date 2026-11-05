/**
 * Pinia store 统一出口。
 *
 * <p>store 之间禁止互相 import（docs §9.3）：跨域协调放在页面组件里完成。</p>
 */
export { useAuthStore } from './auth'
export { useFamilyStore } from './family'
export { useAlbumStore } from './album'
export { usePhotoStore } from './photo'
export { useToastStore, TOAST_DURATION } from './ui-toast'
export type { ToastItem, ToastType, ToastAction } from './ui-toast'
export type { PhotoContext, LightboxState } from './photo'
