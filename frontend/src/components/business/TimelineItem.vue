<script setup lang="ts">
/**
 * 时间轴条目（02 文档 §4.12，核心组件）。
 *
 * <p>三态渲染：照片（4:3 卡片）/ 里程碑（珊瑚竖条卡）/ 成长记录（紧凑行）。
 * 单列布局，左侧时间列 + 脊柱由 `TimelineGroup` 统一绘制，本组件只负责内容区。</p>
 */
import { computed } from 'vue'
import BeeHexBadge from '@/components/base/BeeHexBadge.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeImage from '@/components/base/BeeImage.vue'
import { fileUrl } from '@/utils/fileUrl'
import { formatMonthDay, formatWeekday } from '@/utils/format'
import type { TimelineItem as TimelineItemModel } from '@/types/timeline'

interface Props {
  item: TimelineItemModel
  index: number
}

const props = defineProps<Props>()

const emit = defineEmits<{ openPhoto: [photoId: number]; openMilestone: [milestoneId: number] }>()

const photoUrl = computed<string>(() => fileUrl(props.item.photo?.previewKey, 'preview'))
const milestoneThumb = computed<string>(() => fileUrl(props.item.milestone?.photoThumbKey, 'thumb'))

/** 首屏 5 条依次入场（步长 40ms），更靠后的条目不再延迟，避免滚动时「追赶」感。 */
const staggerDelay = computed<string>(() => (props.index < 5 ? `${props.index * 40}ms` : '0ms'))

const dayLabel = computed<string>(() => formatMonthDay(props.item.sortTime))
const weekdayLabel = computed<string>(() => formatWeekday(props.item.sortTime))

const timeColumn = computed<{ day: string; weekday: string }>(() => ({
  day: dayLabel.value,
  weekday: weekdayLabel.value,
}))

function openPhoto(): void {
  if (props.item.photo) emit('openPhoto', props.item.photo.id)
}

function openMilestone(): void {
  if (props.item.milestone) emit('openMilestone', props.item.milestone.id)
}
</script>

<template>
  <article
    class="bee-timeline-item"
    :class="`bee-timeline-item--${item.type}`"
    :style="{ animationDelay: staggerDelay }"
  >
    <div class="bee-timeline-item__time" aria-hidden="true">
      <span class="bee-timeline-item__day">{{ timeColumn.day }}</span>
      <span class="bee-timeline-item__weekday">{{ timeColumn.weekday }}</span>
    </div>

    <span class="bee-timeline-item__node" :class="{ 'is-milestone': item.type === 'milestone' }" aria-hidden="true" />

    <div class="bee-timeline-item__body">
      <!-- 照片 -->
      <button v-if="item.type === 'photo' && item.photo" class="bee-timeline-item__photo" type="button" @click="openPhoto">
        <BeeImage
          v-if="photoUrl"
          :src="photoUrl"
          :alt="item.photo.title ?? '照片'"
          ratio="4/3"
          radius="var(--r-lg)"
          fit="cover"
        />
        <span class="bee-timeline-item__photo-meta">
          <span v-if="item.photo.title" class="bee-timeline-item__photo-title">{{ item.photo.title }}</span>
          <span v-if="item.photo.locationName" class="bee-timeline-item__photo-location">
            <BeeIcon name="map-pin" :size="12" />
            {{ item.photo.locationName }}
          </span>
        </span>
      </button>

      <!-- 里程碑 -->
      <button
        v-else-if="item.type === 'milestone' && item.milestone"
        class="bee-timeline-item__milestone"
        type="button"
        @click="openMilestone"
      >
        <BeeHexBadge :size="32" variant="coral" icon="heart" />
        <span class="bee-timeline-item__milestone-main">
          <strong class="bee-timeline-item__milestone-title">{{ item.milestone.title }}</strong>
          <span class="bee-timeline-item__milestone-meta">
            {{ item.milestone.achievedAt }}
            <template v-if="item.milestone.ageText"> · {{ item.milestone.ageText }}</template>
          </span>
          <span v-if="item.milestone.description" class="bee-timeline-item__milestone-desc">
            {{ item.milestone.description }}
          </span>
        </span>
        <BeeImage
          v-if="milestoneThumb"
          class="bee-timeline-item__milestone-thumb"
          :src="milestoneThumb"
          :alt="item.milestone.title"
          ratio="1/1"
          radius="var(--r-sm)"
        />
      </button>

      <!-- 成长记录 -->
      <div v-else-if="item.type === 'growth' && item.growth" class="bee-timeline-item__growth">
        <BeeHexBadge :size="24" variant="honey" icon="baby" />
        <span class="bee-timeline-item__growth-title">{{ item.growth.babyName }}的测量</span>
        <span class="bee-timeline-item__growth-values">
          <template v-if="item.growth.heightCm !== null">{{ item.growth.heightCm }} cm</template>
          <template v-if="item.growth.weightKg !== null"> · {{ item.growth.weightKg }} kg</template>
          <template v-if="item.growth.headCm !== null"> · {{ item.growth.headCm }} cm</template>
        </span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.bee-timeline-item {
  position: relative;
  display: grid;
  grid-template-columns: 64px 1fr;
  gap: var(--sp-4);
  padding-bottom: var(--sp-6);
  opacity: 0;
  animation: bee-fade-up var(--dur-slow) var(--ease-out) forwards;
}

