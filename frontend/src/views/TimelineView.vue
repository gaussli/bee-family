<script setup lang="ts">
/**
 * 时间轴首页（02 文档 §5.2）。
 *
 * <p>数据来源 `GET /api/timeline?page&size`，返回按月分组的数据；分页按「组」下发，
 * 同一月份可能跨页重复出现，因此追加时需要按 `month` 合并而不是直接 push。</p>
 *
 * <p>滚动：无限滚动哨兵（`rootMargin: 400px`，每次 24 条）；灯箱的照片列表由所有分组
 * 展平得到，保证在时间轴里也能左右翻页。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeEmpty from '@/components/base/BeeEmpty.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeePageHeader from '@/components/base/BeePageHeader.vue'
import BeeSkeleton from '@/components/base/BeeSkeleton.vue'
import StatBar from '@/components/business/StatBar.vue'
import TimelineGroup from '@/components/business/TimelineGroup.vue'
import Lightbox from '@/components/business/Lightbox.vue'
import MilestoneModal from '@/components/business/MilestoneModal.vue'
import * as timelineApi from '@/api/timeline'
import * as statsApi from '@/api/stats'
import { ApiError } from '@/api/http'
import { useFamilyStore } from '@/stores/family'
import { useToast } from '@/composables/useToast'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import type { Overview, Photo } from '@/types/models'
import type { TimelineGroup as TimelineGroupModel } from '@/types/timeline'

const PAGE_SIZE = 24

const router = useRouter()
const family = useFamilyStore()
const toast = useToast()

const groups = ref<TimelineGroupModel[]>([])
const stats = ref<Overview | null>(null)
const page = ref<number>(0)
const hasMore = ref<boolean>(false)
const totalPhotos = ref<number>(0)
const loading = ref<boolean>(false)
const initialLoading = ref<boolean>(true)
const loadError = ref<string>('')

const lightboxOpen = ref<boolean>(false)
const lightboxIndex = ref<number>(0)
const milestoneOpen = ref<boolean>(false)

/** 所有分组内的照片按时间顺序展平，供灯箱翻页。 */
const flatPhotos = computed<Photo[]>(() =>
  groups.value.flatMap((group) => group.items.map((item) => item.photo).filter((photo): photo is Photo => photo !== null)),
)

const timelineLabel = computed<string>(() => {
  const count = stats.value?.photoCount ?? totalPhotos.value
  return count > 0 ? `共 ${count} 张记忆` : '还没有记忆'
})

const babiesOptions = computed(() => family.babies.map((baby) => ({ id: baby.id, name: baby.name })))

async function loadFirst(): Promise<void> {
  initialLoading.value = true
  loadError.value = ''
  try {
    const result = await timelineApi.getTimeline(0, PAGE_SIZE)
    groups.value = result.groups
    page.value = result.page
    hasMore.value = result.hasMore
    totalPhotos.value = result.total
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '时间轴加载失败'
  } finally {
    initialLoading.value = false
  }
}

async function loadMore(): Promise<void> {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const next = page.value + 1
    const result = await timelineApi.getTimeline(next, PAGE_SIZE)
    mergeGroups(result.groups)
    page.value = result.page
    hasMore.value = result.hasMore
    totalPhotos.value = result.total
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '加载更多失败')
  } finally {
    loading.value = false
  }
}

/** 按月份合并：已存在的月份把新条目插到尾部，不存在则追加分组。 */
function mergeGroups(incoming: TimelineGroupModel[]): void {
  const next = [...groups.value]
  for (const group of incoming) {
    const existing = next.find((item) => item.month === group.month)
    if (existing) {
      const seen = new Set(existing.items.map((item) => `${item.type}-${item.id}`))
      existing.items = [...existing.items, ...group.items.filter((item) => !seen.has(`${item.type}-${item.id}`))]
    } else {
      next.push(group)
    }
  }
  groups.value = next
}

const { sentinel } = useInfiniteScroll(
  () => hasMore.value && !loading.value && !initialLoading.value,
  () => {
    void loadMore()
  },
  '400px',
)

