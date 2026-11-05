<script setup lang="ts">
/**
 * 家庭成员行（02 文档 §5.8）。
 *
 * <p>52px 高，圆形头像 + 昵称 + 角色 Chip + 加入时间。`canRemove` 时在右侧
 * 渲染 `⋯` 菜单（仅 owner 且非自己，由父级判断后传入）。</p>
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import BeeAvatar from '@/components/base/BeeAvatar.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeTag from '@/components/base/BeeTag.vue'
import { fileUrl } from '@/utils/fileUrl'
import { formatDate } from '@/utils/format'
import type { Member } from '@/types/models'

interface Props {
  member: Member
  canRemove: boolean
  isSelf: boolean
}

const props = withDefaults(defineProps<Props>(), {
  canRemove: false,
  isSelf: false,
})

const emit = defineEmits<{ remove: [member: Member] }>()

const menuOpen = ref<boolean>(false)
const menuRef = ref<HTMLElement | null>(null)

const avatar = computed<string | null>(() =>
  props.member.avatarUrl ? fileUrl(props.member.avatarUrl, 'thumb') : null,
)

const roleLabel = computed<string>(() => (props.member.role === 'owner' ? '创建者' : '成员'))

function toggleMenu(): void {
  menuOpen.value = !menuOpen.value
}

function onRemove(): void {
  menuOpen.value = false
  emit('remove', props.member)
}

function onDocumentPointerDown(event: PointerEvent): void {
  if (!menuOpen.value) return
  const root = menuRef.value
  if (root && !root.contains(event.target as Node)) menuOpen.value = false
}

onMounted(() => document.addEventListener('pointerdown', onDocumentPointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocumentPointerDown))
</script>

<template>
  <div class="bee-member-row">
    <BeeAvatar :src="avatar" :name="member.nickname" :size="36" />

    <div class="bee-member-row__main">
      <span class="bee-member-row__name">
        {{ member.nickname }}
        <span v-if="isSelf" class="bee-member-row__self">（我）</span>
      </span>
      <span class="bee-member-row__email">{{ member.email }}</span>
    </div>

    <BeeTag :variant="member.role === 'owner' ? 'honey' : 'default'" size="sm">{{ roleLabel }}</BeeTag>

    <span class="bee-member-row__joined">{{ formatDate(member.joinedAt) }} 加入</span>

    <div v-if="canRemove" ref="menuRef" class="bee-member-row__menu-wrap">
      <button
        class="bee-member-row__more"
        type="button"
        aria-label="成员操作"
        :aria-expanded="menuOpen"
        @click="toggleMenu"
      >
        <BeeIcon name="more" :size="16" />
      </button>
      <div v-if="menuOpen" class="bee-member-row__menu" role="menu">
        <button class="bee-member-row__menu-item" type="button" role="menuitem" @click="onRemove">
          移出家庭
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bee-member-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  min-height: 52px;
  padding: var(--sp-2) var(--sp-1);
  border-bottom: 1px solid var(--border-subtle);
}

.bee-member-row:last-child {
  border-bottom: 0;
}

.bee-member-row__main {
  display: flex;
  flex-direction: column;
  gap: 1px;
  flex: 1;
  min-width: 0;
}

.bee-member-row__name {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-member-row__self {
  color: var(--text-tertiary);
  font-weight: 400;
}

.bee-member-row__email {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-member-row__joined {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  white-space: nowrap;
}

.bee-member-row__menu-wrap {
  position: relative;
}

.bee-member-row__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--r-sm);
  color: var(--text-tertiary);
}

.bee-member-row__more:hover {
  background: var(--warm-100);
  color: var(--text-primary);
}

.bee-member-row__menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  z-index: 20;
  min-width: 140px;
  padding: var(--sp-1);
  background: var(--bg-elevated);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-md);
}

.bee-member-row__menu-item {
  width: 100%;
  height: 34px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-sm);
  font-size: var(--fs-body);
  color: var(--color-danger);
  text-align: left;
}

.bee-member-row__menu-item:hover {
  background: var(--color-danger-bg);
}

@media (max-width: 767px) {
  .bee-member-row__joined {
    display: none;
  }
}
</style>
