<script setup lang="ts">
/**
 * 相册详情（02 文档 §5.4）。
 *
 * <p>包含：面包屑 → 头部（封面 96×96 + 标题 + 元信息）→ sticky 操作条 →
 * 照片网格（3/4/5 列）→ 批量选择浮动条 → 灯箱 / 上传浮层 / 拖拽全屏遮罩。</p>
 *
 * <p>`id === 'unclassified'` 时复用同一页面展示「未分类」照片（无相册对象），
 * 这样相册列表里的未分类卡也能进同一个网格体验。</p>
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeEmpty from '@/components/base/BeeEmpty.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeImage from '@/components/base/BeeImage.vue'
import BeeModal from '@/components/base/BeeModal.vue'
import BeeSelect from '@/components/base/BeeSelect.vue'
import BeeSkeleton from '@/components/base/BeeSkeleton.vue'
import PhotoGrid from '@/components/business/PhotoGrid.vue'
import Lightbox from '@/components/business/Lightbox.vue'
import UploadPanel from '@/components/business/UploadPanel.vue'
import AlbumFormModal from '@/components/business/AlbumFormModal.vue'
import DeleteAlbumModal from '@/components/business/DeleteAlbumModal.vue'
import * as photoApi from '@/api/photo'
import { ApiError } from '@/api/http'
import { usePhotoStore } from '@/stores/photo'
import { useAlbumStore } from '@/stores/album'
import { useToast } from '@/composables/useToast'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { fileUrl } from '@/utils/fileUrl'
import { formatDate } from '@/utils/format'
import type { PhotoSort } from '@/api/photo'
import type { AlbumDetail, Photo } from '@/types/models'

interface Props {
  id: string
}

const props = defineProps<Props>()

const router = useRouter()
const photoStore = usePhotoStore()
const albumStore = useAlbumStore()
const toast = useToast()

const uploadPanelRef = ref<InstanceType<typeof UploadPanel> | null>(null)

const isUnclassified = computed<boolean>(() => props.id === 'unclassified')
const albumId = computed<number | null>(() => (isUnclassified.value ? null : Number(props.id)))

const album = ref<AlbumDetail | null>(null)
const photos = ref<Photo[]>([])
const page = ref<number>(0)
const hasMore = ref<boolean>(false)
const total = ref<number>(0)
const loadingMore = ref<boolean>(false)
const initialLoading = ref<boolean>(true)
const loadError = ref<string>('')

const sort = ref<PhotoSort>('takenAt_desc')
const view = ref<'grid' | 'list'>('grid')
const selectionMode = ref<boolean>(false)
const selected = ref<Set<number>>(new Set())

const uploadOpen = ref<boolean>(false)
const editOpen = ref<boolean>(false)
const deleteOpen = ref<boolean>(false)
const moveOpen = ref<boolean>(false)
const moveTarget = ref<number | null>(null)
const moveLoading = ref<boolean>(false)

const lightboxOpen = ref<boolean>(false)
const lightboxIndex = ref<number>(0)

const dragging = ref<boolean>(false)
let dragDepth = 0

const selectedCount = computed<number>(() => selected.value.size)

const pageTitle = computed<string>(() => (isUnclassified.value ? '未分类' : album.value?.name ?? '相册'))

const coverUrl = computed<string>(() => fileUrl(album.value?.coverThumbKey ?? null, 'thumb'))

const dateRange = computed<string>(() => {
  if (!album.value) return ''
  const from = album.value.takenFrom
  const to = album.value.takenTo
  if (!from && !to) return ''
  if (from && to && from !== to) return `${formatDate(from)} – ${formatDate(to)}`
  return formatDate(from ?? to ?? '')
})

const moveOptions = computed(() =>
  albumStore.items
    .filter((item) => item.id !== albumId.value)
    .map((item) => ({ label: item.name, value: item.id })),
)

const sortOptions: { label: string; value: PhotoSort }[] = [
  { label: '拍摄时间（新→旧）', value: 'takenAt_desc' },
  { label: '拍摄时间（旧→新）', value: 'takenAt_asc' },
  { label: '上传时间（新→旧）', value: 'createdAt_desc' },
]

async function loadAlbum(): Promise<void> {
  if (isUnclassified.value) return
  try {
    album.value = await albumStore.fetchDetail(Number(props.id))
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '相册加载失败'
  }
}

async function loadPhotos(reset = false): Promise<void> {
  if (reset) {
    initialLoading.value = true
    loadError.value = ''
    page.value = 0
  } else {
    loadingMore.value = true
  }
  try {
    const result = await photoApi.listPhotos({
      albumId: isUnclassified.value ? null : albumId.value,
      unclassified: isUnclassified.value,
      page: page.value,
      size: 60,
      sort: sort.value,
    })
    photos.value = reset ? result.items : [...photos.value, ...result.items]
    hasMore.value = result.hasMore
    total.value = result.total
  } catch (error) {
    const message = error instanceof ApiError ? error.message : '照片加载失败'
    if (reset) loadError.value = message
    else toast.danger(message)
  } finally {
    initialLoading.value = false
    loadingMore.value = false
  }
}

async function loadMore(): Promise<void> {
  if (loadingMore.value || !hasMore.value) return
  page.value += 1
  await loadPhotos(false)
}

const { sentinel } = useInfiniteScroll(
  () => hasMore.value && !loadingMore.value && !initialLoading.value,
  () => {
    void loadMore()
  },
  '600px',
)

function onSortChange(value: string | number | null): void {
  sort.value = (value as PhotoSort) ?? 'takenAt_desc'
  void loadPhotos(true)
}

function openLightbox(index: number): void {
  lightboxIndex.value = index
  lightboxOpen.value = true
}

function toggleSelect(id: number): void {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

function enterSelection(): void {
  selectionMode.value = true
  selected.value = new Set()
}

function leaveSelection(): void {
  selectionMode.value = false
  selected.value = new Set()
}

function onUploaded(photo: Photo): void {
  photos.value = [photo, ...photos.value]
  total.value += 1
  albumStore.adjustPhotoCount(photo.albumId, 1)
}

async function onBatchDelete(): Promise<void> {
  const ids = Array.from(selected.value)
  if (ids.length === 0) return
  try {
    const result = await photoApi.batchDelete(ids)
    photos.value = photos.value.filter((photo) => !selected.value.has(photo.id))
    total.value = Math.max(total.value - (result.deleted ?? ids.length), 0)
    albumStore.adjustPhotoCount(albumId.value, -(result.deleted ?? ids.length))
    toast.success(`已删除 ${result.deleted ?? ids.length} 张照片`)
    leaveSelection()
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '删除失败')
  }
}

async function onBatchMove(): Promise<void> {
  const ids = Array.from(selected.value)
  if (ids.length === 0) return
  moveLoading.value = true
  try {
    const result = await photoApi.batchMove(ids, moveTarget.value)
    const moved = result.moved ?? ids.length
    photos.value = photos.value.filter((photo) => !selected.value.has(photo.id))
    total.value = Math.max(total.value - moved, 0)
    albumStore.adjustPhotoCount(albumId.value, -moved)
    albumStore.adjustPhotoCount(moveTarget.value, moved)
    toast.success(`已移动 ${moved} 张照片`)
    moveOpen.value = false
    leaveSelection()
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '移动失败')
  } finally {
    moveLoading.value = false
  }
}

function onAlbumRemoved(): void {
  void router.replace({ name: 'albums' })
}

/* ---------- 拖拽上传（整页遮罩） ---------- */
function onDragEnter(event: DragEvent): void {
  if (!event.dataTransfer?.types.includes('Files')) return
  dragDepth += 1
  dragging.value = true
}

