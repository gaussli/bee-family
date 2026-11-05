<script setup lang="ts">
/**
 * 家庭成员（02 文档 §5.8）。
 *
 * <p>成员列表（52px 行）+ 左下邀请卡（`--font-mono` 32/700、字距 .2em）+ 右下宝宝卡。
 * 邀请码 24 小时有效；重置后二维码/口令立即换新。满员时邀请卡置灰并给出提示。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import BeeAvatar from '@/components/base/BeeAvatar.vue'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeCard from '@/components/base/BeeCard.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeInput from '@/components/base/BeeInput.vue'
import BeeModal from '@/components/base/BeeModal.vue'
import BeePageHeader from '@/components/base/BeePageHeader.vue'
import BeeSelect from '@/components/base/BeeSelect.vue'
import BeeSkeleton from '@/components/base/BeeSkeleton.vue'
import MemberCard from '@/components/business/MemberCard.vue'
import { ApiError } from '@/api/http'
import { useFamilyStore } from '@/stores/family'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { copyText } from '@/utils/clipboard'
import { fileUrl } from '@/utils/fileUrl'
import { formatDateTime } from '@/utils/format'
import type { Gender, Member } from '@/types/models'

const router = useRouter()
const family = useFamilyStore()
const auth = useAuthStore()
const toast = useToast()

const loading = ref<boolean>(true)
const removing = ref<Member | null>(null)
const removeOpen = ref<boolean>(false)
const removeLoading = ref<boolean>(false)

const babyOpen = ref<boolean>(false)
const babySaving = ref<boolean>(false)
const babyForm = reactive({ name: '', gender: 'unknown' as Gender, birthday: '' })
const babyErrors = reactive({ name: '', birthday: '' })

const genderOptions = [
  { label: '女宝宝', value: 'female' },
  { label: '男宝宝', value: 'male' },
  { label: '暂不填写', value: 'unknown' },
]

const members = computed<Member[]>(() => family.members)
const invite = computed(() => family.inviteCode)
const babies = computed(() => family.babies)

const memberSummary = computed<string>(() => {
  const name = family.family?.name ?? '我的家庭'
  return `${name} · ${family.memberCount} / ${family.memberLimit} 人`
})

const expiresText = computed<string>(() => {
  if (!invite.value) return ''
  return invite.value.expired ? '邀请码已过期，请点「重置」' : `${formatDateTime(invite.value.expiresAt)} 前有效`
})

async function load(): Promise<void> {
  loading.value = true
  try {
    await Promise.all([family.fetchFamily(), family.fetchMembers(), family.fetchBabies()])
    if (auth.isOwner) {
      await family.fetchInviteCode()
    }
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '家庭信息加载失败')
  } finally {
    loading.value = false
  }
}

async function copyCode(): Promise<void> {
  if (!invite.value) return
  const ok = await copyText(invite.value.code)
  if (ok) toast.success('邀请码已复制')
  else toast.danger('复制失败，请手动选择复制')
}

async function resetCode(): Promise<void> {
  try {
    await family.resetInviteCode()
    toast.success('邀请码已重置')
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '重置失败')
  }
}

function openRemove(member: Member): void {
  removing.value = member
  removeOpen.value = true
}

async function confirmRemove(): Promise<void> {
  if (!removing.value) return
  removeLoading.value = true
  try {
    await family.removeMember(removing.value.userId)
    toast.success('成员已移出家庭')
    removeOpen.value = false
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '移除失败')
  } finally {
    removeLoading.value = false
  }
}

function openBabyForm(): void {
  babyForm.name = ''
  babyForm.gender = 'unknown'
  babyForm.birthday = ''
  babyErrors.name = ''
  babyErrors.birthday = ''
  babyOpen.value = true
}

async function saveBaby(): Promise<void> {
  babyErrors.name = babyForm.name.trim() ? '' : '请输入宝宝昵称'
  babyErrors.birthday = babyForm.birthday ? '' : '请选择生日'
  if (babyErrors.name || babyErrors.birthday) return

  babySaving.value = true
  try {
    const created = await family.createBaby({
      name: babyForm.name.trim(),
      gender: babyForm.gender,
      birthday: babyForm.birthday,
    })
    if (family.activeBabyId === null) family.setActiveBaby(created.id)
    toast.success('宝宝档案已创建')
    babyOpen.value = false
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '创建失败')
  } finally {
    babySaving.value = false
  }
}

function goBaby(id: number): void {
  void router.push({ name: 'baby', params: { id: String(id) } })
}

function babyAvatar(id: number | null, url: string | null): string | null {
  void id
  return url ? fileUrl(url, 'thumb') : null
}

onMounted(load)
</script>

<template>
  <div class="bee-family-view">
    <BeePageHeader title="家庭成员" :description="memberSummary">
      <template #actions>
        <BeeButton :disabled="family.isFull" @click="copyCode">
          <template #icon><BeeIcon name="user" :size="16" /></template>
          邀请成员
        </BeeButton>
      </template>
    </BeePageHeader>

    <BeeSkeleton v-if="loading" variant="block" :count="4" height="52px" />

    <template v-else>
      <BeeCard padding="sm">
        <MemberCard
          v-for="member in members"
          :key="member.userId"
          :member="member"
          :is-self="member.userId === auth.user?.id"
          :can-remove="auth.isOwner && member.role !== 'owner'"
          @remove="openRemove"
        />
      </BeeCard>

      <div class="bee-family-view__columns">
        <BeeCard>
          <template #header>
            <span>邀请家人加入</span>
            <BeeIcon name="copy" :size="16" />
          </template>

          <div v-if="family.isFull" class="bee-family-view__full">
            已达 {{ family.memberLimit }} 人上限，暂时不能再邀请新成员
          </div>

          <template v-else-if="invite">
            <div class="bee-family-view__code" :class="{ 'is-expired': invite.expired }">
              {{ invite.code }}
            </div>
            <p class="bee-family-view__expires">{{ expiresText }}</p>

            <div class="bee-family-view__invite-actions">
              <BeeButton variant="secondary" size="sm" @click="copyCode">
                <template #icon><BeeIcon name="copy" :size="14" /></template>
                复制邀请码
              </BeeButton>
              <BeeButton variant="ghost" size="sm" @click="resetCode">
                <template #icon><BeeIcon name="refresh" :size="14" /></template>
                重置
              </BeeButton>
            </div>
          </template>

          <p v-else class="bee-family-view__placeholder">只有家庭创建者可以查看邀请码</p>
        </BeeCard>

        <BeeCard>
          <template #header>
            <span>宝宝</span>
            <BeeButton variant="ghost" size="sm" @click="openBabyForm">
              <template #icon><BeeIcon name="plus" :size="14" /></template>
              新增
            </BeeButton>
          </template>

          <div class="bee-family-view__babies">
            <button
              v-for="baby in babies"
              :key="baby.id"
              class="bee-family-view__baby"
              type="button"
              @click="goBaby(baby.id)"
            >
              <BeeAvatar
                :src="babyAvatar(baby.id, baby.avatarUrl)"
                :name="baby.name"
                shape="hex"
                :size="40"
              />
              <span class="bee-family-view__baby-name">{{ baby.name }}</span>
              <span class="bee-family-view__baby-meta">{{ baby.ageText }}</span>
            </button>

            <button class="bee-family-view__baby-add" type="button" @click="openBabyForm">
              <BeeIcon name="plus" :size="18" />
              <span>新增宝宝</span>
            </button>
          </div>
        </BeeCard>
      </div>
    </template>

    <BeeModal
      v-model:open="removeOpen"
      title="移出家庭"
      size="sm"
      danger
      :loading="removeLoading"
      confirm-text="移出"
      @confirm="confirmRemove"
    >
      <p class="bee-family-view__modal-text">
        确定把 <strong>{{ removing?.nickname }}</strong> 移出家庭吗？移除后 TA 上传的照片会保留在家庭中。
      </p>
    </BeeModal>

    <BeeModal
      v-model:open="babyOpen"
      title="新增宝宝"
      size="sm"
      :loading="babySaving"
      confirm-text="创建"
      @confirm="saveBaby"
    >
      <div class="bee-family-view__form">
        <BeeInput v-model="babyForm.name" label="昵称" placeholder="例如：团团" required :error="babyErrors.name" :maxlength="32" />
        <BeeSelect v-model="babyForm.gender" label="性别" :options="genderOptions" />
        <BeeInput v-model="babyForm.birthday" type="date" label="生日" required :error="babyErrors.birthday" />
      </div>
    </BeeModal>
  </div>
</template>

<style scoped>
.bee-family-view__columns {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--sp-6);
  margin-top: var(--sp-6);
}

.bee-family-view__code {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-4);
  border-radius: var(--r-md);
  background: var(--honey-50);
  font-family: var(--font-mono);
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: var(--text-brand);
  font-variant-numeric: tabular-nums;
}

.bee-family-view__code.is-expired {
  color: var(--text-disabled);
  text-decoration: line-through;
}

.bee-family-view__expires {
  margin-top: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-family-view__invite-actions {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
}

.bee-family-view__full {
  padding: var(--sp-3);
  border-radius: var(--r-md);
  background: var(--color-warning-bg);
  color: var(--color-warning);
  font-size: var(--fs-body);
}

.bee-family-view__placeholder {
  font-size: var(--fs-body);
  color: var(--text-tertiary);
}

.bee-family-view__babies {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-4);
}

.bee-family-view__baby {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  width: 76px;
  padding: var(--sp-2);
  border-radius: var(--r-md);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.bee-family-view__baby:hover {
  background: var(--honey-50);
}

.bee-family-view__baby-name {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-primary);
}

.bee-family-view__baby-meta {
  font-size: 11px;
  color: var(--text-tertiary);
}

.bee-family-view__baby-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-1);
  width: 76px;
  min-height: 84px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--r-md);
  color: var(--text-tertiary);
  font-size: var(--fs-caption);
}

.bee-family-view__baby-add:hover {
  border-color: var(--honey-400);
  color: var(--text-brand);
}

.bee-family-view__modal-text {
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
}

.bee-family-view__modal-text strong {
  color: var(--text-primary);
}

.bee-family-view__form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

@media (max-width: 1023px) {
  .bee-family-view__columns {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
