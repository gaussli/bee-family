<script setup lang="ts">
/**
 * 宝宝档案（02 文档 §5.7）。
 *
 * <p>头部卡：96×111 六边形头像 + 昵称/性别/生日/已出生天数 + 编辑按钮；
 * 底部三列（出生身高 / 出生体重 / 里程碑数）。编辑态**同页切换为表单**，不跳转。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import BeeAvatar from '@/components/base/BeeAvatar.vue'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeCard from '@/components/base/BeeCard.vue'
import BeeHexBadge from '@/components/base/BeeHexBadge.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeInput from '@/components/base/BeeInput.vue'
import BeeSelect from '@/components/base/BeeSelect.vue'
import BeeSkeleton from '@/components/base/BeeSkeleton.vue'
import * as babyApi from '@/api/baby'
import { ApiError } from '@/api/http'
import { useFamilyStore } from '@/stores/family'
import { useToast } from '@/composables/useToast'
import { fileUrl } from '@/utils/fileUrl'
import { formatDate, formatDecimal } from '@/utils/format'
import type { BabyDetail, Gender } from '@/types/models'

interface Props {
  id: string
}

const props = defineProps<Props>()

const router = useRouter()
const family = useFamilyStore()
const toast = useToast()

const baby = ref<BabyDetail | null>(null)
const loading = ref<boolean>(true)
const editing = ref<boolean>(false)
const saving = ref<boolean>(false)

const form = reactive({
  name: '',
  gender: 'unknown' as Gender,
  birthday: '',
  birthHeightCm: '',
  birthWeightKg: '',
})

const errors = reactive({ name: '', birthday: '' })

const genderOptions = [
  { label: '女宝宝', value: 'female' },
  { label: '男宝宝', value: 'male' },
  { label: '暂不填写', value: 'unknown' },
]

const avatar = computed<string | null>(() =>
  baby.value?.avatarUrl ? fileUrl(baby.value.avatarUrl, 'thumb') : null,
)

const genderText = computed<string>(() => {
  if (!baby.value) return ''
  if (baby.value.gender === 'female') return '女'
  if (baby.value.gender === 'male') return '男'
  return '性别未填'
})

const ageText = computed<string>(() => {
  if (!baby.value) return ''
  return `已出生 ${baby.value.ageDays} 天`
})

const birthdayText = computed<string>(() => (baby.value ? formatDate(baby.value.birthday) : ''))

async function load(): Promise<void> {
  loading.value = true
  try {
    baby.value = await babyApi.getBaby(Number(props.id))
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '宝宝档案加载失败')
    void router.replace({ name: 'family' })
  } finally {
    loading.value = false
  }
}

function startEdit(): void {
  if (!baby.value) return
  form.name = baby.value.name
  form.gender = baby.value.gender ?? 'unknown'
  form.birthday = baby.value.birthday
  form.birthHeightCm = baby.value.birthHeightCm === null ? '' : String(baby.value.birthHeightCm)
  form.birthWeightKg = baby.value.birthWeightKg === null ? '' : String(baby.value.birthWeightKg)
  errors.name = ''
  errors.birthday = ''
  editing.value = true
}

function cancelEdit(): void {
  editing.value = false
}

async function onSave(): Promise<void> {
  errors.name = form.name.trim() ? '' : '请输入昵称'
  errors.birthday = form.birthday ? '' : '请选择生日'
  if (errors.name || errors.birthday) return

  saving.value = true
  try {
    await babyApi.updateBaby(Number(props.id), {
      name: form.name.trim(),
      gender: form.gender,
      birthday: form.birthday,
      birthHeightCm: form.birthHeightCm ? Number(form.birthHeightCm) : null,
      birthWeightKg: form.birthWeightKg ? Number(form.birthWeightKg) : null,
    })
    toast.success('宝宝资料已保存')
    editing.value = false
    await Promise.all([load(), family.fetchBabies()])
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '保存失败，请重试')
  } finally {
    saving.value = false
  }
}

function goGrowth(): void {
  family.setActiveBaby(Number(props.id))
  void router.push({ name: 'growth' })
}

onMounted(load)
</script>

<template>
  <div class="bee-baby-view">
    <nav class="bee-baby-view__breadcrumb" aria-label="面包屑">
      <RouterLink to="/family">家庭成员</RouterLink>
      <span aria-hidden="true">/</span>
      <span>宝宝档案</span>
    </nav>

    <BeeSkeleton v-if="loading" variant="block" :count="1" height="180px" radius="var(--r-lg)" />

    <BeeCard v-else-if="baby" padding="none" class="bee-baby-view__card">
      <div class="bee-baby-view__head">
        <BeeAvatar :src="avatar" :name="baby.name" shape="hex" :size="96" />

        <div class="bee-baby-view__info">
          <h1 class="bee-baby-view__name">{{ baby.name }}</h1>
          <p class="bee-baby-view__meta">{{ genderText }} · {{ birthdayText }}</p>
          <p class="bee-baby-view__age">{{ ageText }}</p>
        </div>

        <div class="bee-baby-view__actions">
          <BeeButton v-if="!editing" variant="secondary" size="sm" @click="startEdit">
            <template #icon><BeeIcon name="edit" :size="14" /></template>
            编辑资料
          </BeeButton>
          <BeeButton v-if="!editing" variant="ghost" size="sm" @click="goGrowth">看成长记录</BeeButton>
        </div>
      </div>

      <form v-if="editing" class="bee-baby-view__form" @submit.prevent="onSave">
        <BeeInput v-model="form.name" label="昵称" required :error="errors.name" :maxlength="32" />
        <BeeSelect v-model="form.gender" label="性别" :options="genderOptions" />
        <BeeInput v-model="form.birthday" type="date" label="生日" required :error="errors.birthday" />
        <BeeInput v-model="form.birthHeightCm" type="number" label="出生身高（cm）" placeholder="50.0" />
        <BeeInput v-model="form.birthWeightKg" type="number" label="出生体重（kg）" placeholder="3.35" />
        <div class="bee-baby-view__form-actions">
          <BeeButton variant="ghost" @click="cancelEdit">取消</BeeButton>
          <BeeButton type="submit" :loading="saving">保存</BeeButton>
        </div>
      </form>

      <div v-else class="bee-baby-view__stats">
        <div class="bee-baby-view__stat">
          <span class="bee-baby-view__stat-label">出生身高</span>
          <strong class="bee-baby-view__stat-value">
            {{ baby.birthHeightCm === null ? '—' : formatDecimal(baby.birthHeightCm) }} cm
          </strong>
        </div>
        <div class="bee-baby-view__stat">
          <span class="bee-baby-view__stat-label">出生体重</span>
          <strong class="bee-baby-view__stat-value">
            {{ baby.birthWeightKg === null ? '—' : formatDecimal(baby.birthWeightKg) }} kg
          </strong>
        </div>
        <div class="bee-baby-view__stat">
          <span class="bee-baby-view__stat-label">里程碑</span>
          <strong class="bee-baby-view__stat-value">{{ baby.milestoneCount }} 个</strong>
        </div>
      </div>
    </BeeCard>

    <div v-if="baby" class="bee-baby-view__panels">
      <BeeCard>
        <template #header>
          <span>最近测量</span>
          <BeeButton variant="ghost" size="sm" @click="goGrowth">查看曲线</BeeButton>
        </template>
        <div v-if="baby.latestGrowth" class="bee-baby-view__latest">
          <span class="bee-baby-view__latest-date">{{ formatDate(baby.latestGrowth.recordedAt) }}</span>
          <span class="bee-baby-view__latest-values">
            {{ baby.latestGrowth.heightCm === null ? '—' : formatDecimal(baby.latestGrowth.heightCm) }} cm ·
            {{ baby.latestGrowth.weightKg === null ? '—' : formatDecimal(baby.latestGrowth.weightKg) }} kg ·
            {{ baby.latestGrowth.headCm === null ? '—' : formatDecimal(baby.latestGrowth.headCm) }} cm
          </span>
        </div>
        <p v-else class="bee-baby-view__placeholder">还没有测量记录</p>
      </BeeCard>

      <BeeCard>
        <template #header>
          <span>最近里程碑</span>
        </template>
        <div v-if="baby.latestMilestone" class="bee-baby-view__latest">
          <BeeHexBadge :size="28" variant="coral" icon="heart" />
          <div class="bee-baby-view__latest-main">
            <strong>{{ baby.latestMilestone.title }}</strong>
            <small>{{ formatDate(baby.latestMilestone.achievedAt) }}</small>
          </div>
        </div>
        <p v-else class="bee-baby-view__placeholder">还没有里程碑</p>
      </BeeCard>
    </div>
  </div>
</template>

<style scoped>
.bee-baby-view__breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-baby-view__breadcrumb span:last-child {
  color: var(--text-secondary);
}

.bee-baby-view__card {
  overflow: hidden;
}

.bee-baby-view__head {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-5);
  padding: var(--sp-6);
}

.bee-baby-view__info {
  flex: 1;
  min-width: 0;
}

.bee-baby-view__name {
  font-family: var(--font-display);
  font-size: var(--fs-h2);
  line-height: var(--lh-h2);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-baby-view__meta {
  margin-top: var(--sp-1);
  font-size: var(--fs-body);
  color: var(--text-secondary);
}

.bee-baby-view__age {
  margin-top: var(--sp-1);
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-brand);
}

.bee-baby-view__actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex: none;
}

.bee-baby-view__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-top: 1px solid var(--border-subtle);
}

.bee-baby-view__stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--sp-4) var(--sp-6);
  border-left: 1px solid var(--border-subtle);
}

.bee-baby-view__stat:first-child {
  border-left: 0;
}

.bee-baby-view__stat-label {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-baby-view__stat-value {
  font-size: var(--fs-body-lg);
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.bee-baby-view__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-4);
  padding: var(--sp-6);
  border-top: 1px solid var(--border-subtle);
}

.bee-baby-view__form-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}

.bee-baby-view__panels {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-6);
  margin-top: var(--sp-6);
}

.bee-baby-view__latest {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.bee-baby-view__latest-date {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-baby-view__latest-values {
  font-size: var(--fs-body-lg);
  font-weight: 500;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.bee-baby-view__latest-main {
  display: flex;
  flex-direction: column;
}

.bee-baby-view__latest-main strong {
  font-size: var(--fs-body-lg);
  color: var(--text-primary);
}

.bee-baby-view__latest-main small {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-baby-view__placeholder {
  font-size: var(--fs-body);
  color: var(--text-tertiary);
}

@media (max-width: 767px) {
  .bee-baby-view__head {
    flex-wrap: wrap;
    gap: var(--sp-4);
  }

  .bee-baby-view__head :deep(.bee-avatar) {
    width: 64px !important;
    height: 74px !important;
  }

  .bee-baby-view__stats {
    grid-template-columns: minmax(0, 1fr);
  }

  .bee-baby-view__stat {
    border-left: 0;
    border-top: 1px solid var(--border-subtle);
    padding: var(--sp-3) var(--sp-4);
  }

  .bee-baby-view__stat:first-child {
    border-top: 0;
  }

  .bee-baby-view__form {
    grid-template-columns: minmax(0, 1fr);
  }

  .bee-baby-view__panels {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
