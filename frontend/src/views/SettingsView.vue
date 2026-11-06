<script setup lang="ts">
/**
 * 设置（02 文档 §5.10）。
 *
 * <p>左侧 200px 子导航（`?tab=` 同步）、右侧内容区；四个分区：账号 / 家庭 / 数据 / 关于。
 * 危险区「删除家庭」需输入家庭名完全一致才可点击，后端会二次校验。</p>
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BeeAvatar from '@/components/base/BeeAvatar.vue'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeCard from '@/components/base/BeeCard.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeInput from '@/components/base/BeeInput.vue'
import BeeModal from '@/components/base/BeeModal.vue'
import BeePageHeader from '@/components/base/BeePageHeader.vue'
import * as familyApi from '@/api/family'
import * as statsApi from '@/api/stats'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useFamilyStore } from '@/stores/family'
import { useToast } from '@/composables/useToast'
import { formatBytes, formatNumber } from '@/utils/format'
import { SUPPORTED_FORMAT_TEXT, validateNickname, validatePassword } from '@/utils/validate'
import type { Overview } from '@/types/models'

/** `/settings/account` 为 blank 布局：只渲染账号子页，其余 3 个子页 / 左导航 / 危险区隐藏（Q9）。 */
const props = withDefaults(defineProps<{ accountOnly?: boolean }>(), { accountOnly: false })

type TabKey = 'account' | 'family' | 'data' | 'about'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const family = useFamilyStore()
const toast = useToast()

const tabs: { key: TabKey; label: string }[] = [
  { key: 'account', label: '账号' },
  { key: 'family', label: '家庭' },
  { key: 'data', label: '数据' },
  { key: 'about', label: '关于' },
]

const activeTab = ref<TabKey>('account')

const account = reactive({ nickname: '', oldPassword: '', newPassword: '' })
const accountErrors = reactive({ nickname: '', newPassword: '' })
const savingAccount = ref<boolean>(false)

const familyName = ref<string>('')
const savingFamily = ref<boolean>(false)

const overview = ref<Overview | null>(null)

const deleteOpen = ref<boolean>(false)
const deleteConfirm = ref<string>('')
const deleting = ref<boolean>(false)

const nicknameDisplay = computed<string>(() => auth.nickname || '我')
const familyTitle = computed<string>(() => family.family?.name ?? '我的家庭')

const storagePercent = computed<number>(() => {
  const used = overview.value?.storageBytes ?? 0
  const limit = overview.value?.storageLimitBytes ?? 0
  if (limit <= 0) return 0
  return Math.min(100, Math.round((used / limit) * 100))
})

const canDelete = computed<boolean>(
  () => deleteConfirm.value.trim() === familyTitle.value && familyTitle.value.length > 0,
)

function syncTab(key: TabKey): void {
  activeTab.value = key
  void router.replace({ name: 'settings', query: { tab: key } })
}

async function saveAccount(): Promise<void> {
  accountErrors.nickname = validateNickname(account.nickname)
  accountErrors.newPassword = account.newPassword ? validatePassword(account.newPassword, true) : ''
  if (accountErrors.nickname || accountErrors.newPassword) return

  savingAccount.value = true
  try {
    await auth.updateProfile({ nickname: account.nickname.trim() })
    if (account.newPassword) {
      await auth.changePassword(account.oldPassword, account.newPassword)
      // 改密码会让旧 token 全部失效，需要重新登录
      toast.success('密码已修改，请重新登录')
      await router.replace({ name: 'login' })
      return
    }
    toast.success('已保存')
    account.oldPassword = ''
  } catch (error) {
    if (error instanceof ApiError && error.code === 40102) {
      toast.danger('原密码不正确')
      return
    }
    toast.danger(error instanceof ApiError ? error.message : '保存失败，请重试')
  } finally {
    savingAccount.value = false
  }
}

async function saveFamily(): Promise<void> {
  if (!familyName.value.trim()) {
    toast.danger('家庭名称不能为空')
    return
  }
  savingFamily.value = true
  try {
    await family.updateFamily(familyName.value.trim())
    toast.success('已保存')
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '保存失败')
  } finally {
    savingFamily.value = false
  }
}

async function confirmDelete(): Promise<void> {
  if (!canDelete.value) return
  deleting.value = true
  try {
    await familyApi.deleteFamily(deleteConfirm.value.trim())
    toast.success('家庭已删除')
    auth.clearLocal()
    family.reset()
    await router.replace({ name: 'login' })
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '删除失败')
  } finally {
    deleting.value = false
  }
}

async function load(): Promise<void> {
  account.nickname = auth.nickname
  if (props.accountOnly) return
  familyName.value = family.family?.name ?? ''
  if (!family.family) {
    try {
      const loaded = await family.fetchFamily()
      familyName.value = loaded.name
    } catch {
      // 忽略
    }
  }
  try {
    overview.value = await statsApi.getOverview()
  } catch {
    overview.value = null
  }
}

