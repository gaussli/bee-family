<script setup lang="ts">
/**
 * 家庭管理页 `/family`（docs/06 §6）。
 *
 * <p>三块：成员列表（3 种角色 Chip、`[⋯]` 仅 owner 可见）、家庭码卡 + 宝宝卡
 * （左右 6 列）、加入申请列表（仅 owner + admin 渲染，两类角色共用同一 88px 双行）。</p>
 *
 * <p>家庭码为 10 位去混淆字符、永久有效、仅 owner 可重置；重置后警示条采用 §6.2
 * 的既定准确措辞。全页静态文案严格遵循 §9.4 禁令。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import BeeAvatar from '@/components/base/BeeAvatar.vue'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeCard from '@/components/base/BeeCard.vue'
import BeeEmpty from '@/components/base/BeeEmpty.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeInput from '@/components/base/BeeInput.vue'
import BeeModal from '@/components/base/BeeModal.vue'
import BeePageHeader from '@/components/base/BeePageHeader.vue'
import BeeSelect from '@/components/base/BeeSelect.vue'
import BeeSkeleton from '@/components/base/BeeSkeleton.vue'
import BeeTabs from '@/components/base/BeeTabs.vue'
import BeeTag from '@/components/base/BeeTag.vue'
import MemberCard from '@/components/business/MemberCard.vue'
import * as joinRequestApi from '@/api/joinRequest'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useFamilyStore } from '@/stores/family'
import { useToast } from '@/composables/useToast'
import { copyText } from '@/utils/clipboard'
import { fileUrl } from '@/utils/fileUrl'
import { attemptText, formatDateTime, formatFamilyCode } from '@/utils/format'
import type { Gender, JoinRequestResp, JoinStatus, Member, Role } from '@/types/models'

const router = useRouter()
const family = useFamilyStore()
const auth = useAuthStore()
const toast = useToast()

const loading = ref<boolean>(true)
const resetWarning = ref<boolean>(false)

const resetOpen = ref<boolean>(false)
const resetLoading = ref<boolean>(false)

const removing = ref<Member | null>(null)
const removeOpen = ref<boolean>(false)
const removeLoading = ref<boolean>(false)

const revokeTarget = ref<Member | null>(null)
const revokeOpen = ref<boolean>(false)
const revokeLoading = ref<boolean>(false)

const babyOpen = ref<boolean>(false)
const babySaving = ref<boolean>(false)
const babyForm = reactive({ name: '', gender: 'unknown' as Gender, birthday: '' })
const babyErrors = reactive({ name: '', birthday: '' })
const genderOptions = [
  { label: '女宝宝', value: 'female' },
  { label: '男宝宝', value: 'male' },
  { label: '暂不填写', value: 'unknown' },
]

const requestTab = ref<'pending' | 'handled'>('pending')
const pendingRequests = ref<JoinRequestResp[]>([])
const handledRequests = ref<JoinRequestResp[]>([])
const pendingTotal = ref<number>(0)
const approvingId = ref<number | null>(null)
const rejectTarget = ref<JoinRequestResp | null>(null)
const rejectReason = ref<string>('')
const rejectOpen = ref<boolean>(false)
const rejectLoading = ref<boolean>(false)

const members = computed<Member[]>(() => family.members)
const babies = computed(() => family.babies)
const code = computed<string>(() => family.familyCode?.code ?? family.family?.familyCode ?? '')
const displayCode = computed<string>(() => (code.value ? formatFamilyCode(code.value) : ''))
const memberSummary = computed<string>(
  () => `${family.family?.name ?? auth.currentFamilyName} · ${family.memberCount} / ${family.memberLimit} 人`,
)
const viewerRole = computed<Role | null>(() => auth.currentRole)

/** 「待处理 (N)」「已处理」；N 与列表条数一致。 */
const requestTabs = computed(() => [
  { key: 'pending', label: '待处理', count: pendingTotal.value },
  { key: 'handled', label: '已处理' },
])
const visibleRequests = computed<JoinRequestResp[]>(() =>
  requestTab.value === 'pending' ? pendingRequests.value : handledRequests.value,
)