.bee-timeline-item:last-child {
  padding-bottom: 0;
}

.bee-timeline-item__time {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  padding-top: 2px;
  text-align: right;
}

.bee-timeline-item__day {
  font-size: var(--fs-caption);
  font-weight: 500;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.bee-timeline-item__weekday {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-timeline-item__node {
  position: absolute;
  top: 4px;
  left: 71px;
  width: 10px;
  height: 10px;
  border-radius: var(--r-full);
  background: var(--bg-page);
  border: 2px solid var(--honey-500);
}

.bee-timeline-item__node.is-milestone {
  left: 70px;
  width: 12px;
  height: 14px;
  border: 0;
  border-radius: 0;
  background: var(--accent-coral);
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
}

.bee-timeline-item__body {
  min-width: 0;
  /* 左侧 24 让卡片与脊柱留出呼吸；右侧 40 让卡片宽度落在设计稿的 736（880-64-16-24-40） */
  padding-left: var(--sp-6);
  padding-right: 40px;
}

/* ---------- 照片 ---------- */
.bee-timeline-item__photo {
  display: block;
  width: 100%;
  padding: 0;
  border-radius: var(--r-lg);
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  text-align: left;
  transition: box-shadow var(--dur-base) var(--ease-out);
}

.bee-timeline-item__photo:hover {
  box-shadow: var(--shadow-md);
}

.bee-timeline-item__photo:hover :deep(.bee-image) {
  transform: scale(1.02);
}

.bee-timeline-item__photo :deep(.bee-image) {
  transition: transform var(--dur-base) var(--ease-out);
}

.bee-timeline-item__photo-meta {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
}

.bee-timeline-item__photo-title {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-timeline-item__photo-location {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  white-space: nowrap;
}

/* ---------- 里程碑 ---------- */
.bee-timeline-item__milestone {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  width: 100%;
  padding: var(--sp-5);
  padding-left: calc(var(--sp-5) + 3px);
  border-radius: var(--r-lg);
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
  text-align: left;
  transition: box-shadow var(--dur-base) var(--ease-out);
}

.bee-timeline-item__milestone::before {
  content: '';
  position: absolute;
  top: var(--sp-4);
  bottom: var(--sp-4);
  left: 0;
  width: 3px;
  border-radius: 2px;
  background: var(--accent-coral);
}

.bee-timeline-item__milestone:hover {
  box-shadow: var(--shadow-md);
}

.bee-timeline-item__milestone-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.bee-timeline-item__milestone-title {
  font-size: var(--fs-h4);
  line-height: var(--lh-h4);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-timeline-item__milestone-meta {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.bee-timeline-item__milestone-desc {
  margin-top: var(--sp-1);
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
}

.bee-timeline-item__milestone-thumb {
  width: 64px;
  flex: none;
}

/* ---------- 成长记录 ---------- */
.bee-timeline-item__growth {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-lg);
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
}

.bee-timeline-item__growth-title {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-primary);
}

.bee-timeline-item__growth-values {
  margin-left: auto;
  font-size: var(--fs-caption);
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 767px) {
  .bee-timeline-item {
    display: block;
    padding-bottom: var(--sp-5);
  }

  .bee-timeline-item__time,
  .bee-timeline-item__node {
    display: none;
  }

  .bee-timeline-item__body {
    padding-left: 0;
    padding-right: 0;
  }
}
</style>
