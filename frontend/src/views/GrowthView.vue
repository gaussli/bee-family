<script setup lang="ts">
/**
 * 成长记录（02 文档 §5.6）。
 *
 * <p>结构：宝宝切换器 → 指标卡（身高/体重/头围 + 环比）→ 曲线卡（分段范围）→
 * 左「测量记录」列表（8 列）+ 右「里程碑」（4 列）。记录表单用右侧抽屉。</p>
 *
 * <p>新增与编辑共用抽屉：编辑走 `PATCH /growth-records/:id`，新增走
 * `PUT /babies/:id/growth-records`（同一天 UPSERT，天然防重复录入）。</p>
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeDrawer from '@/components/base/BeeDrawer.vue'
import BeeEmpty from '@/components/base/BeeEmpty.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeInput from '@/components/base/BeeInput.vue'
import BeePageHeader from '@/components/base/BeePageHeader.vue'
import BeeSkeleton from '@/components/base/BeeSkeleton.vue'
import BeeTabs from '@/components/base/BeeTabs.vue'
import BabySwitcher from '@/components/business/BabySwitcher.vue'
import GrowthChart from '@/components/business/GrowthChart.vue'
import MilestoneCard from '@/components/business/MilestoneCard.vue'
import MilestoneModal from '@/components/business/MilestoneModal.vue'
import * as growthApi from '@/api/growth'
import * as milestoneApi from '@/api/milestone'
import { ApiError } from '@/api/http'
import { useFamilyStore } from '@/stores/family'
import { useToast } from '@/composables/useToast'
import { formatDate, formatDecimal, todayString } from '@/utils/format'
import { validateRange } from '@/utils/validate'
import type { GrowthCurve, GrowthRange, GrowthRecord, Milestone } from '@/types/models'

const router = useRouter()
const family = useFamilyStore()
const toast = useToast()

const curve = ref<GrowthCurve | null>(null)
const records = ref<GrowthRecord[]>([])
const milestones = ref<Milestone[]>([])
const range = ref<GrowthRange>('all')
const loading = ref<boolean>(true)
const recordsLoading = ref<boolean>(false)
const milestonesLoading = ref<boolean>(false)

const drawerOpen = ref<boolean>(false)
const editingId = ref<number | null>(null)
const saving = ref<boolean>(false)
const milestoneOpen = ref<boolean>(false)

const form = reactive({
  recordedAt: todayString(),
  heightCm: '',
  weightKg: '',
  headCm: '',
  note: '',
})

const errors = reactive({ heightCm: '', weightKg: '', headCm: '' })

const rangeTabs = [
  { key: '3m', label: '3 月' },
  { key: '6m', label: '6 月' },
  { key: '12m', label: '12 月' },
  { key: 'all', label: '全部' },
]

const activeBabyId = computed<number | null>(() => family.activeBabyId)
const babies = computed(() => family.babies)
const hasBaby = computed<boolean>(() => babies.value.length > 0)

const latest = computed(() => curve.value?.latest ?? null)
const delta = computed(() => curve.value?.delta ?? { heightCm: null, weightKg: null, headCm: null })

/**
 * 窄屏用的短日期（"2026-10-24" → "10月24日"）。
 *
 * <p>桌面显示完整年月日，移动端省去年份，保证日期列永不折行（02 文档 T24-⑩）。</p>
 *
 * @param input 后端日期串
 */
function formatShortDate(input: string): string {
  const [, month, day] = input.slice(0, 10).split('-')
  if (!month || !day) return input
  return `${Number(month)}月${Number(day)}日`
}

const metricCards = computed(() => [
  {
    key: 'height',
    label: '身高',
    value: latest.value?.heightCm ?? null,
    unit: 'cm',
    delta: delta.value.heightCm,
  },
  {
    key: 'weight',
    label: '体重',
    value: latest.value?.weightKg ?? null,
    unit: 'kg',
    delta: delta.value.weightKg,
  },
  {
    key: 'head',
    label: '头围',
    value: latest.value?.headCm ?? null,
    unit: 'cm',
    delta: delta.value.headCm,
  },
])

const babyOptions = computed(() => babies.value.map((baby) => ({ id: baby.id, name: baby.name })))

async function loadCurve(): Promise<void> {
  if (!activeBabyId.value) {
    curve.value = null
    loading.value = false
    return
  }
  loading.value = true
  try {
    curve.value = await growthApi.getCurve(activeBabyId.value, range.value)
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '曲线加载失败')
    curve.value = null
  } finally {
    loading.value = false
  }
}

