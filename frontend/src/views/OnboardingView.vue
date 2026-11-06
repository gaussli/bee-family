<script setup lang="ts">
/**
 * 无家庭引导页（docs/06 §5；docs/07 §10.1 / §10.2）。
 *
 * <p>三态：`NEW`（0 家庭 0 申请，两张卡 + 原位展开表单）、`PENDING`（只手动刷新、
 * 无定时器 / 无 WebSocket / 无轮询，两个出口常驻）、`REJECTED_OR_EXPIRED`
 * （结果卡 + 三个入口 + 24h 计数）。</p>
 *
 * <p>Q9 硬约束：**仅回显家庭名**，不请求 / 不渲染成员数、照片数、创建者、创建时间等；
 * 顶部条只有 Logo / 账号设置 / 退出登录，不含任何导航项、搜索、上传、家庭切换器；
 * 页面不得进主界面。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeHexBadge from '@/components/base/BeeHexBadge.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeInput from '@/components/base/BeeInput.vue'
import BeeModal from '@/components/base/BeeModal.vue'
import BeeTag from '@/components/base/BeeTag.vue'
import HexPattern from '@/components/business/HexPattern.vue'
import * as familyApi from '@/api/family'
import * as joinRequestApi from '@/api/joinRequest'
import * as switchApi from '@/api/switch'
import { ApiError } from '@/api/http'
import { ErrorCode } from '@/types/api'
import { useAuthStore } from '@/stores/auth'
import { useSwitchStore } from '@/stores/switch'
import { useToast } from '@/composables/useToast'
import { attemptText, formatDateTime, formatFamilyCode } from '@/utils/format'
import { FAMILY_CODE_PATTERN, normalizeFamilyCode } from '@/utils/validate'
import type { OnboardingStateResp, PendingRequest } from '@/types/models'

type FormMode = 'none' | 'create' | 'join'
type JoinPhase = 'input' | 'confirm'
type ViewState = 'A' | 'B' | 'C'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

const mode = ref<FormMode>('none')
const joinPhase = ref<JoinPhase>('input')
/** 用户从等待态 / 结果态主动切回状态 A 表单时置 'A'；清空后回到服务端三态。 */
const forcedState = ref<ViewState | null>(null)

const createName = ref<string>('')
const createLoading = ref<boolean>(false)

const joinCode = ref<string>('')
const joinMessage = ref<string>('')
const joinError = ref<string>('')
const lookupFamilyName = ref<string>('')
const lookupLoading = ref<boolean>(false)
const submitLoading = ref<boolean>(false)
const alreadyMember = ref<boolean>(false)
const alreadyPending = ref<boolean>(false)
/** 本次会话里用户提交过的家庭码，供「重新申请」复用（仅内存，不持久化）。 */
const lastCode = ref<string>('')

const refreshing = ref<boolean>(false)
const withdrawOpen = ref<boolean>(false)
const withdrawLoading = ref<boolean>(false)
const mineState = ref<OnboardingStateResp | null>(null)

const RATE_LIMIT = 5

const serverState = computed<string>(() => auth.onboardingState)
const viewState = computed<ViewState>(() => {
  if (forcedState.value) return forcedState.value
  if (serverState.value === 'PENDING') return 'B'
  if (serverState.value === 'REJECTED_OR_EXPIRED') return 'C'
  return 'A'
})

const pending = computed<PendingRequest | null>(() => auth.pendingRequest ?? mineState.value?.pendingRequest ?? null)
/** 结果卡数据源：只取家庭名 / 时间 / 拒绝理由，不取任何统计字段。 */
const resultRequest = computed<PendingRequest | null>(() => pending.value)
const pendingName = computed<string>(() => pending.value?.familyName ?? lookupFamilyName.value ?? '该家庭')
const resultName = computed<string>(() => resultRequest.value?.familyName ?? lookupFamilyName.value ?? '该家庭')
const rejectReason = computed<string>(() => resultRequest.value?.rejectReason ?? '')
const submittedAt = computed<string>(() => formatDateTime(resultRequest.value?.createdAt ?? pending.value?.createdAt))
const attempts24h = computed<number>(() => resultRequest.value?.attempts24h ?? 0)
const historyCount = computed<number>(() => {
  const fromMe = auth.historyRequestCount
  if (fromMe > 0) return fromMe
  const fromMine = mineState.value?.historyRequestCount ?? 0
  if (fromMine > 0) return fromMine
  return resultRequest.value?.attemptCount ?? 1
})
const rateLimited = computed<boolean>(() => attempts24h.value >= RATE_LIMIT)