async function load(): Promise<void> {
  loading.value = true
  try {
    await Promise.all([
      family.fetchFamily(),
      family.fetchMembers(),
      family.fetchBabies(),
      family.fetchFamilyCode().catch(() => {
        // 家庭码接口在后端升级前可能不存在：由 /family 响应里的 familyCode 兜底
      }),
    ])
    if (auth.canReview) await loadRequests()
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '家庭信息加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRequests(): Promise<void> {
  try {
    const [pending, handled, count] = await Promise.all([
      joinRequestApi.listJoinRequests('pending'),
      joinRequestApi.listJoinRequests('handled'),
      joinRequestApi.getPendingCount(),
    ])
    pendingRequests.value = pending.items
    handledRequests.value = handled.items
    pendingTotal.value = count.count
    family.pendingReviewCount = count.count
  } catch {
    // 后端升级前申请接口可能不存在：申请区块渲染空态，不影响成员 / 家庭码卡
    pendingRequests.value = []
    handledRequests.value = []
    pendingTotal.value = 0
  }
}

async function copyCode(): Promise<void> {
  if (!code.value) return
  const ok = await copyText(code.value)
  if (ok) toast.success('家庭码已复制')
  else toast.danger('复制失败，请手动选择复制')
}

async function confirmReset(): Promise<void> {
  resetLoading.value = true
  try {
    await family.resetFamilyCode()
    resetOpen.value = false
    resetWarning.value = true
    toast.success('家庭码已重置')
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '重置失败')
  } finally {
    resetLoading.value = false
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

async function onChangeRole(member: Member, role: 'admin' | 'member'): Promise<void> {
  if (role === 'member') {
    revokeTarget.value = member
    revokeOpen.value = true
    return
  }
  try {
    await family.setMemberRole(member.userId, 'admin')
    toast.success(`已将『${member.nickname}』设为管理员`)
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '操作失败')
  }
}

async function confirmRevoke(): Promise<void> {
  if (!revokeTarget.value) return
  revokeLoading.value = true
  try {
    await family.setMemberRole(revokeTarget.value.userId, 'member')
    toast.success('已撤销管理员身份')
    revokeOpen.value = false
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '操作失败')
  } finally {
    revokeLoading.value = false
  }
}

function switchRequestTab(key: string): void {
  requestTab.value = key === 'handled' ? 'handled' : 'pending'
}

async function approve(item: JoinRequestResp): Promise<void> {
  if (family.isFull || approvingId.value !== null) return
  approvingId.value = item.id
  try {
    await joinRequestApi.approveJoinRequest(item.id)
    // 通过按钮 loading 200ms 后再把行从「待处理」移除
    await new Promise<void>((resolve) => window.setTimeout(resolve, 200))
    pendingRequests.value = pendingRequests.value.filter((row) => row.id !== item.id)
    pendingTotal.value = Math.max(0, pendingTotal.value - 1)
    family.pendingReviewCount = pendingTotal.value
    toast.success(`已通过『${item.applicant.nickname}』的加入申请`)
    await Promise.all([family.fetchMembers(), family.fetchFamily()])
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '操作失败')
  } finally {
    approvingId.value = null
  }
}

function openReject(item: JoinRequestResp): void {
  rejectTarget.value = item
  rejectReason.value = ''
  rejectOpen.value = true
}

async function confirmReject(): Promise<void> {
  if (!rejectTarget.value) return
  rejectLoading.value = true
  try {
    await joinRequestApi.rejectJoinRequest(rejectTarget.value.id, {
      reason: rejectReason.value.trim() || undefined,
    })
    pendingRequests.value = pendingRequests.value.filter((row) => row.id !== rejectTarget.value?.id)
    pendingTotal.value = Math.max(0, pendingTotal.value - 1)
    family.pendingReviewCount = pendingTotal.value
    toast.success('已拒绝申请')
    rejectOpen.value = false
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '操作失败')
  } finally {
    rejectLoading.value = false
  }
}