watch(
  () => route.query.tab,
  (value) => {
    if (props.accountOnly) {
      activeTab.value = 'account'
      return
    }
    const key = typeof value === 'string' ? (value as TabKey) : 'account'
    if (tabs.some((tab) => tab.key === key)) activeTab.value = key
  },
)

watch(
  () => auth.nickname,
  (value) => {
    // 直链打开 /settings 时 authStore 的用户信息可能晚于 onMounted 才就绪（守卫里再拉 /auth/me），
    // 昵称是「复制值」而非计算值，必须跟随补填，否则输入框会一直空白
    if (value && !account.nickname) account.nickname = value
  },
)

onMounted(async () => {
  if (props.accountOnly) {
    activeTab.value = 'account'
  } else {
    const key = typeof route.query.tab === 'string' ? (route.query.tab as TabKey) : 'account'
    if (tabs.some((tab) => tab.key === key)) activeTab.value = key
  }
  await load()
})
</script>

<template>
  <div class="bee-settings-view">
    <BeePageHeader title="设置" description="账号、家庭与数据都在这里管理。" />

    <div class="bee-settings-view__layout">
      <nav v-if="!accountOnly" class="bee-settings-view__nav" aria-label="设置分区">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="bee-settings-view__nav-item"
          :class="{ 'is-active': activeTab === tab.key }"
          type="button"
          @click="syncTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </nav>

      <div class="bee-settings-view__content">
        <!-- 账号 -->
        <section v-if="activeTab === 'account' || accountOnly" class="bee-settings-view__section">
          <BeeCard>
            <template #header>
              <span>账号信息</span>
            </template>

            <div class="bee-settings-view__profile">
              <BeeAvatar :src="auth.user?.avatarUrl ?? null" :name="nicknameDisplay" :size="40" />
              <div class="bee-settings-view__profile-main">
                <strong>{{ nicknameDisplay }}</strong>
                <small>{{ auth.user?.email }}</small>
              </div>
              <BeeButton variant="secondary" size="sm" disabled title="头像上传将在下个版本支持">
                更换头像
              </BeeButton>
            </div>

            <form class="bee-settings-view__form" @submit.prevent="saveAccount">
              <BeeInput v-model="account.nickname" label="昵称" :error="accountErrors.nickname" :maxlength="32" />
              <BeeInput :model-value="auth.user?.email ?? ''" label="邮箱" disabled hint="邮箱作为登录账号，暂不支持修改" />
              <BeeInput v-model="account.oldPassword" type="password" label="原密码" placeholder="修改密码时填写" />
              <BeeInput
                v-model="account.newPassword"
                type="password"
                label="新密码"
                placeholder="至少 6 位，留空表示不修改"
                :error="accountErrors.newPassword"
              />

              <div class="bee-settings-view__form-actions">
                <BeeButton variant="ghost" type="button" @click="load">取消</BeeButton>
                <BeeButton type="submit" :loading="savingAccount">保存</BeeButton>
              </div>
            </form>
          </BeeCard>

          <!-- 静态隐私提示（D-4）：常驻、不做开关、不出现 Toggle / Checkbox -->
          <div class="bee-settings-view__static-notes">
            <p>照片的拍摄参数与位置信息对所有家庭成员可见</p>
            <p>{{ SUPPORTED_FORMAT_TEXT }}</p>
          </div>

          <div v-if="!accountOnly && auth.isOwner" class="bee-settings-view__danger">
            <div class="bee-settings-view__danger-head">
              <h2>危险区域</h2>
              <span>删除家庭 <BeeIcon name="info" :size="13" /> 不可恢复</span>
            </div>
            <p class="bee-settings-view__danger-text">
              删除后家庭内所有照片、相册、成长记录与里程碑都会被永久清除，且无法找回。
            </p>
            <BeeButton variant="danger" size="sm" @click="deleteOpen = true">删除家庭</BeeButton>
          </div>
        </section>

        <!-- 家庭 -->
        <section v-else-if="!accountOnly && activeTab === 'family'" class="bee-settings-view__section">
          <BeeCard>
            <template #header>
              <span>家庭信息</span>
            </template>
            <form class="bee-settings-view__form" @submit.prevent="saveFamily">
              <BeeInput v-model="familyName" label="家庭名称" :maxlength="40" />
              <BeeInput
                :model-value="`${family.memberCount} / ${family.memberLimit} 人`"
                label="成员数量"
                disabled
                hint="成员管理请到「家庭成员」页面"
              />
              <div class="bee-settings-view__form-actions">
                <RouterLink to="/family"><BeeButton variant="secondary" type="button">管理成员</BeeButton></RouterLink>
                <BeeButton type="submit" :loading="savingFamily">保存</BeeButton>
              </div>
            </form>
          </BeeCard>
        </section>

        <!-- 数据 -->
        <section v-else-if="!accountOnly && activeTab === 'data'" class="bee-settings-view__section">
          <BeeCard>
            <template #header>
              <span>存储占用</span>
            </template>

            <div class="bee-settings-view__storage">
              <div class="bee-settings-view__storage-bar">
                <span :style="{ width: `${storagePercent}%` }" />
              </div>
              <p class="bee-settings-view__storage-text">
                已用 {{ formatBytes(overview?.storageBytes ?? 0) }}
                <template v-if="overview?.storageLimitBytes">
                  / {{ formatBytes(overview.storageLimitBytes) }}
                </template>
              </p>
            </div>

            <dl class="bee-settings-view__stats">
              <div>
                <dt>照片</dt>
                <dd>{{ formatNumber(overview?.photoCount ?? 0) }}</dd>
              </div>
              <div>
                <dt>相册</dt>
                <dd>{{ formatNumber(overview?.albumCount ?? 0) }}</dd>
              </div>
              <div>
                <dt>里程碑</dt>
                <dd>{{ formatNumber(overview?.milestoneCount ?? 0) }}</dd>
              </div>
            </dl>

            <div class="bee-settings-view__form-actions">
              <BeeButton variant="secondary" size="sm" disabled title="下个版本支持">
                导出全部照片（下个版本支持）
              </BeeButton>
            </div>
          </BeeCard>
        </section>

        <!-- 关于 -->
        <section v-else-if="!accountOnly" class="bee-settings-view__section">
          <BeeCard>
            <template #header>
              <span>关于蜜记</span>
            </template>
            <p class="bee-settings-view__about">
              蜜记 · Bee Family 是一个只为家人准备的小相册：照片按时间成轴，宝宝的成长有曲线，
              里程碑不会忘，家人共用一本。
            </p>
            <dl class="bee-settings-view__stats">
              <div>
                <dt>版本</dt>
                <dd>v1.0.0</dd>
              </div>
              <div>
                <dt>技术栈</dt>
                <dd>Vue 3 · TypeScript · Spring Boot</dd>
              </div>
            </dl>
          </BeeCard>
        </section>
      </div>
    </div>

    <BeeModal
      v-model:open="deleteOpen"
      title="删除家庭"
      size="sm"
      danger
      :loading="deleting"
      confirm-text="永久删除"
      @confirm="confirmDelete"
    >
      <p class="bee-settings-view__modal-text">
        此操作会永久删除家庭 <strong>{{ familyTitle }}</strong> 的全部数据。
        请输入家庭名称以确认：
      </p>
      <BeeInput v-model="deleteConfirm" label="家庭名称" :placeholder="familyTitle" />
    </BeeModal>
  </div>
