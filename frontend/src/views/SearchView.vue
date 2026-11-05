<script setup lang="ts">
/**
 * 全局搜索（02 文档 §5.9）。
 *
 * <p>防抖 300ms；关键词同步到 URL（`?q=`）以便刷新/分享后仍可复现。
 * 高亮由 `highlight()` 完成：先做 HTML 转义再包 `<mark>`，防止 XSS
 * （搜索结果里的标题可能包含用户输入的任意字符）。</p>
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeEmpty from '@/components/base/BeeEmpty.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeImage from '@/components/base/BeeImage.vue'
import BeeSkeleton from '@/components/base/BeeSkeleton.vue'
import BeeTag from '@/components/base/BeeTag.vue'
import SearchGroup from '@/components/business/SearchGroup.vue'
import * as searchApi from '@/api/search'
import * as albumApi from '@/api/album'
import { ApiError } from '@/api/http'
import { useToast } from '@/composables/useToast'
import { debounce } from '@/utils/debounce'
import { fileUrl } from '@/utils/fileUrl'
import { formatDate, highlight } from '@/utils/format'
import type { Album, Tag } from '@/types/models'
import type { SearchGroup as SearchGroupModel } from '@/types/search'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const keyword = ref<string>(typeof route.query.q === 'string' ? route.query.q : '')
const result = ref<SearchGroupModel | null>(null)
const loading = ref<boolean>(false)
const searched = ref<boolean>(false)
const tags = ref<Tag[]>([])
const albums = ref<Album[]>([])

const inputRef = ref<HTMLInputElement | null>(null)

const trimmed = computed<string>(() => keyword.value.trim())
const hasQuery = computed<boolean>(() => trimmed.value.length > 0)

const totalCount = computed<number>(() => result.value?.totalCount ?? 0)

const photos = computed(() => result.value?.photos.items ?? [])
const albumsResult = computed(() => result.value?.albums.items ?? [])
const milestonesResult = computed(() => result.value?.milestones.items ?? [])

/** 与后端保持一致的转义后高亮。 */
function mark(text: string | null): string {
  if (!text) return ''
  return highlight(text, trimmed.value)
}

const runSearch = debounce(async (): Promise<void> => {
  if (!trimmed.value) {
    result.value = null
    searched.value = false
    return
  }
  loading.value = true
  try {
    result.value = await searchApi.search(trimmed.value, 'all', 20)
    searched.value = true
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '搜索失败，请重试')
  } finally {
    loading.value = false
  }
}, 300)

function syncUrl(): void {
  const query = trimmed.value ? { q: trimmed.value } : {}
  void router.replace({ name: 'search', query })
}

function onInput(): void {
  void runSearch()
  syncUrl()
}

function clearKeyword(): void {
  keyword.value = ''
  result.value = null
  searched.value = false
  void router.replace({ name: 'search' })
  inputRef.value?.focus()
}

function useTag(name: string): void {
  keyword.value = name
  onInput()
}

function viewAll(type: 'photo' | 'album' | 'milestone'): void {
  toast.info(type === 'photo' ? '照片结果已在下方全部展示' : '该类型结果已全部展示')
}

async function loadInitial(): Promise<void> {
  try {
    const [tagList, albumList] = await Promise.all([
      searchApi.listTags(),
      albumApi.listAlbums().catch(() => ({ items: [], unclassified: { count: 0, coverPhotoId: null, coverThumbKey: null } })),
    ])
    tags.value = tagList.slice(0, 12)
    albums.value = (albumList.items ?? []).slice(0, 4)
  } catch {
    tags.value = []
    albums.value = []
  }
}

watch(
  () => route.query.q,
  (value) => {
    const next = typeof value === 'string' ? value : ''
    if (next === keyword.value) return
    keyword.value = next
    void runSearch()
  },
)

onMounted(async () => {
  await loadInitial()
  inputRef.value?.focus()
  if (hasQuery.value) void runSearch()
})

onBeforeUnmount(() => {
  runSearch.cancel()
})
</script>

