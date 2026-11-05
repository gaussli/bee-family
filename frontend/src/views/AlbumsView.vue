<script setup lang="ts">
/**
 * 相册列表（02 文档 §5.3）。
 *
 * <p>网格 4/3/2/1 列；末尾固定一张「未分类」特殊卡（不可删、不可编辑），
 * 点进去用 `?unclassified` 语义走照片网格的未分类筛选。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeEmpty from '@/components/base/BeeEmpty.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeImage from '@/components/base/BeeImage.vue'
import BeePageHeader from '@/components/base/BeePageHeader.vue'
import BeeSkeleton from '@/components/base/BeeSkeleton.vue'
import AlbumCard from '@/components/business/AlbumCard.vue'
import AlbumFormModal from '@/components/business/AlbumFormModal.vue'
import DeleteAlbumModal from '@/components/business/DeleteAlbumModal.vue'
import { useAlbumStore } from '@/stores/album'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { ApiError } from '@/api/http'
import { fileUrl } from '@/utils/fileUrl'
import type { Album } from '@/types/models'

const router = useRouter()
const albumStore = useAlbumStore()
const auth = useAuthStore()
const toast = useToast()

const formOpen = ref<boolean>(false)
const editing = ref<Album | null>(null)
const deleteOpen = ref<boolean>(false)
const deleting = ref<Album | null>(null)
const initialLoading = ref<boolean>(true)

const albums = computed<Album[]>(() => albumStore.items)
const unclassified = computed(() => albumStore.unclassified)
const unclassifiedCover = computed<string>(() => fileUrl(unclassified.value.coverThumbKey, 'thumb'))

async function load(): Promise<void> {
  initialLoading.value = true
  try {
    await albumStore.fetchList(true)
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '相册加载失败')
  } finally {
    initialLoading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  formOpen.value = true
}

function openEdit(album: Album): void {
  editing.value = album
  formOpen.value = true
}

function openDelete(album: Album): void {
  deleting.value = album
  deleteOpen.value = true
}

function openUnclassified(): void {
  void router.push({ name: 'album-detail', params: { id: 'unclassified' } })
}

function onSaved(): void {
  void load()
}

function onRemoved(): void {
  void load()
}

onMounted(load)
</script>

<template>
  <div class="bee-albums-view">
    <BeePageHeader title="相册" :count="albums.length" description="把照片按主题收成一册，未分类的也能随时整理。">
      <template #actions>
        <BeeButton @click="openCreate">
          <template #icon><BeeIcon name="plus" :size="16" /></template>
          新建相册
        </BeeButton>
      </template>
    </BeePageHeader>

    <BeeSkeleton v-if="initialLoading" variant="card" :count="4" />

    <template v-else>
      <BeeEmpty
        v-if="albums.length === 0 && unclassified.count === 0"
        variant="no-album"
        title="还没有相册"
        description="新建第一本相册，把散落的照片收起来。"
        action-text="新建第一本相册"
        @action="openCreate"
      />

      <div v-else class="bee-albums-view__grid">
        <AlbumCard
          v-for="album in albums"
          :key="album.id"
          :album="album"
          :editable="auth.isLoggedIn"
          @edit="openEdit"
          @remove="openDelete"
        />

        <button class="bee-albums-view__unclassified" type="button" @click="openUnclassified">
          <span class="bee-albums-view__unclassified-cover">
            <BeeImage
              v-if="unclassifiedCover"
              :src="unclassifiedCover"
              alt="未分类照片"
              ratio="4/3"
              radius="0"
            />
            <span v-else class="bee-albums-view__unclassified-placeholder">
              <BeeIcon name="hex" :size="32" :stroke-width="1.2" />
            </span>
          </span>
          <span class="bee-albums-view__unclassified-info">
            <strong>未分类</strong>
            <small>{{ unclassified.count }} 张 · 散落的照片都在这里</small>
          </span>
        </button>
      </div>
    </template>

    <AlbumFormModal v-model:open="formOpen" :album="editing" @saved="onSaved" />
    <DeleteAlbumModal
      v-if="deleting"
      v-model:open="deleteOpen"
      :album="deleting"
      @removed="onRemoved"
    />
  </div>
</template>

<style scoped>
.bee-albums-view__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--grid-gutter);
}

.bee-albums-view__unclassified {
  display: flex;
  flex-direction: column;
  border-radius: var(--r-lg);
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  text-align: left;
  transition:
    transform var(--dur-base) var(--ease-out),
    box-shadow var(--dur-base) var(--ease-out);
}

.bee-albums-view__unclassified:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.bee-albums-view__unclassified-cover {
  display: block;
  background: var(--honey-50);
}

.bee-albums-view__unclassified-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 4 / 3;
  color: var(--honey-300);
}

.bee-albums-view__unclassified-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--sp-4);
}

.bee-albums-view__unclassified-info strong {
  font-size: var(--fs-h4);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-albums-view__unclassified-info small {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

@media (min-width: 640px) {
  .bee-albums-view__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1024px) {
  .bee-albums-view__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 1280px) {
  .bee-albums-view__grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 639px) {
  .bee-albums-view__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