async function loadRecords(): Promise<void> {
  if (!activeBabyId.value) {
    records.value = []
    return
  }
  recordsLoading.value = true
  try {
    const page = await growthApi.listRecords(activeBabyId.value, 0, 20)
    records.value = page.items
  } catch {
    records.value = []
  } finally {
    recordsLoading.value = false
  }
}

async function loadMilestones(): Promise<void> {
  milestonesLoading.value = true
  try {
    const page = await milestoneApi.listMilestones(activeBabyId.value, 0, 5)
    milestones.value = page.items
  } catch {
    milestones.value = []
  } finally {
    milestonesLoading.value = false
  }
}

function onRangeChange(key: string): void {
  range.value = key as GrowthRange
  void loadCurve()
}

function onBabyChange(id: number): void {
  family.setActiveBaby(id)
}

function openCreate(): void {
  editingId.value = null
  form.recordedAt = todayString()
  form.heightCm = ''
  form.weightKg = ''
  form.headCm = ''
  form.note = ''
  Object.assign(errors, { heightCm: '', weightKg: '', headCm: '' })
  drawerOpen.value = true
}

function openEdit(record: GrowthRecord): void {
  editingId.value = record.id
  form.recordedAt = record.recordedAt
  form.heightCm = record.heightCm === null ? '' : String(record.heightCm)
  form.weightKg = record.weightKg === null ? '' : String(record.weightKg)
  form.headCm = record.headCm === null ? '' : String(record.headCm)
  form.note = record.note ?? ''
  Object.assign(errors, { heightCm: '', weightKg: '', headCm: '' })
  drawerOpen.value = true
}

function validate(): boolean {
  errors.heightCm = validateRange(form.heightCm, 20, 200, '身高')
  errors.weightKg = validateRange(form.weightKg, 0.5, 80, '体重')
  errors.headCm = validateRange(form.headCm, 20, 70, '头围')
  if (!form.heightCm && !form.weightKg && !form.headCm) {
    errors.heightCm = '至少填写一项测量数据'
  }
  return !errors.heightCm && !errors.weightKg && !errors.headCm
}

async function onSave(): Promise<void> {
  if (!activeBabyId.value) return
  if (!validate()) return
  saving.value = true
  try {
    const body: growthApi.GrowthBody = {
      recordedAt: form.recordedAt,
      heightCm: form.heightCm ? Number(form.heightCm) : null,
      weightKg: form.weightKg ? Number(form.weightKg) : null,
      headCm: form.headCm ? Number(form.headCm) : null,
      note: form.note.trim() || null,
    }
    if (editingId.value !== null) {
      await growthApi.updateRecord(editingId.value, body)
      toast.success('记录已更新')
    } else {
      await growthApi.upsertRecord(activeBabyId.value, body)
      toast.success('记录已保存')
    }
    drawerOpen.value = false
    await Promise.all([loadCurve(), loadRecords()])
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '保存失败，请重试')
  } finally {
    saving.value = false
  }
}

async function onDelete(record: GrowthRecord): Promise<void> {
  try {
    await growthApi.deleteRecord(record.id)
    toast.success('记录已删除')
    await Promise.all([loadCurve(), loadRecords()])
  } catch (error) {
    toast.danger(error instanceof ApiError ? error.message : '删除失败')
  }
}

function goAddBaby(): void {
  void router.push({ name: 'family' })
}

watch(
  () => family.activeBabyId,
  async () => {
    await Promise.all([loadCurve(), loadRecords(), loadMilestones()])
  },
)

onMounted(async () => {
  if (family.babies.length === 0) {
    try {
      await family.fetchBabies()
    } catch {
      // 忽略：下面会显示空态
    }
    if (family.babies.length > 0 && family.activeBabyId === null) {
      const first = family.babies[0]
      if (first) family.setActiveBaby(first.id)
    }
  }
  await Promise.all([loadCurve(), loadRecords(), loadMilestones()])
})
</script>