function onDragOver(event: DragEvent): void {
  if (!dragging.value) return
  event.preventDefault()
}

function onDragLeave(): void {
  dragDepth = Math.max(dragDepth - 1, 0)
  if (dragDepth === 0) dragging.value = false
}

function onDrop(event: DragEvent): void {
  if (!dragging.value) return
  event.preventDefault()
  dragDepth = 0
  dragging.value = false
  const files = event.dataTransfer?.files
  if (!files || files.length === 0) return
  uploadOpen.value = true
  uploadPanelRef.value?.addFiles(Array.from(files))
}

watch(
  () => props.id,
  async () => {
    leaveSelection()
    photos.value = []
    album.value = null
    await Promise.all([loadAlbum(), loadPhotos(true)])
    await albumStore.fetchList()
  },
)

onMounted(async () => {
  await Promise.all([loadAlbum(), loadPhotos(true), albumStore.fetchList()])
  window.addEventListener('dragenter', onDragEnter)
  window.addEventListener('dragover', onDragOver)
  window.addEventListener('dragleave', onDragLeave)
  window.addEventListener('drop', onDrop)
})

onBeforeUnmount(() => {
  window.removeEventListener('dragenter', onDragEnter)
  window.removeEventListener('dragover', onDragOver)
  window.removeEventListener('dragleave', onDragLeave)
  window.removeEventListener('drop', onDrop)
  photoStore.reset()
})
</script>