<template>
  <div class="bee-search-view">
    <div class="bee-search-view__box">
      <BeeIcon class="bee-search-view__icon" name="search" :size="20" />
      <input
        ref="inputRef"
        v-model="keyword"
        class="bee-search-view__input"
        type="search"
        placeholder="搜索照片标题、地点、相册名、里程碑"
        aria-label="搜索"
        @input="onInput"
      />
      <span v-if="loading" class="bee-search-view__spinner" aria-hidden="true" />
      <button v-if="keyword" class="bee-search-view__clear" type="button" aria-label="清空" @click="clearKeyword">
        <BeeIcon name="close" :size="16" />
      </button>
    </div>

    <p v-if="searched && hasQuery" class="bee-search-view__count">找到 {{ totalCount }} 条结果</p>

    <BeeSkeleton v-if="loading && !result" variant="block" :count="3" height="88px" />

    <!-- 初始态：最近标签 + 最近相册 -->
    <template v-else-if="!hasQuery">
      <section v-if="tags.length > 0" class="bee-search-view__section">
        <h2 class="bee-search-view__section-title">最近标签</h2>
        <div class="bee-search-view__tags">
          <BeeTag v-for="tag in tags" :key="tag.id" variant="outline" clickable @click="useTag(tag.name)">
            {{ tag.name }}
            <span class="bee-search-view__tag-count">{{ tag.photoCount }}</span>
          </BeeTag>
        </div>
      </section>

      <section v-if="albums.length > 0" class="bee-search-view__section">
        <h2 class="bee-search-view__section-title">最近相册</h2>
        <div class="bee-search-view__albums">
          <RouterLink
            v-for="album in albums"
            :key="album.id"
            class="bee-search-view__album"
            :to="`/albums/${album.id}`"
          >
            <BeeImage
              :src="fileUrl(album.coverThumbKey, 'thumb')"
              :alt="`${album.name} 封面`"
              ratio="1/1"
              radius="var(--r-sm)"
            />
            <span class="bee-search-view__album-name">{{ album.name }}</span>
            <span class="bee-search-view__album-meta">{{ album.photoCount }} 张</span>
          </RouterLink>
        </div>
      </section>

      <BeeEmpty
        v-if="tags.length === 0 && albums.length === 0"
        variant="no-result"
        title="搜点什么吧"
        description="输入关键词，比如「海边」「生日」「第一次走路」。"
      />
    </template>

    <!-- 空结果 -->
    <BeeEmpty
      v-else-if="searched && totalCount === 0 && !loading"
      variant="no-result"
      :title="`没找到「${trimmed}」`"
      description="试试更短的关键词，或按标签搜索。"
      action-text="清空重新搜索"
      @action="clearKeyword"
    />

    <!-- 结果 -->
    <template v-else-if="result && totalCount > 0">
      <SearchGroup
        v-if="photos.length > 0"
        title="照片"
        :total="result.photos.total"
        :has-more="result.photos.hasMore"
        @more="viewAll('photo')"
      >
        <div class="bee-search-view__photos">
          <RouterLink
            v-for="photo in photos"
            :key="photo.id"
            class="bee-search-view__photo"
            :to="photo.albumId ? `/albums/${photo.albumId}` : '/timeline'"
            :aria-label="photo.title ?? '照片'"
          >
            <BeeImage
              :src="fileUrl(photo.thumbKey, 'thumb')"
              :alt="photo.title ?? '照片'"
              ratio="1/1"
              radius="var(--r-sm)"
            />
          </RouterLink>
        </div>
      </SearchGroup>

      <SearchGroup
        v-if="albumsResult.length > 0"
        title="相册"
        :total="result.albums.total"
        :has-more="result.albums.hasMore"
        @more="viewAll('album')"
      >
        <div class="bee-search-view__albums">
          <RouterLink
            v-for="album in albumsResult"
            :key="album.id"
            class="bee-search-view__album"
            :to="`/albums/${album.id}`"
          >
            <BeeImage
              :src="fileUrl(album.coverThumbKey, 'thumb')"
              :alt="`${album.name} 封面`"
              ratio="1/1"
              radius="var(--r-sm)"
            />
            <span class="bee-search-view__album-name" v-html="mark(album.name)" />
            <span class="bee-search-view__album-meta">{{ album.photoCount }} 张</span>
          </RouterLink>
        </div>
      </SearchGroup>

      <SearchGroup
        v-if="milestonesResult.length > 0"
        title="里程碑"
        :total="result.milestones.total"
        :has-more="result.milestones.hasMore"
        @more="viewAll('milestone')"
      >
        <ul class="bee-search-view__milestones">
          <li v-for="milestone in milestonesResult" :key="milestone.id" class="bee-search-view__milestone">
            <BeeIcon name="heart" :size="14" />
            <span class="bee-search-view__milestone-title" v-html="mark(milestone.title)" />
            <span class="bee-search-view__milestone-date">{{ formatDate(milestone.achievedAt) }}</span>
          </li>
        </ul>
      </SearchGroup>
    </template>

    <BeeButton v-if="searched && hasQuery" class="bee-search-view__reset" variant="ghost" size="sm" @click="clearKeyword">
      清空关键词
    </BeeButton>
  </div>