const expiresText = computed<string>(() => {
  const raw = pending.value?.expiresAt
  if (!raw) return '申请将在 7 天后过期'
  const expires = new Date(raw.replace(' ', 'T'))
  const days = Math.ceil((expires.getTime() - Date.now()) / 86_400_000)
  if (!Number.isFinite(days)) return '申请将在 7 天后过期'
  if (days <= 0) return '申请已过期'
  return `申请将在 ${days} 天后过期`
})

const createDisabled = computed<boolean>(() => {
  const name = createName.value.trim()
  return name.length === 0 || name.length > 40 || createLoading.value
})
const codeValid = computed<boolean>(() => FAMILY_CODE_PATTERN.test(normalizeFamilyCode(joinCode.value)))

/** 卡片 A 展开：创建我的家庭 */
function expandCreate(): void {
  mode.value = 'create'
  joinPhase.value = 'input'
  joinError.value = ''
}

/** 卡片 B 展开：凭家庭码加入 */
function expandJoin(): void {
  mode.value = 'join'
  joinPhase.value = 'input'
  joinError.value = ''
  alreadyMember.value = false
  alreadyPending.value = false
}

function backToChoice(): void {
  mode.value = 'none'
  joinPhase.value = 'input'
  joinError.value = ''
  alreadyMember.value = false
  alreadyPending.value = false
}

function onCodeInput(value: string): void {
  joinCode.value = formatFamilyCode(value)
  joinError.value = ''
  alreadyMember.value = false
  alreadyPending.value = false
}

async function submitCreate(): Promise<void> {
  const name = createName.value.trim()
  if (name.length === 0 || name.length > 40) return
  createLoading.value = true
  try {
    await familyApi.createFamily(name)
    toast.info('家庭码已生成，可在『家庭成员』页分享给家人')
    await auth.fetchMe()
    await router.replace({ name: 'timeline' })
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '创建失败，请重试')
  } finally {
    createLoading.value = false
  }
}

async function lookupCode(): Promise<void> {
  const code = normalizeFamilyCode(joinCode.value)
  if (!FAMILY_CODE_PATTERN.test(code)) {
    joinError.value = '家庭码无效或不存在'
    return
  }
  lookupLoading.value = true
  joinError.value = ''
  alreadyMember.value = false
  alreadyPending.value = false
  try {
    const result = await joinRequestApi.lookupFamilyCode({ familyCode: code })
    if (!result.valid || !result.familyName) {
      // 统一文案：不区分码不存在 / 已重置 / 家庭已满 / 家庭已删除
      joinError.value = '家庭码无效或不存在'
      return
    }
    lookupFamilyName.value = result.familyName
    lastCode.value = code
    joinPhase.value = 'confirm'
  } catch {
    joinError.value = '家庭码无效或不存在'
  } finally {
    lookupLoading.value = false
  }
}

function reInput(): void {
  joinPhase.value = 'input'
  joinError.value = ''
  alreadyMember.value = false
  alreadyPending.value = false
}

