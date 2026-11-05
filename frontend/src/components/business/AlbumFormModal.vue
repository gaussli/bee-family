<script setup lang="ts">
/**
 * 相册新建 / 编辑弹窗。
 *
 * <p>新建与编辑复用同一组件：`album` 为 `null` 时是新建。名称重复（40905）
 * 会把错误落到「名称」字段上，而不是弹 Toast —— 符合 02 文档 §5.1 的字段级错误规范。</p>
 */
import { computed, ref, watch } from 'vue'
import BeeInput from '@/components/base/BeeInput.vue'
import BeeModal from '@/components/base/BeeModal.vue'
import { useAlbumStore } from '@/stores/album'
import { ApiError } from '@/api/http'
import { useToast } from '@/composables/useToast'
import type { Album } from '@/types/models'

interface Props {
  open: boolean
  album?: Album | null
}

const props = withDefaults(defineProps<Props>(), {
  album: null,
})

const emit = defineEmits<{ 'update:open': [value: boolean]; saved: [album: Album] }>()

const albumStore = useAlbumStore()
const toast = useToast()

const name = ref<string>('')
const description = ref<string>('')
const nameError = ref<string>('')
const loading = ref<boolean>(false)

const isEdit = computed<boolean>(() => props.album !== null)
const title = computed<string>(() => (isEdit.value ? '编辑相册' : '新建相册'))

watch(
  () => props.open,
  (value) => {
    if (!value) return
    name.value = props.album?.name ?? ''
    description.value = props.album?.description ?? ''
    nameError.value = ''
  },
)

function validate(): boolean {
  const trimmed = name.value.trim()
  if (!trimmed) {
    nameError.value = '相册名称不能为空'
    return false
  }
  if (trimmed.length > 40) {
    nameError.value = '相册名称最多 40 个字'
    return false
  }
  nameError.value = ''
  return true
}

async function onSubmit(): Promise<void> {
  if (!validate()) return
  loading.value = true
  try {
    const body = { name: name.value.trim(), description: description.value.trim() || null }
    const saved = props.album
      ? await albumStore.update(props.album.id, body)
      : await albumStore.create(body)
    toast.success(isEdit.value ? '相册已更新' : '相册已创建')
    emit('saved', saved)
    emit('update:open', false)
  } catch (error) {
    if (error instanceof ApiError && error.code === 40905) {
      nameError.value = '已有同名相册，换一个名字吧'
      return
    }
    toast.danger(error instanceof ApiError ? error.message : '保存失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <BeeModal
    :open="open"
    :title="title"
    size="sm"
    :loading="loading"
    :confirm-text="isEdit ? '保存' : '创建'"
    @update:open="emit('update:open', $event)"
    @confirm="onSubmit"
  >
    <div class="bee-album-form">
      <BeeInput
        v-model="name"
        label="相册名称"
        placeholder="例如：青岛海边"
        required
        :error="nameError"
        :maxlength="40"
        autofocus
      />
      <BeeInput
        v-model="description"
        label="描述（选填）"
        placeholder="记录一下这本相册的故事"
        :rows="3"
        :maxlength="200"
      />
    </div>
  </BeeModal>
</template>

<style scoped>
.bee-album-form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}
</style>