</template>

<style scoped>
.bee-search-view__box {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  height: 48px;
  padding: 0 var(--sp-4);
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--r-md);
  transition: border-color var(--dur-fast) var(--ease-out);
}

.bee-search-view__box:focus-within {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-focus);
}

.bee-search-view__icon {
  color: var(--honey-500);
  flex: none;
}

.bee-search-view__input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  background: transparent;
  font-family: var(--font-sans);
  font-size: var(--fs-body-lg);
  color: var(--text-primary);
  outline: none;
}

.bee-search-view__input::placeholder {
  color: var(--text-tertiary);
}

.bee-search-view__spinner {
  width: 16px;
  height: 16px;
  flex: none;
  border: 2px solid var(--warm-300);
  border-top-color: var(--honey-500);
  border-radius: var(--r-full);
  animation: bee-spin 900ms linear infinite;
}

.bee-search-view__clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex: none;
  border-radius: var(--r-full);
  color: var(--text-tertiary);
}

.bee-search-view__clear:hover {
  background: var(--warm-100);
  color: var(--text-primary);
}

.bee-search-view__count {
  margin: var(--sp-4) 0 var(--sp-6);
  font-size: var(--fs-body);
  color: var(--text-secondary);
}

.bee-search-view__section {
  margin-bottom: var(--sp-8);
}

.bee-search-view__section-title {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-search-view__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.bee-search-view__tag-count {
  margin-left: 4px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.bee-search-view__photos {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-2);
}

.bee-search-view__photo {
  display: block;
  border-radius: var(--r-sm);
  overflow: hidden;
}

.bee-search-view__photo:hover {
  box-shadow: 0 0 0 2px var(--honey-500);
}

.bee-search-view__albums {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-3);
}

.bee-search-view__album {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-2);
  border-radius: var(--r-md);
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.bee-search-view__album:hover {
  background: var(--honey-50);
}

.bee-search-view__album :deep(.bee-image) {
  width: 56px;
  flex: none;
}

.bee-search-view__album-name {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-primary);
}

.bee-search-view__album-meta {
  margin-left: auto;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  white-space: nowrap;
}

.bee-search-view__milestones {
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.bee-search-view__milestone {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  height: 52px;
  padding: 0 var(--sp-4);
  border-bottom: 1px solid var(--border-subtle);
  color: var(--accent-coral);
}

.bee-search-view__milestone:last-child {
  border-bottom: 0;
}

.bee-search-view__milestone-title {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-primary);
}

.bee-search-view__milestone-date {
  margin-left: auto;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-search-view__reset {
  margin-top: var(--sp-6);
}

:deep(mark) {
  background: var(--honey-200);
  color: var(--text-primary);
  border-radius: 3px;
  padding: 0 2px;
}

@media (min-width: 768px) {
  .bee-search-view__photos {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }

  .bee-search-view__photo {
    aspect-ratio: 1 / 1;
  }
}

@media (max-width: 767px) {
  .bee-search-view__albums {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