async function submitJoin(): Promise<void> {
  const code = lastCode.value || normalizeFamilyCode(joinCode.value)
  if (!FAMILY_CODE_PATTERN.test(code)) {
    joinPhase.value = 'input'
    joinError.value = '家庭码无效或不存在'
    return
  }
  submitLoading.value = true
  joinError.value = ''
  try {
    await joinRequestApi.createJoinRequest({
      familyCode: code,
      message: joinMessage.value.trim() || undefined,
    })
    lastCode.value = code
    forcedState.value = null
    joinPhase.value = 'input'
    mode.value = 'none'
    await auth.refreshOnboarding()
    await loadMine()
  } catch (error) {
    if (error instanceof ApiError && error.code === ErrorCode.JOIN_REQUEST_ALREADY_MEMBER) {
      joinError.value = '你已是该家庭成员'
      alreadyMember.value = true
      return
    }
    if (error instanceof ApiError && error.code === ErrorCode.JOIN_REQUEST_STATUS_ILLEGAL) {
      joinError.value = '申请已提交，请等待管理员处理'
      alreadyPending.value = true
      return
    }
    // 42901 等差异化文案一律展示服务端 message，不做按 code 的本地文案映射
    joinError.value = error instanceof ApiError ? error.message : '提交失败，请稍后重试'
  } finally {
    submitLoading.value = false
  }
}

async function switchToExistingFamily(): Promise<void> {
  try {
    const families = await familyApi.listFamilies()
    const target = families.find((item) => item.name === lookupFamilyName.value)
    if (!target) {
      toast.info('请在家庭切换器中选择该家庭')
      return
    }
    const payload = await switchApi.switchFamily(target.familyId)
    auth.applyMe(payload)
    useSwitchStore().resetAllForFamily(target.familyId)
    toast.info(`已切换到『${target.name}』`)
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '切换失败，请重试')
  }
}

/** 手动刷新：只在用户点击时查询一次，页面不设任何定时器 / WebSocket（Q9）。 */
async function refresh(): Promise<void> {
  refreshing.value = true
  try {
    await auth.refreshOnboarding()
    await loadMine()
    if (!auth.noFamily) {
      toast.success(`已加入『${auth.currentFamilyName}』`)
      await router.replace({ name: 'timeline' })
      return
    }
    if (auth.onboardingState === 'PENDING') toast.info('仍是待审核状态，请稍后再查')
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '刷新失败，请重试')
  } finally {
    refreshing.value = false
  }
}

async function loadMine(): Promise<void> {
  try {
    mineState.value = await joinRequestApi.getMine()
  } catch {
    // 后端升级前该接口可能不存在：三态仍由 /auth/me 驱动，不造 mock
    mineState.value = null
  }
}

/** 等待态两个出口：常驻、等宽、均 enabled */
function reapplyOtherFamily(): void {
  forcedState.value = 'A'
  expandJoin()
  joinCode.value = ''
  joinMessage.value = ''
}

function createOwnFamily(): void {
  forcedState.value = 'A'
  expandCreate()
  createName.value = ''
}

function goReapply(): void {
  if (rateLimited.value) return
  forcedState.value = 'A'
  expandJoin()
  if (lastCode.value) {
    joinCode.value = formatFamilyCode(lastCode.value)
    joinPhase.value = 'confirm'
    lookupFamilyName.value = resultName.value
  } else {
    joinCode.value = ''
  }
}

function changeCode(): void {
  forcedState.value = 'A'
  expandJoin()
  joinCode.value = ''
}

async function confirmWithdraw(): Promise<void> {
  const id = pending.value?.id
  if (id === undefined) {
    withdrawOpen.value = false
    return
  }
  withdrawLoading.value = true
  try {
    await joinRequestApi.withdrawJoinRequest(id)
    withdrawOpen.value = false
    forcedState.value = null
    await auth.refreshOnboarding()
    await loadMine()
    toast.info('申请已撤回')
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '撤回失败，请重试')
  } finally {
    withdrawLoading.value = false
  }
}

async function logout(): Promise<void> {
  await auth.logout()
  toast.success('已退出登录')
  await router.replace({ name: 'login' })
}

onMounted(async () => {
  // 进入页面只查询一次申请状态；不做轮询，等待态完全靠「刷新状态」手动触发。
  await loadMine()
  const intent = route.query.intent
  if (intent === 'create') {
    forcedState.value = 'A'
    expandCreate()
  } else if (intent === 'join') {
    forcedState.value = 'A'
    expandJoin()
  }
})
</script>