function statusLabel(status: JoinStatus): string {
  if (status === 'approved') return '通过'
  if (status === 'rejected') return '拒绝'
  if (status === 'withdrawn') return '撤回'
  if (status === 'expired') return '过期'
  return '待处理'
}

function requestMessage(item: JoinRequestResp): string {
  return item.message && item.message.trim() ? item.message : '无留言'
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

function babyAvatar(url: string | null): string | null {
  return url ? fileUrl(url, 'thumb') : null
}

onMounted(load)
</script>

<template>
  <div class="bee-family-view">
    <BeePageHeader title="家庭成员" :description="memberSummary">
      <template #actions>
        <BeeButton :disabled="!code" @click="copyCode">
          <template #icon><BeeIcon name="copy" :size="16" /></template>
          复制家庭码
        </BeeButton>
      </template>
    </BeePageHeader>

    <BeeSkeleton v-if="loading" variant="block" :count="4" height="52px" />

    <template v-else>
      <!-- 成员列表 -->
      <BeeCard padding="sm">
        <template #header>
          <span>成员</span>
          <span class="bee-family-view__member-count">{{ family.memberCount }} 人</span>
        </template>
        <MemberCard
          v-for="member in members"
          :key="member.userId"
          :member="member"
          :viewer-role="viewerRole"
          :is-self="member.userId === auth.user?.id"
          @remove="openRemove"
          @change-role="onChangeRole"
        />
      </BeeCard>

      <!-- 家庭码卡 + 宝宝卡（左右各 6 列） -->
      <div class="bee-family-view__columns">
        <BeeCard class="bee-family-view__code-card">
          <template #header>
            <span>家庭码</span>
            <span class="bee-family-view__permanent">永久有效</span>
          </template>

          <div class="bee-family-view__code">{{ displayCode }}</div>
          <p class="bee-family-view__code-hint">凭此码申请加入，需管理员通过</p>

          <div class="bee-family-view__code-actions">
            <BeeButton variant="secondary" size="sm" :disabled="!code" @click="copyCode">
              <template #icon><BeeIcon name="copy" :size="14" /></template>
              复制
            </BeeButton>
            <BeeButton v-if="auth.isOwner" variant="ghost" size="sm" @click="resetOpen = true">
              <template #icon><BeeIcon name="refresh" :size="14" /></template>
              重置
            </BeeButton>
          </div>

          <div v-if="resetWarning" class="bee-family-view__warning">
            <span>旧码已失效，请把新码分享给家人</span>
            <button type="button" @click="copyCode">再复制一次</button>
          </div>
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
              <BeeAvatar :src="babyAvatar(baby.avatarUrl)" :name="baby.name" shape="hex" :size="40" />
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

      <!-- 加入申请：仅 owner + admin 渲染；两类角色共用同一 88px 双行 -->
      <BeeCard v-if="auth.canReview" class="bee-family-view__requests">
        <template #header>
          <BeeTabs :model-value="requestTab" :items="requestTabs" @update:model-value="switchRequestTab" />
          <span class="bee-family-view__requests-note">仅创建者 / 管理员可见</span>
        </template>

        <div v-if="requestTab === 'pending' && family.isFull" class="bee-family-view__warning bee-family-view__full">
          家庭已满 {{ family.memberLimit }} 人，请先移除成员后再通过申请
        </div>

        <BeeEmpty
          v-if="visibleRequests.length === 0"
          compact
          title="暂无待处理的申请"
          description="把家庭码分享给家人，申请会出现在这里"
        />

        <template v-else-if="requestTab === 'pending'">
          <div v-for="item in visibleRequests" :key="item.id" class="bee-family-view__request">
            <div class="bee-family-view__request-line1">
              <BeeAvatar :src="null" :name="item.applicant.nickname" :size="32" />
              <strong>{{ item.applicant.nickname }}</strong>
              <span class="bee-family-view__email">{{ item.applicant.email }}</span>
              <BeeTag variant="outline" size="sm">{{ attemptText(item.attemptCount) }}</BeeTag>
            </div>
            <div class="bee-family-view__request-line2">
              <span class="bee-family-view__request-meta">
                {{ formatDateTime(item.createdAt) }} · {{ requestMessage(item) }} · 近 24 小时 {{ item.attempts24h }} / 5 次
              </span>
              <div class="bee-family-view__request-actions">
                <BeeButton variant="ghost" size="sm" @click="openReject(item)">拒绝</BeeButton>
                <BeeButton
                  variant="primary"
                  size="sm"
                  :loading="approvingId === item.id"
                  :disabled="family.isFull || approvingId !== null"
                  @click="approve(item)"
                >
                  通过
                </BeeButton>
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <div v-for="item in visibleRequests" :key="item.id" class="bee-family-view__handled">
            <span class="bee-family-view__status" :class="{ 'is-rejected': item.status === 'rejected' }">
              {{ statusLabel(item.status) }}
            </span>
            <strong>{{ item.applicant.nickname }}</strong>
            <span v-if="item.reviewedAt" class="bee-family-view__request-meta">{{ formatDateTime(item.reviewedAt) }}</span>
            <span v-if="item.reviewerNickname" class="bee-family-view__request-meta">{{ item.reviewerNickname }}</span>
            <span v-if="item.rejectReason" class="bee-family-view__request-meta">{{ item.rejectReason }}</span>
            <BeeTag variant="outline" size="sm">{{ attemptText(item.attemptCount) }}</BeeTag>
          </div>
        </template>
      </BeeCard>
    </template>

    <!-- 重置家庭码确认：ghost sm 触发、primary 确认、warning 条，不用 danger -->
    <BeeModal v-model:open="resetOpen" title="重置家庭码？" size="sm">
      <div class="bee-family-view__warning bee-family-view__reset-warning">
        重置后旧码立即失效，已分享的旧码无法再用于申请；已提交的申请和已有成员不受影响。
      </div>
      <template #footer>
        <BeeButton variant="secondary" size="md" :disabled="resetLoading" @click="resetOpen = false">取消</BeeButton>
        <BeeButton variant="primary" size="md" :loading="resetLoading" @click="confirmReset">确认重置</BeeButton>
      </template>
    </BeeModal>

    <!-- 拒绝理由 -->
    <BeeModal v-model:open="rejectOpen" :title="`拒绝『${rejectTarget?.applicant.nickname ?? ''}』的加入申请？`" size="sm">
      <BeeInput v-model="rejectReason" label="拒绝理由（选填）" :rows="3" :maxlength="100" placeholder="选填" />
      <span class="bee-family-view__count">{{ rejectReason.length }}/100</span>
      <template #footer>
        <BeeButton variant="secondary" size="md" :disabled="rejectLoading" @click="rejectOpen = false">取消</BeeButton>
        <BeeButton variant="danger" size="md" :loading="rejectLoading" @click="confirmReject">确认拒绝</BeeButton>
      </template>
    </BeeModal>

    <!-- 撤销管理员二次确认 -->
    <BeeModal v-model:open="revokeOpen" :title="`撤销『${revokeTarget?.nickname ?? ''}』的管理员身份？`" size="sm" :loading="revokeLoading" confirm-text="确认撤销" @confirm="confirmRevoke">
      <p>撤销后 TA 将变为普通成员，不能再审批加入申请。</p>
    </BeeModal>

    <!-- 移除成员二次确认 -->
    <BeeModal v-model:open="removeOpen" title="移出家庭" size="sm" :loading="removeLoading" confirm-text="移出" @confirm="confirmRemove">
      <p>确定把 <strong>{{ removing?.nickname }}</strong> 移出家庭吗？移除后 TA 上传的照片会保留在家庭中。</p>
    </BeeModal>

    <!-- 新增宝宝（沿用 02 §5.8） -->
    <BeeModal v-model:open="babyOpen" title="新增宝宝" size="sm" :loading="babySaving" confirm-text="创建" @confirm="saveBaby">
      <div class="bee-family-view__form">
        <BeeInput v-model="babyForm.name" label="昵称" placeholder="例如：团团" required :error="babyErrors.name" :maxlength="32" />
        <BeeSelect v-model="babyForm.gender" label="性别" :options="genderOptions" />
        <BeeInput v-model="babyForm.birthday" type="date" label="生日" required :error="babyErrors.birthday" />
      </div>
    </BeeModal>
  </div>
</template>

<style scoped>
.bee-family-view__member-count,
.bee-family-view__permanent,
.bee-family-view__requests-note {
  font-size: var(--fs-caption);
  font-weight: 400;
  color: var(--text-tertiary);
}

.bee-family-view__columns {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--sp-6);
  margin-top: var(--sp-6);
}

