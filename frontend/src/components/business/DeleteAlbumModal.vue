<script setup lang="ts">
/**
 * 删除相册确认。
 *
 * <p>必须先选处置策略再二次确认：`move` 把照片移到「未分类」（推荐，默认选中），
 * `delete` 连带删除照片。危险操作用 `danger` 变体渲染主按钮。</p>
 */
import { computed, ref, watch } from 'vue'
import BeeModal from '@/components/base/BeeModal.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import { useAlbumStore } from '@/stores/album'
import type { PhotoStrategy } from '@/api/album'
import { ApiError } from '@/api/http'
import { useToast } from '@/composables/useToast'
import type { Album } from '@/types/models'

interface Props {
  open: boolean
  album: Album
}

const props = defineProps<Props>()

const emit = defineEmits<{ 'update:open': [value: boolean]; removed: [albumId: number] }>()

const albumStore = useAlbumStore()
const toast = useToast()

const strategy = ref<PhotoStrategy>('move')
const loading = ref<boolean>(false)

const total = computed<number>(() => props.album.photoCount)

const options: { value: PhotoStrategy; title: string; hint: string; recommend?: boolean }[] = [
  { value: 'move', title: '照片移到未分类', hint: '照片一张不丢，之后可以再整理', recommend: true },
  { value: 'delete', title: '一并删除这些照片', hint: '照片会从家庭相册中永久移除，不可恢复' },
]

watch(
  () => props.open,
  (value) => {
    if (value) strategy.value = 'move'
  },
)

async function onConfirm(): Promise<void> {
  loading.value = true
  try {
    const result = await albumStore.remove(props.album.id, strategy.value)
    const message =
      strategy.value === 'move'
        ? `已删除相册，${result.movedPhotos} 张照片已移到未分类`
        : `已删除相册与 ${result.deletedPhotos} 张照片`
    toast.success(message)
    emit('removed', props.album.id)
    emit('update:open', false)
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '删除失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <BeeModal
    :open="open"
    title="删除相册"
    size="sm"
    danger
    :loading="loading"
    confirm-text="删除"
    @update:open="emit('update:open', $event)"
    @confirm="onConfirm"
  >
    <p class="bee-delete-album__lead">
      将要删除《{{ album.name }}》，其中共有 <strong>{{ total }}</strong> 张照片。请选择处理方式：
    </p>

    <div class="bee-delete-album__options" role="radiogroup" aria-label="照片处理方式">
      <label
        v-for="option in options"
        :key="option.value"
        class="bee-delete-album__option"
        :class="{ 'is-active': strategy === option.value }"
      >
        <input v-model="strategy" class="bee-delete-album__radio" type="radio" :value="option.value" />
        <span class="bee-delete-album__option-main">
          <span class="bee-delete-album__option-title">
            {{ option.title }}
            <span v-if="option.recommend" class="bee-delete-album__badge">推荐</span>
          </span>
          <span class="bee-delete-album__option-hint">{{ option.hint }}</span>
        </span>
        <BeeIcon v-if="strategy === option.value" class="bee-delete-album__check" name="check" :size="16" />
      </label>
    </div>
  </BeeModal>
</template>

<style scoped>
.bee-delete-album__lead {
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
}

.bee-delete-album__lead strong {
  color: var(--text-primary);
  font-weight: 600;
}

.bee-delete-album__options {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
}

.bee-delete-album__option {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  padding: var(--sp-3);
  border: 1px solid var(--border-default);
  border-radius: var(--r-md);
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-out),
    background-color var(--dur-fast) var(--ease-out);
}

.bee-delete-album__option:hover {
  border-color: var(--border-strong);
}

.bee-delete-album__option.is-active {
  border-color: var(--honey-400);
  background: var(--honey-50);
}

.bee-delete-album__radio {
  margin-top: 3px;
  accent-color: var(--honey-500);
}

.bee-delete-album__option-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}

.bee-delete-album__option-title {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-primary);
}

.bee-delete-album__badge {
  padding: 0 6px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  border-radius: var(--r-sm);
  background: var(--honey-100);
  color: var(--text-brand);
  font-size: 11px;
  font-weight: 500;
}

.bee-delete-album__option-hint {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.bee-delete-album__check {
  color: var(--honey-600);
  margin-top: 2px;
}
</style>
