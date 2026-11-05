import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as photoApi from '@/api/photo'
import type { Photo } from '@/types/models'

/** 当前列表上下文：相册内 / 未分类 / 全家庭 */
export interface PhotoContext {
  albumId: number | null
  unclassified: boolean
  sort: photoApi.PhotoSort
}

export interface LightboxState {
  open: boolean
  list: Photo[]
  index: number
}

const DEFAULT_CONTEXT: PhotoContext = { albumId: null, unclassified: false, sort: 'takenAt_desc' }

/**
 * 照片网格的分页、选择与灯箱状态。
 *
 * <p>不 import 其他 store；上传成功的照片由页面调用 `prepend` 原地插入。</p>
 */
export const usePhotoStore = defineStore('photo', () => {
  const items = ref<Photo[]>([])
  const page = ref(0)
  const size = ref(60)
  const total = ref(0)
  const hasMore = ref(false)
  const loading = ref(false)
  const initialLoading = ref(false)
  const selection = ref<Set<number>>(new Set())
  const selectionMode = ref(false)
  const lightbox = ref<LightboxState>({ open: false, list: [], index: 0 })
  const context = ref<PhotoContext>({ ...DEFAULT_CONTEXT })

  const selectedCount = computed(() => selection.value.size)
  const selectedIds = computed(() => Array.from(selection.value))

  async function loadFirst(query: Partial<PhotoContext> & { size?: number } = {}): Promise<void> {
    context.value = {
      albumId: query.albumId ?? null,
      unclassified: query.unclassified ?? false,
      sort: query.sort ?? 'takenAt_desc',
    }
    size.value = query.size ?? 60
    page.value = 0
    initialLoading.value = true
    loading.value = true
    clearSelection()
    try {
      const data = await photoApi.listPhotos({
        albumId: context.value.albumId,
        unclassified: context.value.unclassified,
        sort: context.value.sort,
        page: 0,
        size: size.value,
      })
      items.value = data.items
      total.value = data.total
      hasMore.value = data.hasMore
    } finally {
      initialLoading.value = false
      loading.value = false
    }
  }

  async function loadMore(): Promise<void> {
    if (loading.value || !hasMore.value) {
      return
    }
    loading.value = true
    try {
      const next = page.value + 1
      const data = await photoApi.listPhotos({
        albumId: context.value.albumId,
        unclassified: context.value.unclassified,
        sort: context.value.sort,
        page: next,
        size: size.value,
      })
      const seen = new Set(items.value.map((item) => item.id))
      items.value = [...items.value, ...data.items.filter((item) => !seen.has(item.id))]
      page.value = next
      total.value = data.total
      hasMore.value = data.hasMore
    } finally {
      loading.value = false
    }
  }

  /** 上传成功后原地插入到列表头部（不整页刷新） */
  function prepend(list: Photo[]): void {
    if (list.length === 0) {
      return
    }
    const incoming = new Set(list.map((item) => item.id))
    items.value = [...list, ...items.value.filter((item) => !incoming.has(item.id))]
    total.value += list.length
  }

  function patch(photo: Photo): void {
    items.value = items.value.map((item) => (item.id === photo.id ? photo : item))
    lightbox.value = {
      ...lightbox.value,
      list: lightbox.value.list.map((item) => (item.id === photo.id ? photo : item)),
    }
  }

  function removeMany(ids: number[]): void {
    const removed = new Set(ids)
    items.value = items.value.filter((item) => !removed.has(item.id))
    total.value = Math.max(0, total.value - ids.length)
    lightbox.value = {
      open: false,
      list: [],
      index: 0,
    }
    clearSelection()
  }

  function toggleSelection(id: number): void {
    const next = new Set(selection.value)
    if (next.has(id)) {
      next.delete(id)
    } else {
      next.add(id)
    }
    selection.value = next
    selectionMode.value = next.size > 0
  }

  function clearSelection(): void {
    selection.value = new Set()
    selectionMode.value = false
  }

  function openLightbox(index: number, list?: Photo[]): void {
    lightbox.value = { open: true, list: list ?? items.value, index }
  }

  function closeLightbox(): void {
    lightbox.value = { ...lightbox.value, open: false }
  }

  function stepLightbox(delta: number): void {
    const length = lightbox.value.list.length
    if (length === 0) {
      return
    }
    const next = (lightbox.value.index + delta + length) % length
    lightbox.value = { ...lightbox.value, index: next }
  }

  function reset(): void {
    items.value = []
    page.value = 0
    total.value = 0
    hasMore.value = false
    clearSelection()
    closeLightbox()
  }

  return {
    items,
    page,
    size,
    total,
    hasMore,
    loading,
    initialLoading,
    selection,
    selectionMode,
    selectedCount,
    selectedIds,
    lightbox,
    context,
    loadFirst,
    loadMore,
    prepend,
    patch,
    removeMany,
    toggleSelection,
    clearSelection,
    openLightbox,
    closeLightbox,
    stepLightbox,
    reset,
  }
})