.bee-family-view__code-card {
  min-height: 200px;
}

.bee-family-view__code {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 72px;
  border-radius: var(--r-md);
  background: var(--honey-50);
  font-family: var(--font-mono);
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--text-brand);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.bee-family-view__code-hint {
  margin-top: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-family-view__code-actions {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
}

.bee-family-view__warning {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  min-height: 40px;
  margin-top: var(--sp-3);
  padding: var(--sp-2) var(--sp-3);
  border-radius: var(--r-md);
  background: var(--color-warning-bg);
  color: var(--color-warning);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
}

.bee-family-view__warning button {
  flex: none;
  color: var(--honey-700);
  text-decoration: underline;
}

.bee-family-view__reset-warning {
  min-height: 48px;
  margin-top: 0;
  display: block;
}

.bee-family-view__full {
  margin-top: 0;
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

/* ---------- 申请列表 ---------- */
.bee-family-view__requests {
  margin-top: var(--sp-6);
}

.bee-family-view__request {
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 88px;
  border-bottom: 1px solid var(--border-subtle);
}

.bee-family-view__request:last-child {
  border-bottom: 0;
}

.bee-family-view__request-line1,
.bee-family-view__request-line2 {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-height: 32px;
}

.bee-family-view__request-line1 strong {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-primary);
}

.bee-family-view__email,
.bee-family-view__request-meta {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  white-space: nowrap;
}

.bee-family-view__request-line1 :deep(.bee-tag) {
  margin-left: auto;
}

.bee-family-view__request-line2 {
  min-height: 40px;
}

.bee-family-view__request-line2 .bee-family-view__request-meta {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-secondary);
}

