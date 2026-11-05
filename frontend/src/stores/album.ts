import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as albumApi from '@/api/album'
import type { Album, AlbumDetail, DeleteAlbumResult, Unclassified } from '@/types/models'

/**
 * 相册列表与当前相册。
 *
 * <p>刻意不 import `photo` store（docs §9.3 禁止 store 互相引用），
 * 跨 store 的协调由页面组件负责。</p>
 */
export const useAlbumStore = defineStore('album', () => {
  const items = ref<Album[]>([])
  const unclassified = ref<Unclassified>({ count: 0, coverPhotoId: null, coverThumbKey: null })
  const current = ref<AlbumDetail | null>(null)
  const loading = ref(false)
  const loaded = ref(false)

  const totalCount = computed(() => items.value.length)

  async function fetchList(force = false): Promise<void> {
    if (loaded.value && !force) {
      return
    }
    loading.value = true
    try {
      const data = await albumApi.listAlbums()
      items.value = data.items
      unclassified.value = data.unclassified
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(id: number): Promise<AlbumDetail> {
    loading.value = true
    try {
      current.value = await albumApi.getAlbum(id)
      return current.value
    } finally {
      loading.value = false
    }
  }

  async function create(body: albumApi.AlbumBody): Promise<Album> {
    const created = await albumApi.createAlbum(body)
    items.value = [created, ...items.value]
    return created
  }

  async function update(id: number, body: albumApi.AlbumUpdateBody): Promise<Album> {
    const updated = await albumApi.updateAlbum(id, body)
    items.value = items.value.map((item) => (item.id === id ? { ...item, ...updated } : item))
    if (current.value && current.value.id === id) {
      current.value = { ...current.value, ...updated }
    }
    return updated
  }

  async function remove(id: number, strategy: albumApi.PhotoStrategy): Promise<DeleteAlbumResult> {
    const result = await albumApi.deleteAlbum(id, strategy)
    items.value = items.value.filter((item) => item.id !== id)
    if (current.value?.id === id) {
      current.value = null
    }
    if (strategy === 'move') {
      unclassified.value = {
        ...unclassified.value,
        count: unclassified.value.count + result.movedPhotos,
      }
    }
    return result
  }

  /** 照片变动后同步相册计数（不重新请求，避免闪烁） */
  function adjustPhotoCount(albumId: number | null, delta: number): void {
    if (albumId === null) {
      unclassified.value = { ...unclassified.value, count: Math.max(0, unclassified.value.count + delta) }
      return
    }
    items.value = items.value.map((item) =>
      item.id === albumId ? { ...item, photoCount: Math.max(0, item.photoCount + delta) } : item,
    )
    if (current.value?.id === albumId) {
      current.value = { ...current.value, photoCount: Math.max(0, current.value.photoCount + delta) }
    }
  }

  function reset(): void {
    items.value = []
    unclassified.value = { count: 0, coverPhotoId: null, coverThumbKey: null }
    current.value = null
    loaded.value = false
  }

  return {
    items,
    unclassified,
    current,
    loading,
    loaded,
    totalCount,
    fetchList,
    fetchDetail,
    create,
    update,
    remove,
    adjustPhotoCount,
    reset,
  }
})