<template>
  <div class="bee-album-detail">
    <nav class="bee-album-detail__breadcrumb" aria-label="面包屑">
      <RouterLink to="/albums">相册</RouterLink>
      <span aria-hidden="true">/</span>
      <span>{{ pageTitle }}</span>
    </nav>

    <header class="bee-album-detail__header">
      <div class="bee-album-detail__cover">
        <BeeImage v-if="coverUrl" :src="coverUrl" :alt="`${pageTitle} 封面`" ratio="1/1" radius="var(--r-lg)" />
        <span v-else class="bee-album-detail__cover-placeholder">
          <BeeIcon name="hex" :size="28" :stroke-width="1.2" />
        </span>
      </div>

      <div class="bee-album-detail__info">
        <h1 class="bee-album-detail__title">{{ pageTitle }}</h1>
        <p class="bee-album-detail__meta">
          {{ total }} 张照片
          <template v-if="dateRange"> · {{ dateRange }}</template>
        </p>
        <p v-if="album?.description" class="bee-album-detail__desc">{{ album.description }}</p>
      </div>

      <div v-if="!isUnclassified" class="bee-album-detail__header-actions">
        <BeeButton variant="secondary" size="sm" @click="editOpen = true">编辑</BeeButton>
        <BeeButton variant="ghost" size="sm" icon-only aria-label="更多操作" @click="deleteOpen = true">
          <BeeIcon name="more" :size="16" />
        </BeeButton>
      </div>
    </header>

    <div class="bee-album-detail__toolbar">
      <div class="bee-album-detail__toolbar-left">
        <BeeButton size="sm" @click="uploadOpen = true">
          <template #icon><BeeIcon name="upload" :size="15" /></template>
          上传照片
        </BeeButton>
        <BeeButton v-if="!selectionMode" variant="ghost" size="sm" @click="enterSelection">选择</BeeButton>
        <BeeButton v-else variant="ghost" size="sm" @click="leaveSelection">取消选择</BeeButton>
      </div>

      <div class="bee-album-detail__toolbar-right">
        <div class="bee-album-detail__view-toggle" role="group" aria-label="视图切换">
          <button
            class="bee-album-detail__view-btn"
            :class="{ 'is-active': view === 'grid' }"
            type="button"
            aria-label="网格视图"
            @click="view = 'grid'"
          >
            <BeeIcon name="grid" :size="16" />
          </button>
          <button
            class="bee-album-detail__view-btn"
            :class="{ 'is-active': view === 'list' }"
            type="button"
            aria-label="列表视图"
            @click="view = 'list'"
          >
            <BeeIcon name="list" :size="16" />
          </button>
        </div>

        <BeeSelect
          class="bee-album-detail__sort"
          :model-value="sort"
          :options="sortOptions"
          placeholder="排序"
          @update:model-value="onSortChange"
        />
      </div>
    </div>

    <div v-if="loadError" class="bee-album-detail__error">
      <span>{{ loadError }}</span>
      <BeeButton variant="ghost" size="sm" @click="loadPhotos(true)">重试</BeeButton>
    </div>

    <BeeSkeleton v-if="initialLoading" variant="grid" :count="12" :columns="5" />

    <template v-else>
      <BeeEmpty
        v-if="photos.length === 0"
        variant="no-photo"
        title="这本相册还是空的"
        description="拖拽照片到这里，或点击上传按钮添加第一张。"
        action-text="上传照片"
        @action="uploadOpen = true"
      />

      <div v-else class="bee-album-detail__body" :class="{ 'is-list': view === 'list' }">
        <PhotoGrid
          :photos="photos"
          :selection-mode="selectionMode"
          :selected="selected"
          :loading-more="loadingMore"
          @open="openLightbox"
          @toggle="toggleSelect"
        />
      </div>

      <div ref="sentinel" class="bee-album-detail__sentinel" aria-hidden="true" />

      <p v-if="loadingMore" class="bee-album-detail__end">加载中…</p>
      <p v-else-if="!hasMore && photos.length > 0" class="bee-album-detail__end">没有更多了 · 共 {{ total }} 张</p>
    </template>

    <!-- 批量选择浮动条 -->
    <Transition name="bee-selection-bar">
      <div v-if="selectionMode && selectedCount > 0" class="bee-album-detail__selection">
        <span class="bee-album-detail__selection-count">已选 {{ selectedCount }} 张</span>
        <div class="bee-album-detail__selection-actions">
          <BeeButton variant="ghost" size="sm" @click="leaveSelection">取消</BeeButton>
          <BeeButton variant="secondary" size="sm" @click="moveOpen = true">移动到相册</BeeButton>
          <BeeButton variant="danger" size="sm" @click="onBatchDelete">删除</BeeButton>
        </div>
      </div>
    </Transition>

    <!-- 拖拽遮罩 -->
    <div v-if="dragging" class="bee-album-detail__dropzone">
      <span>松开即可上传到《{{ pageTitle }}》</span>
    </div>

    <Lightbox
      :photos="photos"
      :start-index="lightboxIndex"
      :open="lightboxOpen"
      @update:open="lightboxOpen = $event"
    />

    <UploadPanel
      ref="uploadPanelRef"
      :open="uploadOpen"
      :album-id="albumId"
      @close="uploadOpen = false"
      @uploaded="onUploaded"
    />

    <BeeModal
      v-model:open="moveOpen"
      title="移动到相册"
      size="sm"
      :loading="moveLoading"
      confirm-text="移动"
      @confirm="onBatchMove"
    >
      <BeeSelect
        v-model="moveTarget"
        :options="moveOptions"
        label="目标相册"
        placeholder="选择目标相册"
        clearable
      />
      <p class="bee-album-detail__move-hint">留空表示移动到「未分类」。</p>
    </BeeModal>

    <AlbumFormModal v-if="album" v-model:open="editOpen" :album="album" @saved="loadAlbum" />
    <DeleteAlbumModal
      v-if="album"
      v-model:open="deleteOpen"
      :album="album"
      @removed="onAlbumRemoved"
    />
  </div>