.bee-family-view__request-actions {
  display: flex;
  flex: none;
  gap: var(--sp-2);
}

.bee-family-view__handled {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  height: 56px;
  border-bottom: 1px solid var(--border-subtle);
  font-size: var(--fs-body);
  color: var(--text-primary);
}

.bee-family-view__handled:last-child {
  border-bottom: 0;
}

.bee-family-view__handled .bee-family-view__request-meta {
  overflow: hidden;
  text-overflow: ellipsis;
}

.bee-family-view__handled :deep(.bee-tag) {
  margin-left: auto;
}

.bee-family-view__status {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 var(--sp-2);
  border-radius: var(--r-sm);
  background: var(--honey-100);
  color: var(--text-brand);
  font-size: var(--fs-caption);
  font-weight: 500;
}

.bee-family-view__status.is-rejected {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

.bee-family-view__count {
  display: block;
  margin-top: var(--sp-1);
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  text-align: right;
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

@media (max-width: 767px) {
  .bee-family-view__request {
    height: auto;
    min-height: 132px;
    padding: var(--sp-2) 0;
  }

  .bee-family-view__request-line1,
  .bee-family-view__request-line2 {
    flex-wrap: wrap;
  }

  .bee-family-view__request-actions {
    width: 100%;
  }

  .bee-family-view__request-actions :deep(.bee-btn) {
    flex: 1;
  }
}
</style>