<template>
  <div class="bee-onboarding">
    <section class="bee-onboarding__brand" aria-hidden="false">
      <div class="bee-onboarding__brand-inner">
        <div class="bee-onboarding__logo">
          <span class="bee-onboarding__mark"><BeeIcon name="hex" :size="32" :stroke-width="1.4" /></span>
          <span class="bee-onboarding__logo-text">
            <strong>蜜记</strong>
            <small>Bee Family</small>
          </span>
        </div>
        <h1 class="bee-onboarding__slogan">把一家人的时光，<br />收成一册暖光相册。</h1>
        <span class="bee-onboarding__rule" />
      </div>
      <div class="bee-onboarding__pattern" aria-hidden="true">
        <HexPattern :cols="10" :rows="8" :radius="16" :opacity="0.08" />
      </div>
    </section>

    <section class="bee-onboarding__panel">
      <header class="bee-onboarding__top">
        <RouterLink to="/onboarding" class="bee-onboarding__top-logo" aria-label="蜜记首页">
          <BeeIcon name="hex" :size="24" :stroke-width="1.4" />
          <strong>蜜记</strong>
        </RouterLink>
        <div class="bee-onboarding__top-links">
          <RouterLink :to="{ name: 'settings-account' }">账号设置</RouterLink>
          <button type="button" @click="logout">退出登录</button>
        </div>
      </header>

      <div class="bee-onboarding__mobile-logo" aria-hidden="true">
        <BeeIcon name="hex" :size="28" />
      </div>

      <div class="bee-onboarding__content">
        <!-- ============ 状态 A · 全新 ============ -->
        <template v-if="viewState === 'A'">
          <h2 class="bee-onboarding__heading">先给自己安一个家</h2>
          <p class="bee-onboarding__subheading">创建一个家庭，或凭家庭码申请加入。</p>

          <div class="bee-onboarding__choices">
            <!-- 卡 A：创建我的家庭 -->
            <div v-if="mode !== 'create'" class="bee-onboarding__choice" role="button" tabindex="0" @click="expandCreate" @keydown.enter.prevent="expandCreate" @keydown.space.prevent="expandCreate">
              <BeeHexBadge :size="32" variant="honey" icon="hex" />
              <div class="bee-onboarding__choice-main">
                <strong>创建我的家庭</strong>
                <small>自己成为家庭管理员</small>
              </div>
              <span class="bee-onboarding__choice-action">创建 →</span>
            </div>
            <div v-else class="bee-onboarding__card">
              <strong class="bee-onboarding__card-title">创建我的家庭</strong>
              <BeeInput
                :model-value="createName"
                label="家庭名"
                placeholder="例如：许家小院"
                :maxlength="40"
                @update:model-value="createName = $event"
              />
              <BeeButton class="bee-onboarding__block-btn" size="lg" block :loading="createLoading" :disabled="createDisabled" @click="submitCreate">
                {{ createLoading ? '创建中…' : '创建家庭' }}
              </BeeButton>
              <button class="bee-onboarding__text-link" type="button" @click="backToChoice">返回选择</button>
            </div>

            <!-- 卡 B：凭家庭码加入 -->
            <div v-if="mode !== 'join'" class="bee-onboarding__choice" role="button" tabindex="0" @click="expandJoin" @keydown.enter.prevent="expandJoin" @keydown.space.prevent="expandJoin">
              <BeeHexBadge :size="32" variant="outline" icon="user" />
              <div class="bee-onboarding__choice-main">
                <strong>凭家庭码加入</strong>
                <small>向已有家庭发出申请</small>
              </div>
              <span class="bee-onboarding__choice-action">加入 →</span>
            </div>
            <div v-else class="bee-onboarding__card">
              <template v-if="joinPhase === 'input'">
                <strong class="bee-onboarding__card-title">凭家庭码加入</strong>
                <BeeInput
                  :model-value="joinCode"
                  label="家庭码"
                  placeholder="BEE7K-M3Q92"
                  :maxlength="11"
                  :error="joinError"
                  @update:model-value="onCodeInput"
                />
                <p class="bee-onboarding__hint">10 位家庭码，忽略连字符与大小写</p>
                <BeeButton class="bee-onboarding__block-btn" size="lg" block :loading="lookupLoading" :disabled="!codeValid || lookupLoading" @click="lookupCode">
                  校验家庭码
                </BeeButton>
                <button class="bee-onboarding__text-link" type="button" @click="backToChoice">返回选择</button>
              </template>

              <template v-else>
                <!-- 仅回显家庭名：不渲染成员数 / 照片数 / 创建者 / 创建时间 -->
                <div class="bee-onboarding__confirm">
                  <span>你要申请加入</span>
                  <strong>{{ lookupFamilyName }}</strong>
                </div>
                <BeeInput
                  v-model="joinMessage"
                  label="给管理员留一句话（选填）"
                  placeholder="我是团团的爸爸～"
                  :rows="3"
                  :maxlength="50"
                />
                <span class="bee-onboarding__count">{{ joinMessage.length }}/50</span>
                <div v-if="joinError" class="bee-onboarding__inline-error">
                  <span>{{ joinError }}</span>
                  <button v-if="alreadyMember" type="button" @click="switchToExistingFamily">切换到该家庭</button>
                  <button v-else-if="alreadyPending" type="button" @click="forcedState = null">查看申请状态</button>
                </div>
                <BeeButton class="bee-onboarding__block-btn" size="lg" block :loading="submitLoading" @click="submitJoin">
                  提交申请
                </BeeButton>
                <button class="bee-onboarding__text-link" type="button" @click="reInput">重新输入</button>
              </template>
            </div>
          </div>
        </template>

        <!-- ============ 状态 B · 待审核中 ============ -->
        <template v-else-if="viewState === 'B'">
          <div class="bee-onboarding__breath" aria-hidden="true">
            <span class="bee-onboarding__breath-inner" />
          </div>
          <h2 class="bee-onboarding__pending-title">已向「{{ pendingName }}」发出申请</h2>
          <p class="bee-onboarding__pending-meta">提交时间 {{ formatDateTime(pending?.createdAt) }}</p>
          <p class="bee-onboarding__pending-meta">{{ expiresText }}</p>

          <BeeButton class="bee-onboarding__block-btn" size="md" block :loading="refreshing" @click="refresh">
            {{ refreshing ? '刷新中…' : '刷新状态' }}
          </BeeButton>

          <div class="bee-onboarding__exits">
            <BeeButton variant="secondary" size="md" @click="reapplyOtherFamily">再申请其他家庭</BeeButton>
            <BeeButton variant="secondary" size="md" @click="createOwnFamily">不等了，创建自己的家庭</BeeButton>
          </div>

          <button class="bee-onboarding__text-link bee-onboarding__withdraw" type="button" @click="withdrawOpen = true">
            撤回申请
          </button>
          <p class="bee-onboarding__note">仅手动刷新，无自动轮询；刷新按钮每次点击重新查询一次状态</p>
        </template>

        <!-- ============ 状态 C · 被拒 / 过期 / 全撤回 ============ -->
        <template v-else>
          <h2 class="bee-onboarding__heading">申请暂未通过</h2>
          <div class="bee-onboarding__result">
            <BeeTag variant="outline" size="md">未通过</BeeTag>
            <strong>{{ resultName }}</strong>
            <span v-if="submittedAt">{{ submittedAt }}</span>
            <p v-if="rejectReason" class="bee-onboarding__reason">{{ rejectReason }}</p>
          </div>
          <p class="bee-onboarding__result-meta">{{ attemptText(historyCount) }} · 近 24 小时已提交 {{ attempts24h }} / {{ RATE_LIMIT }} 次</p>
          <p v-if="rateLimited" class="bee-onboarding__rate">操作太频繁，请稍后再试</p>

          <BeeButton class="bee-onboarding__block-btn" size="md" block :disabled="rateLimited" @click="goReapply">重新申请</BeeButton>
          <BeeButton class="bee-onboarding__block-btn" variant="secondary" size="md" block @click="changeCode">换个家庭码</BeeButton>
          <BeeButton class="bee-onboarding__block-btn" variant="ghost" size="md" block @click="createOwnFamily">创建自己的家庭</BeeButton>
        </template>
      </div>
    </section>

    <BeeModal
      v-model:open="withdrawOpen"
      title="撤回申请？"
      size="sm"
      :loading="withdrawLoading"
      confirm-text="确认撤回"
      @confirm="confirmWithdraw"
    >
      <p>撤回后需要重新提交申请。</p>
    </BeeModal>
  </div>