</template>

<style scoped>
.bee-album-detail__breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  height: 20px;
  margin-bottom: var(--sp-3);
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-album-detail__breadcrumb a:hover {
  color: var(--text-secondary);
}

.bee-album-detail__breadcrumb span:last-child {
  color: var(--text-secondary);
}

.bee-album-detail__header {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-4);
  min-height: 128px;
  padding-bottom: var(--sp-6);
}

.bee-album-detail__cover {
  width: 96px;
  flex: none;
  border-radius: var(--r-lg);
  overflow: hidden;
}

.bee-album-detail__cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1 / 1;
  border-radius: var(--r-lg);
  background: var(--honey-50);
  color: var(--honey-300);
}

.bee-album-detail__info {
  flex: 1;
  min-width: 0;
}

.bee-album-detail__title {
  font-family: var(--font-display);
  font-size: var(--fs-h2);
  line-height: var(--lh-h2);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-album-detail__meta {
  margin-top: var(--sp-1);
  font-size: 13px;
  color: var(--text-tertiary);
}

.bee-album-detail__desc {
  margin-top: var(--sp-2);
  max-width: 640px;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.bee-album-detail__header-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex: none;
}

.bee-album-detail__toolbar {
  position: sticky;
  top: var(--topbar-h);
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  height: 48px;
  margin-bottom: var(--sp-4);
  padding: 0 var(--sp-1);
  background: color-mix(in srgb, var(--bg-page) 92%, transparent);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border-subtle);
}