<template>
  <div class="bee-growth-view">
    <BeePageHeader title="成长记录" description="身高、体重、头围，一条曲线看成长。">
      <template #actions>
        <BabySwitcher
          v-if="hasBaby"
          :babies="babies"
          :model-value="activeBabyId ?? 0"
          allow-add
          @update:model-value="onBabyChange"
          @add="goAddBaby"
        />
        <BeeButton v-if="hasBaby" @click="openCreate">
          <template #icon><BeeIcon name="plus" :size="16" /></template>
          记录身高体重
        </BeeButton>
      </template>
    </BeePageHeader>

    <BeeEmpty
      v-if="!hasBaby"
      variant="no-growth"
      title="还没有宝宝档案"
      description="先创建宝宝档案，才能记录身高体重与里程碑。"
      action-text="去添加宝宝"
      @action="goAddBaby"
    />

    <template v-else>
      <div class="bee-growth-view__metrics">
        <div v-for="card in metricCards" :key="card.key" class="bee-growth-view__metric">
          <span class="bee-growth-view__metric-label">{{ card.label }}</span>
          <strong class="bee-growth-view__metric-value">
            {{ card.value === null ? '—' : formatDecimal(card.value) }}
            <small>{{ card.unit }}</small>
          </strong>
          <span
            class="bee-growth-view__metric-delta"
            :class="{ 'is-down': card.delta !== null && card.delta < 0 }"
          >
            <template v-if="card.delta !== null">
              {{ card.delta >= 0 ? '+' : '' }}{{ formatDecimal(card.delta) }} 较上次
            </template>
            <template v-else>暂无对比</template>
          </span>
        </div>
      </div>

      <section class="bee-growth-view__chart-card">
        <header class="bee-growth-view__chart-head">
          <h2 class="bee-growth-view__card-title">成长曲线</h2>
          <BeeTabs :model-value="range" :items="rangeTabs" variant="segment" @update:model-value="onRangeChange" />
        </header>
        <GrowthChart :points="curve?.points ?? []" :range="range" :loading="loading" @record="openCreate" />
      </section>

      <div class="bee-growth-view__columns">
        <section class="bee-growth-view__records">
          <header class="bee-growth-view__section-head">
            <h2 class="bee-growth-view__card-title">测量记录</h2>
            <span class="bee-growth-view__count">{{ records.length }} 条</span>
          </header>

          <BeeSkeleton v-if="recordsLoading" variant="block" :count="4" height="44px" />

          <BeeEmpty
            v-else-if="records.length === 0"
            variant="no-growth"
            title="还没有测量记录"
            description="记录第一次测量，曲线就会从这里开始。"
            action-text="记录第一次测量"
            compact
            @action="openCreate"
          />

          <ul v-else class="bee-growth-view__record-list">
            <li v-for="record in records" :key="record.id" class="bee-growth-view__record">
              <span class="bee-growth-view__record-date">
                <span class="is-full">{{ formatDate(record.recordedAt) }}</span>
                <span class="is-short">{{ formatShortDate(record.recordedAt) }}</span>
              </span>
              <span class="bee-growth-view__record-values">
                <span>{{ record.heightCm === null ? '—' : formatDecimal(record.heightCm) }} cm</span>
                <span>{{ record.weightKg === null ? '—' : formatDecimal(record.weightKg) }} kg</span>
                <span>{{ record.headCm === null ? '—' : formatDecimal(record.headCm) }} cm</span>
              </span>
              <span class="bee-growth-view__record-actions">
                <button type="button" aria-label="编辑记录" @click="openEdit(record)">
                  <BeeIcon name="edit" :size="14" />
                </button>
                <button type="button" aria-label="删除记录" @click="onDelete(record)">
                  <BeeIcon name="trash" :size="14" />
                </button>
              </span>
            </li>
          </ul>
        </section>

        <section class="bee-growth-view__milestones">
          <header class="bee-growth-view__section-head">
            <h2 class="bee-growth-view__card-title">里程碑</h2>
            <button class="bee-growth-view__add-link" type="button" @click="milestoneOpen = true">+ 记录</button>
          </header>

          <BeeSkeleton v-if="milestonesLoading" variant="block" :count="2" height="80px" />

          <BeeEmpty
            v-else-if="milestones.length === 0"
            variant="no-growth"
            title="还没有里程碑"
            description="第一次翻身、第一次叫妈妈，都值得记下来。"
            action-text="记录里程碑"
            compact
            @action="milestoneOpen = true"
          />

          <div v-else class="bee-growth-view__milestone-list">
            <MilestoneCard
              v-for="milestone in milestones"
              :key="milestone.id"
              :milestone="milestone"
              editable
              @edit="milestoneOpen = true"
              @remove="milestoneOpen = true"
            />
          </div>
        </section>
      </div>
    </template>

    <BeeDrawer
      v-model:open="drawerOpen"
      :title="editingId === null ? '记录测量' : '编辑测量'"
      :width="400"
      :loading="saving"
      confirm-text="保存"
      @confirm="onSave"
    >
      <div class="bee-growth-view__form">
        <BeeInput v-model="form.recordedAt" type="date" label="测量日期" required />
        <BeeInput v-model="form.heightCm" type="number" label="身高（cm）" placeholder="例如 78.5" :error="errors.heightCm" />
        <BeeInput v-model="form.weightKg" type="number" label="体重（kg）" placeholder="例如 9.8" :error="errors.weightKg" />
        <BeeInput v-model="form.headCm" type="number" label="头围（cm）" placeholder="例如 46.2" :error="errors.headCm" />
        <BeeInput v-model="form.note" label="备注（选填）" :rows="3" :maxlength="200" placeholder="今天体检，医生说长得很好" />
      </div>
    </BeeDrawer>

    <MilestoneModal
      :open="milestoneOpen"
      :baby-id="activeBabyId"
      :babies="babyOptions"
      @update:open="milestoneOpen = $event"
      @saved="loadMilestones"
    />
  </div>