</template>

<style scoped>
.bee-onboarding {
  display: flex;
  min-height: 100vh;
  background: var(--bg-page);
}

/* ---------- 左品牌区（沿用 02 §5.1 登录页） ---------- */
.bee-onboarding__brand {
  position: relative;
  flex: 0 0 55%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-16);
  background: var(--honey-50);
  border-right: 1px solid var(--border-subtle);
  overflow: hidden;
}

.bee-onboarding__brand-inner {
  position: relative;
  z-index: 2;
  max-width: 420px;
}

.bee-onboarding__logo {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.bee-onboarding__mark {
  color: var(--honey-500);
}

.bee-onboarding__logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.bee-onboarding__logo-text strong {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.bee-onboarding__logo-text small {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  letter-spacing: 0.06em;
}

.bee-onboarding__slogan {
  margin-top: var(--sp-10);
  font-family: var(--font-display);
  font-size: var(--fs-display);
  line-height: var(--lh-display);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-onboarding__rule {
  display: block;
  width: 48px;
  height: 2px;
  margin-top: var(--sp-6);
  border-radius: var(--r-full);
  background: var(--honey-500);
}

.bee-onboarding__pattern {
  position: absolute;
  right: -80px;
  bottom: -80px;
  z-index: 1;
  width: 460px;
  height: 460px;
  pointer-events: none;
  -webkit-mask-image: radial-gradient(circle at 100% 100%, black 0%, transparent 68%);
  mask-image: radial-gradient(circle at 100% 100%, black 0%, transparent 68%);
}

/* ---------- 右操作区 ---------- */
.bee-onboarding__panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.bee-onboarding__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  height: 64px;
  flex: none;
  padding: 0 var(--sp-6);
}

.bee-onboarding__top-logo {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  color: var(--honey-500);
}

.bee-onboarding__top-logo strong {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.bee-onboarding__top-links {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  font-size: var(--fs-caption);
  color: var(--text-secondary);
}

.bee-onboarding__top-links button:hover {
  color: var(--text-brand);
  text-decoration: underline;
}

.bee-onboarding__mobile-logo {
  display: none;
  align-items: center;
  justify-content: center;
  height: 120px;
  color: var(--honey-500);
}

.bee-onboarding__content {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  width: 440px;
  max-width: calc(100% - 32px);
  margin: 0 auto;
  padding: var(--sp-8) 0 var(--sp-12);
}

.bee-onboarding__heading {
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
  color: var(--text-primary);
}

.bee-onboarding__subheading {
  margin-top: var(--sp-2);
  margin-bottom: var(--sp-6);
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
}

.bee-onboarding__choices {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

/* ---------- 两张等高 112px 卡 ---------- */
.bee-onboarding__choice {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  min-height: 112px;
  padding: var(--sp-4);
  border-radius: var(--r-lg);
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition:
    transform var(--dur-base) var(--ease-out),
    box-shadow var(--dur-base) var(--ease-out);
}

.bee-onboarding__choice:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.bee-onboarding__choice:focus-visible {
  outline: 2px solid var(--honey-500);
  outline-offset: 3px;
}

.bee-onboarding__choice-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--sp-1);
  min-width: 0;
}

.bee-onboarding__choice-main strong {
  font-size: var(--fs-body-lg);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-onboarding__choice-main small {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-onboarding__choice-action {
  align-self: flex-end;
  font-size: var(--fs-caption);
  font-weight: 500;
  color: var(--text-brand);
  white-space: nowrap;
}

.bee-onboarding__card {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-6);
  border-radius: var(--r-lg);
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
}

.bee-onboarding__card-title {
  font-size: var(--fs-body-lg);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-onboarding__block-btn + .bee-onboarding__block-btn {
  margin-top: var(--sp-3);
}

.bee-onboarding__hint,
.bee-onboarding__note,
.bee-onboarding__count {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.bee-onboarding__count {
  align-self: flex-end;
  margin-top: calc(var(--sp-3) * -1);
}

.bee-onboarding__text-link {
  align-self: center;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-onboarding__text-link:hover {
  color: var(--text-brand);
  text-decoration: underline;
}

/* 状态 A 确认条：只有家庭名 */
.bee-onboarding__confirm {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  justify-content: center;
  min-height: 64px;
  padding: var(--sp-4);
  border-radius: var(--r-md);
  background: var(--honey-50);
}

.bee-onboarding__confirm span {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-onboarding__confirm strong {
  font-size: 20px;
  line-height: 28px;
  font-weight: 600;
  color: var(--text-brand);
}

.bee-onboarding__inline-error {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  font-size: var(--fs-caption);
  color: var(--color-danger);
}

.bee-onboarding__inline-error button {
  align-self: flex-start;
  color: var(--honey-700);
  text-decoration: underline;
}

/* ---------- 状态 B ---------- */
.bee-onboarding__breath {
  position: relative;
  width: 96px;
  height: 111px;
  margin: 0 auto var(--sp-6);
  background: var(--honey-300);
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
  animation: bee-breathe 2.4s var(--ease-in-out) infinite;
}

.bee-onboarding__breath-inner {
  position: absolute;
  inset: 2px;
  background: var(--honey-100);
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
}

@media (prefers-reduced-motion: reduce) {
  .bee-onboarding__breath {
    opacity: 1;
    animation: none;
  }
}

.bee-onboarding__pending-title {
  font-size: 20px;
  line-height: 28px;
  font-weight: 600;
  color: var(--text-primary);
  text-align: center;
}

.bee-onboarding__pending-meta {
  margin-top: var(--sp-2);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
  text-align: center;
}

.bee-onboarding__exits {
  display: flex;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
}

.bee-onboarding__exits :deep(.bee-btn) {
  flex: 1;
  min-width: 0;
}

.bee-onboarding__withdraw {
  margin-top: var(--sp-4);
}

.bee-onboarding__note {
  margin-top: var(--sp-6);
  text-align: center;
}

/* ---------- 状态 C ---------- */
.bee-onboarding__result {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--sp-2);
  min-height: 120px;
  margin-top: var(--sp-4);
  padding: var(--sp-4);
  border-left: 3px solid var(--color-danger);
  border-radius: var(--r-md);
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
}

.bee-onboarding__result strong {
  font-size: var(--fs-body-lg);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-onboarding__result > span {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-onboarding__reason {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-secondary);
}

.bee-onboarding__result-meta,
.bee-onboarding__rate {
  margin: var(--sp-3) 0;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-onboarding__rate {
  color: var(--color-warning);
}

@media (max-width: 1023px) {
  .bee-onboarding__brand {
    display: none;
  }

  .bee-onboarding__mobile-logo {
    display: flex;
  }
}

@media (max-width: 639px) {
  .bee-onboarding__top {
    padding: 0 var(--sp-4);
  }

  .bee-onboarding__content {
    width: 100%;
    max-width: calc(100% - 32px);
  }

  .bee-onboarding__card {
    padding: var(--sp-4);
  }

  .bee-onboarding__exits {
    flex-direction: column;
  }
}
</style>