.bee-album-detail__toolbar-left,
.bee-album-detail__toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.bee-album-detail__view-toggle {
  display: inline-flex;
  padding: 2px;
  border-radius: var(--r-sm);
  background: var(--bg-sunken);
}

.bee-album-detail__view-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  color: var(--text-tertiary);
}

.bee-album-detail__view-btn.is-active {
  background: var(--bg-elevated);
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}

.bee-album-detail__sort {
  width: 168px;
}

.bee-album-detail__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  height: 48px;
  padding: 0 var(--sp-4);
  margin-bottom: var(--sp-4);
  border-radius: var(--r-md);
  background: var(--color-danger-bg);
  color: var(--color-danger);
  font-size: var(--fs-body);
}

.bee-album-detail__body.is-list :deep(.bee-photo-grid) {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-3);
}

.bee-album-detail__sentinel {
  height: 1px;
}

.bee-album-detail__end {
  padding: var(--sp-5) 0;
  text-align: center;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-album-detail__selection {
  position: sticky;
  bottom: var(--sp-6);
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  height: 56px;
  margin-top: var(--sp-4);
  padding: 0 var(--sp-5);
  border-radius: var(--r-lg);
  background: var(--bg-elevated);
  box-shadow: var(--shadow-lg);
}

.bee-album-detail__selection-count {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-primary);
}

.bee-album-detail__selection-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.bee-album-detail__dropzone {
  position: fixed;
  inset: var(--sp-4);
  z-index: 940;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px dashed var(--honey-400);
  border-radius: var(--r-lg);
  background: color-mix(in srgb, var(--honey-50) 92%, transparent);
  color: var(--text-brand);
  font-family: var(--font-display);
  font-size: var(--fs-h3);
  font-weight: 600;
  pointer-events: none;
}

.bee-album-detail__move-hint {
  margin-top: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-selection-bar-enter-active,
.bee-selection-bar-leave-active {
  transition:
    opacity var(--dur-base) var(--ease-out),
    transform var(--dur-base) var(--ease-out);
}

.bee-selection-bar-enter-from,
.bee-selection-bar-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 767px) {
  .bee-album-detail__header {
    min-height: auto;
    padding-bottom: var(--sp-4);
  }

  .bee-album-detail__cover {
    width: 72px;
  }

  .bee-album-detail__desc {
    display: none;
  }

  /* 375 宽度下「上传/选择」与「视图切换/排序」合计超宽，允许换行避免横向滚动 */
  .bee-album-detail__toolbar {
    height: auto;
    min-height: 48px;
    flex-wrap: wrap;
    row-gap: var(--sp-2);
    padding: var(--sp-2) 0;
  }

  .bee-album-detail__toolbar-right {
    margin-left: auto;
  }

  .bee-album-detail__sort {
    width: 132px;
  }

  .bee-album-detail__selection {
    left: var(--sp-3);
    right: var(--sp-3);
    bottom: calc(var(--tabbar-h) + var(--sp-2));
    height: auto;
    padding: var(--sp-3);
    flex-direction: column;
    align-items: stretch;
    gap: var(--sp-2);
  }

  .bee-album-detail__selection-actions {
    justify-content: space-between;
  }

  .bee-album-detail__body.is-list :deep(.bee-photo-grid) {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
