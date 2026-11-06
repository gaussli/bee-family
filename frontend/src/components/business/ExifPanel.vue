<script setup lang="ts">
/**
 * 灯箱信息栏「拍摄信息」区块（docs/06 §8；docs/07 §9.3 / §9.9）。
 *
 * <p>19 字段按 A 基础区（相机 / 镜头 / 参数）→ C 文件与位置区（文件 / 地点 / GPS 坐标）
 * → B 展开区 → 下载入口的固定顺序展示；**缺字段一律整行省略，不渲染 `—`、不留空行**。</p>
 *
 * <p>GPS 展示十进制度**原文**（不四舍五入 / 不补零 / 不换算度分秒）、不接地理编码、
 * 不提供任何开关、不做角色判断。HEIC 的 EXIF 一律取自原始文件，展示副本不参与解析。</p>
 *
 * <p>三种缺失文案 + 解析异常文案按 `parseStatus` 与 `hasExif` 判定，彼此不串台；
 * 任何解析失败都不得阻断灯箱打开或照片浏览。</p>
 */
import { computed, ref } from 'vue'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import { HEIC_COPY_TEXT } from '@/utils/validate'
import { cameraName, exposureLine, fileDimension, gpsRaw, pairText } from '@/utils/format'
import { downloadEntries, fileUrl, type DownloadEntry } from '@/utils/fileUrl'
import type { ExifResp, PhotoDetail } from '@/types/models'

interface Props {
  /** 照片详情；`exif` 字段缺失（undefined）表示后端尚未返回，不误报「无 EXIF」。 */
  detail: PhotoDetail | null
  /** 用户手填地点（`02` §4.11 原有行，非 EXIF）。 */
  locationName?: string | null
  /** 详情请求失败（读取报错但图像可解码）。 */
  parseFailed?: boolean
  /** 详情不可用时下载入口的兜底 key。 */
  fallbackPreviewKey?: string | null
}

const props = withDefaults(defineProps<Props>(), {
  locationName: null,
  parseFailed: false,
  fallbackPreviewKey: null,
})

const emit = defineEmits<{ retry: [] }>()

interface ExifRow {
  label: string
  value: string
}

const expanded = ref<boolean>(false)

const exif = computed<ExifResp | null>(() => props.detail?.exif ?? null)
/** 只有后端明确下发 `exif` 字段（值可为 null）时才渲染拍摄信息 / 缺失文案。 */
const hasExifField = computed<boolean>(() => props.detail !== null && props.detail.exif !== undefined)

const cameraText = computed<string>(() => cameraName(exif.value?.make, exif.value?.model))

function fNumberText(value: string | null | undefined): string {
  const raw = (value ?? '').trim()
  if (!raw) return ''
  return raw.toLowerCase().startsWith('f/') ? raw : `f/${raw}`
}

function shutterText(value: string | null | undefined): string {
  const raw = (value ?? '').trim()
  if (!raw) return ''
  return /s(ec)?$/i.test(raw) ? raw : `${raw} s`
}

function isoText(value: string | null | undefined): string {
  const raw = (value ?? '').trim()
  if (!raw) return ''
  return raw.toUpperCase().startsWith('ISO') ? raw : `ISO ${raw}`
}

const exposureText = computed<string>(() =>
  exposureLine([
    fNumberText(exif.value?.fNumber),
    shutterText(exif.value?.exposureTime),
    isoText(exif.value?.iso),
    (exif.value?.focalLength ?? '').trim(),
  ]),
)

const fileText = computed<string>(() => {
  const detail = props.detail
  if (!detail) return ''
  const source = exif.value
  return fileDimension(
    source?.width ?? detail.width,
    source?.height ?? detail.height,
    source?.fileSize ?? detail.fileSize,
  )
})

const gpsLine = computed<string>(() => gpsRaw(exif.value?.gpsLatitudeRaw, exif.value?.gpsLongitudeRaw))
const altitudeText = computed<string>(() => {
  const altitude = exif.value?.gpsAltitude
  return altitude === null || altitude === undefined ? '' : `海拔 ${altitude} m`
})

