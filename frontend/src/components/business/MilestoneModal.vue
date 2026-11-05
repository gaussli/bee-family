<script setup lang="ts">
/**
 * 里程碑新建 / 编辑弹窗。
 *
 * <p>字段：标题、达成日期、描述、关联宝宝、可选配图。配图用「最近照片」简易选择器，
 * 避免再做一个完整的照片浏览弹层（MVP 范围控制）。</p>
 */
import { computed, ref, watch } from 'vue'
import BeeInput from '@/components/base/BeeInput.vue'
import BeeModal from '@/components/base/BeeModal.vue'
import BeeSelect from '@/components/base/BeeSelect.vue'
import BeeImage from '@/components/base/BeeImage.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import * as photoApi from '@/api/photo'
import * as milestoneApi from '@/api/milestone'
import { ApiError } from '@/api/http'
import { useToast } from '@/composables/useToast'
import { fileUrl } from '@/utils/fileUrl'
import { todayString } from '@/utils/format'
import type { Milestone, Photo } from '@/types/models'

interface Props {
  open: boolean
  milestone?: Milestone | null
  babyId?: number | null
  babies?: { id: number; name: string }[]
}

const props = withDefaults(defineProps<Props>(), {
  milestone: null,
  babyId: null,
  babies: () => [],
})

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: [milestone: Milestone]
}>()

const toast = useToast()

const title = ref<string>('')
const achievedAt = ref<string>('')
const description = ref<string>('')
const babyIdValue = ref<number | null>(null)
const photoId = ref<number | null>(null)
const titleError = ref<string>('')
const loading = ref<boolean>(false)
const pickerOpen = ref<boolean>(false)
const recentPhotos = ref<Photo[]>([])
const pickerLoading = ref<boolean>(false)

const isEdit = computed<boolean>(() => props.milestone !== null)

const babyOptions = computed(() =>
  props.babies.map((baby) => ({ label: baby.name, value: baby.id })),
)

const selectedPhoto = computed<Photo | null>(
  () => recentPhotos.value.find((photo) => photo.id === photoId.value) ?? null,
)

watch(
  () => props.open,
  async (value) => {
    if (!value) return
    title.value = props.milestone?.title ?? ''
    achievedAt.value = props.milestone?.achievedAt ?? todayString()
    description.value = props.milestone?.description ?? ''
    babyIdValue.value = props.milestone?.babyId ?? props.babyId ?? null
    photoId.value = props.milestone?.photoId ?? null
    titleError.value = ''
    pickerOpen.value = false
    await loadRecent()
  },
)

async function loadRecent(): Promise<void> {
  pickerLoading.value = true
  try {
    const page = await photoApi.listPhotos({ page: 0, size: 12, sort: 'takenAt_desc' })
    recentPhotos.value = page.items
  } catch {
    recentPhotos.value = []
  } finally {
    pickerLoading.value = false
  }
}

function choosePhoto(photo: Photo): void {
  photoId.value = photo.id
  pickerOpen.value = false
}

function clearPhoto(): void {
  photoId.value = null
}

async function onSubmit(): Promise<void> {
  const trimmed = title.value.trim()
  if (!trimmed) {
    titleError.value = '给这个瞬间起个名字吧'
    return
  }
  if (!achievedAt.value) {
    titleError.value = '请选择达成日期'
    return
  }
  titleError.value = ''
  loading.value = true
  try {
    const body = {
      babyId: babyIdValue.value,
      title: trimmed,
      description: description.value.trim() || null,
      achievedAt: achievedAt.value,
      photoId: photoId.value,
    }
    const saved = props.milestone
      ? await milestoneApi.updateMilestone(props.milestone.id, body)
      : await milestoneApi.createMilestone(body)
    toast.success(isEdit.value ? '里程碑已更新' : '里程碑已记录')
    emit('saved', saved)
    emit('update:open', false)
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '保存失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <BeeModal
    :open="open"
    :title="isEdit ? '编辑里程碑' : '记录里程碑'"
    size="md"
    :loading="loading"
    :confirm-text="isEdit ? '保存' : '记录'"
    @update:open="emit('update:open', $event)"
    @confirm="onSubmit"
  >
    <div class="bee-milestone-form">
      <BeeInput v-model="title" label="标题" placeholder="例如：第一次独立走路" required :error="titleError" :maxlength="40" />
      <BeeInput v-model="achievedAt" type="date" label="达成日期" required />
      <BeeSelect
        v-if="babyOptions.length > 0"
        v-model="babyIdValue"
        label="宝宝"
        :options="babyOptions"
        placeholder="请选择宝宝"
        clearable
      />
      <BeeInput v-model="description" label="描述（选填）" placeholder="记录当时的细节" :rows="3" :maxlength="200" />

      <div class="bee-milestone-form__photo">
        <span class="bee-milestone-form__label">配图（选填）</span>
        <div v-if="photoId" class="bee-milestone-form__chosen">
          <BeeImage
            :src="fileUrl(selectedPhoto?.thumbKey ?? null, 'thumb')"
            :alt="selectedPhoto?.title ?? '已选配图'"
            ratio="1/1"
            radius="var(--r-md)"
          />
          <button class="bee-milestone-form__remove" type="button" aria-label="移除配图" @click="clearPhoto">
            <BeeIcon name="close" :size="12" />
          </button>
        </div>
        <button v-else class="bee-milestone-form__picker-btn" type="button" @click="pickerOpen = !pickerOpen">
          <BeeIcon name="camera" :size="16" />
          选择一张照片
        </button>
      </div>

      <div v-if="pickerOpen" class="bee-milestone-form__picker">
        <p v-if="pickerLoading" class="bee-milestone-form__picker-hint">加载中…</p>
        <p v-else-if="recentPhotos.length === 0" class="bee-milestone-form__picker-hint">还没有照片可选</p>
        <div v-else class="bee-milestone-form__picker-grid">
          <button
            v-for="photo in recentPhotos"
            :key="photo.id"
            class="bee-milestone-form__picker-cell"
            type="button"
            @click="choosePhoto(photo)"
          >
            <BeeImage :src="fileUrl(photo.thumbKey, 'thumb')" :alt="photo.title ?? '照片'" ratio="1/1" radius="var(--r-sm)" />
          </button>
        </div>
      </div>
    </div>
  </BeeModal>
</template>

<style scoped>
.bee-milestone-form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.bee-milestone-form__photo {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.bee-milestone-form__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.bee-milestone-form__chosen {
  position: relative;
  width: 88px;
}

.bee-milestone-form__remove {
  position: absolute;
  top: -6px;
  right: -6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--r-full);
  background: var(--bg-elevated);
  color: var(--text-secondary);
  box-shadow: var(--shadow-sm);
}

.bee-milestone-form__picker-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  align-self: flex-start;
  height: 36px;
  padding: 0 var(--sp-3);
  border: 1px dashed var(--border-strong);
  border-radius: var(--r-md);
  font-size: var(--fs-body);
  color: var(--text-secondary);
}

.bee-milestone-form__picker-btn:hover {
  border-color: var(--honey-400);
  color: var(--text-brand);
}

.bee-milestone-form__picker {
  padding: var(--sp-2);
  border-radius: var(--r-md);
  background: var(--bg-sunken);
}

.bee-milestone-form__picker-hint {
  padding: var(--sp-3);
  text-align: center;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-milestone-form__picker-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: var(--sp-1);
}

.bee-milestone-form__picker-cell {
  border-radius: var(--r-sm);
  overflow: hidden;
}

.bee-milestone-form__picker-cell:hover {
  box-shadow: 0 0 0 2px var(--honey-500);
}
</style>
