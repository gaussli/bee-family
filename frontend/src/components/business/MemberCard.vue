<script setup lang="ts">
/**
 * 家庭成员行（docs/06 §6.3 / §6.4）。
 *
 * <p>52px 行：头像 36 → 昵称 → 角色 Chip → 加入时间 → 上传照片数 → 操作。
 * 角色 Chip 全部复用 `BeeTag` 既有变体：`owner` = honey「创建者」、
 * `admin` = outline「管理员」、`member` = default「成员」，不新增色值或变体。</p>
 *
 * <p>权限矩阵：`[⋯]` 菜单**仅 owner 查看非 owner 行时渲染**；
 * admin / member 查看任意行都不渲染菜单；owner 行右侧改显示「身份不可撤销」。</p>
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import BeeAvatar from '@/components/base/BeeAvatar.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeTag from '@/components/base/BeeTag.vue'
import { roleLabel } from '@/api/family'
import { fileUrl } from '@/utils/fileUrl'
import { formatDate } from '@/utils/format'
import type { Member, Role } from '@/types/models'

interface Props {
  member: Member
  /** 当前登录用户在本家庭的角色，决定 `[⋯]` 菜单是否渲染。 */
  viewerRole: Role | null
  isSelf?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isSelf: false,
})

const emit = defineEmits<{
  remove: [member: Member]
  changeRole: [member: Member, role: 'admin' | 'member']
}>()

const menuOpen = ref<boolean>(false)
const menuRef = ref<HTMLElement | null>(null)

const avatar = computed<string | null>(() =>
  props.member.avatarUrl ? fileUrl(props.member.avatarUrl, 'thumb') : null,
)

const tagVariant = computed<'honey' | 'outline' | 'default'>(() => {
  if (props.member.role === 'owner') return 'honey'
  if (props.member.role === 'admin') return 'outline'
  return 'default'
})

/** 仅 owner 且目标不是 owner 时渲染菜单（admin / member 一律不渲染）。 */
const canManage = computed<boolean>(() => props.viewerRole === 'owner' && props.member.role !== 'owner')

function closeMenu(): void {
  menuOpen.value = false
}

function toggleMenu(): void {
  menuOpen.value = !menuOpen.value
}

function onChangeRole(role: 'admin' | 'member'): void {
  closeMenu()
  emit('changeRole', props.member, role)
}

function onRemove(): void {
  closeMenu()
  emit('remove', props.member)
}

function onDocumentPointerDown(event: PointerEvent): void {
  if (!menuOpen.value) return
  const root = menuRef.value
  if (root && !root.contains(event.target as Node)) closeMenu()
}

function onDocumentKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && menuOpen.value) closeMenu()
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onDocumentKeydown)
})
</script>

<template>
  <div class="bee-member-row">
    <BeeAvatar :src="avatar" :name="member.nickname" :size="36" />

    <span class="bee-member-row__name">
      {{ member.nickname }}
      <small v-if="isSelf">（我）</small>
    </span>

    <BeeTag :variant="tagVariant" size="sm">{{ roleLabel(member.role) }}</BeeTag>

    <span class="bee-member-row__joined">{{ formatDate(member.joinedAt) }} 加入</span>
    <span class="bee-member-row__uploads">上传 {{ member.uploadedPhotoCount }} 张</span>

    <span v-if="member.role === 'owner'" class="bee-member-row__owner-note">身份不可撤销</span>

    <div v-else-if="canManage" ref="menuRef" class="bee-member-row__menu-wrap">
      <button
        class="bee-member-row__more"
        type="button"
        aria-label="成员操作"
        :aria-expanded="menuOpen"
        aria-haspopup="menu"
        @click="toggleMenu"
      >
        <BeeIcon name="more" :size="16" />
      </button>
      <div v-if="menuOpen" class="bee-member-row__menu" role="menu">
        <button
          v-if="member.role === 'member'"
          class="bee-member-row__menu-item"
          type="button"
          role="menuitem"
          @click="onChangeRole('admin')"
        >
          设为管理员
        </button>
        <button v-else class="bee-member-row__menu-item" type="button" role="menuitem" @click="onChangeRole('member')">
          撤销管理员
        </button>
        <button class="bee-member-row__menu-item is-danger" type="button" role="menuitem" @click="onRemove">
          移除成员
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bee-member-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-height: 52px;
  padding: var(--sp-2) var(--sp-1);
  border-bottom: 1px solid var(--border-subtle);
}

.bee-member-row:last-child {
  border-bottom: 0;
}

.bee-member-row:hover {
  background: var(--honey-50);
}

.bee-member-row__name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-member-row__name small {
  font-weight: 400;
  color: var(--text-tertiary);
}

.bee-member-row__joined,
.bee-member-row__uploads,
.bee-member-row__owner-note {
  flex: none;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  white-space: nowrap;
}

.bee-member-row__joined {
  width: 120px;
  margin-left: auto;
}

.bee-member-row__uploads {
  width: 80px;
}

.bee-member-row__menu-wrap {
  position: relative;
  flex: none;
}

.bee-member-row__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
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
  width: 180px;
  padding: var(--sp-1);
  background: var(--bg-elevated);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-md);
}

.bee-member-row__menu-item {
  width: 100%;
  height: 36px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-sm);
  font-size: var(--fs-body);
  color: var(--text-primary);
  text-align: left;
}

.bee-member-row__menu-item:hover {
  background: var(--honey-50);
}

.bee-member-row__menu-item.is-danger {
  color: var(--color-danger);
}

.bee-member-row__menu-item.is-danger:hover {
  background: var(--color-danger-bg);
}

@media (max-width: 767px) {
  .bee-member-row__joined,
  .bee-member-row__uploads {
    display: none;
  }
}
</style>