const aRows = computed<ExifRow[]>(() => {
  const rows: ExifRow[] = []
  if (cameraText.value) rows.push({ label: '相机', value: cameraText.value })
  if ((exif.value?.lensModel ?? '').trim()) rows.push({ label: '镜头', value: exif.value?.lensModel?.trim() ?? '' })
  if (exposureText.value) rows.push({ label: '参数', value: exposureText.value })
  return rows
})

const cRows = computed<ExifRow[]>(() => {
  const rows: ExifRow[] = []
  if (fileText.value) rows.push({ label: '文件', value: fileText.value })
  if ((props.locationName ?? '').trim()) rows.push({ label: '地点', value: props.locationName?.trim() ?? '' })
  return rows
})

const bRows = computed<ExifRow[]>(() => {
  const source = exif.value
  if (!source) return []
  const rows: ExifRow[] = []
  if ((source.focalLength35mm ?? '').trim()) {
    rows.push({ label: '等效焦距', value: `等效 ${source.focalLength35mm?.trim()}` })
  }
  if ((source.exposureBias ?? '').trim()) rows.push({ label: '曝光补偿', value: source.exposureBias?.trim() ?? '' })
  const modes = pairText(source.exposureMode, source.meteringMode)
  if (modes) rows.push({ label: '曝光 / 测光', value: modes })
  const light = pairText(source.whiteBalance, source.flash)
  if (light) rows.push({ label: '白平衡 / 闪光', value: light })
  if ((source.originalFileName ?? '').trim()) {
    rows.push({ label: '原始文件名', value: source.originalFileName?.trim() ?? '' })
  }
  const software = pairText(source.software, source.modifyTime)
  if (software) rows.push({ label: '软件 / 修改', value: software })
  return rows
})

const hasAnyRow = computed<boolean>(() => aRows.value.length > 0 || cRows.value.length > 0 || gpsLine.value !== '')
const showExpand = computed<boolean>(() => bRows.value.length > 0)

type MissingKind = 'absent' | 'stripped' | 'failed' | null
const missingKind = computed<MissingKind>(() => {
  if (props.parseFailed || exif.value?.parseStatus === 'failed') return 'failed'
  if (!hasExifField.value) return null
  if (!exif.value) return 'absent'
  if (exif.value.parseStatus === 'stripped' || exif.value.exifStripped) return 'stripped'
  if (exif.value.parseStatus === 'absent' || !exif.value.hasExif || !hasAnyRow.value) return 'absent'
  return null
})

const missingText = computed<string>(() => {
  if (missingKind.value === 'absent') return '这张照片没有拍摄参数（可能是截图或经过转发的图片）'
  if (missingKind.value === 'stripped') return '拍摄参数已被清除（常见于聊天软件转发，或分享时开启了隐私保护）'
  if (missingKind.value === 'failed') return '拍摄参数读取失败，不影响照片浏览'
  return ''
})

const isHeic = computed<boolean>(() => {
  const detail = props.detail
  if (!detail) return false
  return (detail.displayKey !== null && detail.displayKey !== undefined) || detail.sourceFormat === 'heic'
})

const entries = computed<DownloadEntry[]>(() => {
  const list = downloadEntries(props.detail)
  if (list.length > 0) return list
  if (!props.fallbackPreviewKey) return []
  return [
    {
      label: '下载原图',
      href: fileUrl(props.fallbackPreviewKey, 'preview'),
      variant: 'secondary',
      original: true,
    },
  ]
})

function triggerDownload(entry: DownloadEntry): void {
  if (!entry.href) return
  const anchor = document.createElement('a')
  anchor.href = entry.href
  anchor.download = ''
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
}

</script>