function openPhoto(photoId: number): void {
  const index = flatPhotos.value.findIndex((photo) => photo.id === photoId)
  if (index < 0) return
  lightboxIndex.value = index
  lightboxOpen.value = true
}

function openMilestone(): void {
  milestoneOpen.value = true
}

function goUpload(): void {
  router.push({ name: 'timeline' })
  toast.info('点击右上角「上传照片」开始上传')
}

async function loadStats(): Promise<void> {
  try {
    stats.value = await statsApi.getOverview()
  } catch {
    stats.value = null
  }
}

onMounted(async () => {
  await Promise.all([loadFirst(), loadStats()])
})
</script>

<template>
  <div class="bee-timeline-view">
    <BeePageHeader title="时间轴" :description="timelineLabel">
      <template #actions>
        <BeeButton variant="secondary" @click="openMilestone">
          <template #icon><BeeIcon name="heart" :size="16" /></template>
          记录里程碑
        </BeeButton>
      </template>
    </BeePageHeader>

    <StatBar v-if="stats" :stats="stats" class="bee-timeline-view__stats" />

    <div v-if="loadError" class="bee-timeline-view__error">
      <span>{{ loadError }}</span>
      <BeeButton variant="ghost" size="sm" @click="loadFirst">重试</BeeButton>
    </div>

    <BeeSkeleton v-if="initialLoading" variant="timeline" :count="3" class="bee-timeline-view__skeleton" />

    <template v-else>
      <BeeEmpty
        v-if="groups.length === 0"
        variant="no-photo"
        title="还没有记忆"
        description="上传第一批照片，时间轴就会从今天开始生长。"
        action-text="上传第一批照片"
        @action="goUpload"
      />

      <div v-else class="bee-timeline-view__stream">
        <TimelineGroup
          v-for="group in groups"
          :key="group.month"
          :group="group"
          @open-photo="openPhoto"
          @open-milestone="openMilestone"
        />

        <div ref="sentinel" class="bee-timeline-view__sentinel" aria-hidden="true" />

        <p v-if="loading" class="bee-timeline-view__loading">
          <span class="bee-timeline-view__dot" />
          <span class="bee-timeline-view__dot" />
          <span class="bee-timeline-view__dot" />
        </p>
        <p v-else-if="!hasMore" class="bee-timeline-view__end">
          没有更多了 · 共 {{ totalPhotos }} 张
        </p>
      </div>
    </template>

    <Lightbox
      :photos="flatPhotos"
      :start-index="lightboxIndex"
      :open="lightboxOpen"
      @update:open="lightboxOpen = $event"
    />

    <MilestoneModal
      :open="milestoneOpen"
      :baby-id="family.activeBabyId"
      :babies="babiesOptions"
      @update:open="milestoneOpen = $event"
      @saved="loadFirst"
    />
  </div>
</template>

<style scoped>
.bee-timeline-view__stats {
  margin-bottom: var(--sp-8);
}

.bee-timeline-view__error {
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

.bee-timeline-view__skeleton {
  max-width: 880px;
  margin: 0 auto;
}

.bee-timeline-view__stream {
  max-width: 880px;
  margin: 0 auto;
}

.bee-timeline-view__sentinel {
  height: 1px;
}

.bee-timeline-view__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: var(--sp-6) 0;
}

.bee-timeline-view__dot {
  width: 6px;
  height: 6px;
  border-radius: var(--r-full);
  background: var(--honey-500);
  animation: bee-dot 1.2s var(--ease-in-out) infinite;
}

.bee-timeline-view__dot:nth-child(2) {
  animation-delay: 120ms;
}

.bee-timeline-view__dot:nth-child(3) {
  animation-delay: 240ms;
}

.bee-timeline-view__end {
  padding: var(--sp-6) 0;
  text-align: center;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

@media (max-width: 767px) {
  .bee-timeline-view__stats {
    margin-bottom: var(--sp-5);
  }
}
</style>