</template>

<style scoped>
.bee-settings-view__layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: var(--sp-6);
  align-items: start;
}

.bee-settings-view__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.bee-settings-view__nav-item {
  height: 40px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-md);
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-secondary);
  text-align: left;
  transition:
    background-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.bee-settings-view__nav-item:hover {
  background: var(--honey-50);
  color: var(--text-primary);
}

.bee-settings-view__nav-item.is-active {
  background: var(--honey-100);
  color: var(--text-brand);
  font-weight: 600;
}

.bee-settings-view__section {
  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
  max-width: 560px;
}

.bee-settings-view__profile {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding-bottom: var(--sp-4);
  border-bottom: 1px solid var(--border-subtle);
}

.bee-settings-view__profile-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.bee-settings-view__profile-main strong {
  font-size: var(--fs-body-lg);
  color: var(--text-primary);
}

.bee-settings-view__profile-main small {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-settings-view__form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  margin-top: var(--sp-4);
}

.bee-settings-view__form-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}

.bee-settings-view__static-notes {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-md);
  background: var(--bg-sunken);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.bee-settings-view__danger {
  padding: var(--sp-4);
  border-top: 1px solid var(--color-danger);
  border-radius: var(--r-md);
  background: var(--color-danger-bg);
}

.bee-settings-view__danger-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
}

.bee-settings-view__danger-head h2 {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--color-danger);
}

.bee-settings-view__danger-head span {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: var(--fs-caption);
  color: var(--color-danger);
}

.bee-settings-view__danger-text {
  margin: var(--sp-2) 0 var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-secondary);
}

.bee-settings-view__storage {
  margin-bottom: var(--sp-4);
}

.bee-settings-view__storage-bar {
  height: 8px;
  border-radius: var(--r-full);
  background: var(--warm-100);
  overflow: hidden;
}

.bee-settings-view__storage-bar span {
  display: block;
  height: 100%;
  border-radius: var(--r-full);
  background: var(--honey-500);
}

.bee-settings-view__storage-text {
  margin-top: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.bee-settings-view__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}

.bee-settings-view__stats div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.bee-settings-view__stats dt {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-settings-view__stats dd {
  font-size: var(--fs-body-lg);
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.bee-settings-view__about {
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
}

.bee-settings-view__modal-text {
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
}

.bee-settings-view__modal-text strong {
  color: var(--text-primary);
}

@media (max-width: 1023px) {
  .bee-settings-view__layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .bee-settings-view__nav {
    flex-direction: row;
    overflow-x: auto;
    gap: var(--sp-2);
    padding-bottom: var(--sp-2);
  }

  .bee-settings-view__nav-item {
    white-space: nowrap;
  }

  .bee-settings-view__section {
    max-width: none;
  }
}
</style>