<template>
  <section class="bee-exif" aria-label="拍摄信息">
    <div class="bee-exif__divider" />
    <h3 class="bee-exif__group-title">拍摄信息</h3>

    <!-- 三种缺失文案 + 解析异常：替代 A / C / 展开区 -->
    <template v-if="missingKind">
      <p class="bee-exif__missing">{{ missingText }}</p>
      <button v-if="missingKind === 'failed'" class="bee-exif__retry" type="button" @click="emit('retry')">重试</button>
    </template>

    <template v-else>
      <!-- A 基础区 -->
      <dl v-if="aRows.length" class="bee-exif__rows">
        <div v-for="row in aRows" :key="`a-${row.label}`" class="bee-exif__row">
          <dt>{{ row.label }}</dt>
          <dd>{{ row.value }}</dd>
        </div>
      </dl>

      <div v-if="aRows.length && (cRows.length || gpsLine)" class="bee-exif__divider bee-exif__divider--inner" />

      <!-- C 文件与位置区 -->
      <dl v-if="cRows.length || gpsLine" class="bee-exif__rows">
        <div v-for="row in cRows" :key="`c-${row.label}`" class="bee-exif__row">
          <dt>{{ row.label }}</dt>
          <dd>{{ row.value }}</dd>
        </div>
        <div v-if="gpsLine" class="bee-exif__row">
          <dt>GPS 坐标</dt>
          <dd>
            <span class="bee-exif__gps">{{ gpsLine }}</span>
            <span v-if="altitudeText" class="bee-exif__altitude">{{ altitudeText }}</span>
          </dd>
        </div>
      </dl>

      <!-- B 展开区：仅当至少 1 个 B 类字段有值时渲染 -->
      <template v-if="showExpand">
        <button class="bee-exif__expand" type="button" :aria-expanded="expanded" @click="expanded = !expanded">
          查看完整拍摄参数
          <BeeIcon name="chevron-down" :size="16" :class="{ 'is-open': expanded }" />
        </button>
        <dl v-if="expanded" class="bee-exif__rows">
          <div v-for="row in bRows" :key="`b-${row.label}`" class="bee-exif__row">
            <dt>{{ row.label }}</dt>
            <dd>{{ row.value }}</dd>
          </div>
        </dl>
      </template>
    </template>

    <!-- 下载入口：HEIC 双按钮、非 HEIC 单按钮；不提供「查看原图」、不提供任何开关 -->
    <div class="bee-exif__divider" />
    <div class="bee-exif__downloads" :class="{ 'is-dual': entries.length > 1 }">
      <BeeButton
        v-for="entry in entries"
        :key="entry.label"
        :variant="entry.variant"
        size="sm"
        block
        @click="triggerDownload(entry)"
      >
        {{ entry.label }}
      </BeeButton>
    </div>
    <p v-if="isHeic" class="bee-exif__hint">
      {{ HEIC_COPY_TEXT }}；JPG 用于浏览，原始 HEIC 可单独下载
    </p>
  </section>
</template>

<style scoped>
.bee-exif {
  margin-top: var(--sp-5);
}

.bee-exif__divider {
  height: 1px;
  margin: var(--sp-3) 0;
  background: var(--border-subtle);
}

.bee-exif__divider--inner {
  margin: var(--sp-3) 0;
}

.bee-exif__group-title {
  font-size: var(--fs-caption);
  font-weight: 500;
  letter-spacing: 0.06em;
  color: var(--text-tertiary);
}

.bee-exif__rows {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.bee-exif__row {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
}

.bee-exif__row dt {
  width: 64px;
  flex: none;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.bee-exif__row dd {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-secondary);
  overflow-wrap: anywhere;
}

.bee-exif__gps {
  display: block;
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.bee-exif__altitude {
  display: block;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.bee-exif__missing {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.bee-exif__retry {
  margin-top: var(--sp-1);
  font-size: var(--fs-caption);
  color: var(--honey-700);
}

.bee-exif__retry:hover {
  text-decoration: underline;
}

.bee-exif__expand {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  margin-top: var(--sp-3);
  font-size: 13px;
  color: var(--honey-700);
}

.bee-exif__expand :deep(svg) {
  transition: transform var(--dur-base) var(--ease-out);
}

.bee-exif__expand :deep(svg.is-open) {
  transform: rotate(180deg);
}

.bee-exif__downloads {
  display: flex;
  gap: var(--sp-2);
}

.bee-exif__downloads :deep(.bee-btn) {
  flex: 1;
  min-width: 0;
}

.bee-exif__hint {
  margin-top: var(--sp-2);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}
</style>