</template>

<style scoped>
.bee-growth-view__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-4);
  margin-bottom: var(--sp-6);
}

.bee-growth-view__metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 96px;
  padding: var(--sp-4) var(--sp-5);
  background: var(--bg-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-sm);
}

.bee-growth-view__metric-label {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-growth-view__metric-value {
  font-family: var(--font-display);
  font-size: 32px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.bee-growth-view__metric-value small {
  font-family: var(--font-sans);
  font-size: var(--fs-body);
  font-weight: 400;
  color: var(--text-tertiary);
  margin-left: 2px;
}

.bee-growth-view__metric-delta {
  font-size: var(--fs-caption);
  color: var(--color-success);
}

.bee-growth-view__metric-delta.is-down {
  color: var(--text-tertiary);
}

.bee-growth-view__chart-card {
  padding: var(--sp-5) var(--sp-5) var(--sp-2);
  margin-bottom: var(--sp-6);
  background: var(--bg-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-sm);
}

.bee-growth-view__chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  min-height: 48px;
}

.bee-growth-view__card-title {
  font-size: var(--fs-h4);
  line-height: var(--lh-h4);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-growth-view__columns {
  display: grid;
  grid-template-columns: minmax(0, 8fr) minmax(0, 4fr);
  gap: var(--sp-6);
}

.bee-growth-view__records,
.bee-growth-view__milestones {
  min-width: 0;
}

.bee-growth-view__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-3);
}

.bee-growth-view__count {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-growth-view__add-link {
  font-size: 13px;
  font-weight: 500;
  color: var(--honey-600);
}

.bee-growth-view__add-link:hover {
  color: var(--honey-700);
}

.bee-growth-view__record-list {
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.bee-growth-view__record {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  height: 52px;
  padding: 0 var(--sp-4);
  border-bottom: 1px solid var(--border-subtle);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.bee-growth-view__record:last-child {
  border-bottom: 0;
}

.bee-growth-view__record:hover {
  background: var(--honey-50);
}

.bee-growth-view__record-date {
  width: 118px;
  flex: none;
  font-size: var(--fs-body);
  color: var(--text-primary);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.bee-growth-view__record-date .is-short {
  display: none;
}

.bee-growth-view__record-values {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-2);
  flex: 1;
  font-size: 13px;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}

.bee-growth-view__record-actions {
  display: flex;
  gap: var(--sp-1);
  opacity: 0;
  transition: opacity var(--dur-fast) var(--ease-out);
}

.bee-growth-view__record:hover .bee-growth-view__record-actions {
  opacity: 1;
}

.bee-growth-view__record-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: var(--r-sm);
  color: var(--text-tertiary);
}

.bee-growth-view__record-actions button:hover {
  background: var(--warm-100);
  color: var(--text-primary);
}

.bee-growth-view__milestone-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.bee-growth-view__form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

@media (max-width: 1023px) {
  .bee-growth-view__columns {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 767px) {
  .bee-growth-view__metrics {
    grid-template-columns: repeat(3, 140px);
    overflow-x: auto;
    padding-bottom: var(--sp-1);
  }

  .bee-growth-view__record-values {
    font-size: 12px;
  }

  .bee-growth-view__record-date {
    width: 88px;
    font-size: 13px;
  }

  .bee-growth-view__record-date .is-full {
    display: none;
  }

  .bee-growth-view__record-date .is-short {
    display: inline;
  }

  .bee-growth-view__record-actions {
    opacity: 1;
  }
}
</style>
