<script setup lang="ts">
/**
 * 灯箱（02 文档 §4.11）。
 *
 * <p>`role="dialog" aria-modal="true"` + 焦点陷阱；键盘 `←`/`→` 翻页、`Esc` 关闭、`I` 切换信息栏。
 * 关闭后浏览器焦点回到触发单元（由 `useFocusTrap` 记录并还原）。</p>
 *
 * <p>图像区使用 `--photo-void` 深底衬托照片；信息栏标题/描述/元信息/标签来自
 * `Photo` 列表项，拍摄信息与下载入口来自按需拉取的 `PhotoDetail`
 * （HEIC 的 EXIF 一律由服务端从原始 HEIC 读取，展示副本不参与解析）。</p>
 *
 * <p>切换家庭后 `familyEpoch` 递增，`:key` 强制重挂载图片，避免旧家庭图片残留
 * （docs/07 §10.3 第 9 / 12 项）。</p>
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeTag from '@/components/base/BeeTag.vue'
import BeeAvatar from '@/components/base/BeeAvatar.vue'
import ExifPanel from './ExifPanel.vue'
import * as photoApi from '@/api/photo'
import { useFocusTrap } from '@/composables/useFocusTrap'
import { useSwitchStore } from '@/stores/switch'
import { fileUrl } from '@/utils/fileUrl'
import { formatDateTime } from '@/utils/format'
import type { Photo, PhotoDetail } from '@/types/models'

interface Props {
  photos: Photo[]
  startIndex: number
  open: boolean
}

const props = withDefaults(defineProps<Props>(), {
  startIndex: 0,
})

const emit = defineEmits<{ 'update:open': [value: boolean]; change: [index: number] }>()

const index = ref<number>(props.startIndex)
const infoOpen = ref<boolean>(true)
const imageFailed = ref<boolean>(false)
const imageLoaded = ref<boolean>(false)
const retryNonce = ref<number>(0)
const dialogRef = ref<HTMLElement | null>(null)

const detail = ref<PhotoDetail | null>(null)
const detailFailed = ref<boolean>(false)
const detailNonce = ref<number>(0)
const switchStore = useSwitchStore()

const isOpen = computed<boolean>(() => props.open)

useFocusTrap(dialogRef, isOpen, '.bee-lightbox__close')

const current = computed<Photo | null>(() => props.photos[index.value] ?? null)
const total = computed<number>(() => props.photos.length)
const familyEpoch = computed<number>(() => switchStore.familyEpoch)

/** 拍摄时间缺失时回退到上传时间（docs/06 §8.2 #1）。 */
const takenAtText = computed<string>(() => {
  const value = formatDateTime(current.value?.takenAt)
  if (!value) return ''
  return detail.value?.exif?.takenAtFromExif === false ? `${value}（按上传时间）` : value
})
const showTakenAtHint = computed<boolean>(() => detail.value?.exif?.takenAtFromExif === false)

/** HEIC 灯箱展示 1600px JPG 展示副本；非 HEIC 走 previewKey。 */
const mainUrl = computed<string>(() => {
  const key = detail.value?.displayKey ?? current.value?.displayKey ?? current.value?.previewKey
  const base = fileUrl(key, 'preview')
  if (!base || retryNonce.value === 0) return base
  return `${base}${base.includes('?') ? '&' : '?'}retry=${retryNonce.value}`
})
const canStep = computed<boolean>(() => total.value > 1)

/**
 * 按需拉照片详情。失败只影响「拍摄信息」区块，不阻断灯箱与图片浏览
 * （docs/06 §8.5 硬性约束）。
 */
async function loadDetail(): Promise<void> {
  const photoId = current.value?.id
  if (!photoId) {
    detail.value = null
    detailFailed.value = false
    return
  }
  detailNonce.value += 1
  const nonce = detailNonce.value
  detail.value = null
  detailFailed.value = false
  try {
    const result = await photoApi.getPhoto(photoId)
    if (nonce !== detailNonce.value) return
    detail.value = result
  } catch {
    if (nonce !== detailNonce.value) return
    detail.value = null
    detailFailed.value = true
  }
}

function close(): void {
  emit('update:open', false)
}

function step(delta: number): void {
  if (!canStep.value) return
  const next = (index.value + delta + total.value) % total.value
  index.value = next
  emit('change', next)
  imageFailed.value = false
  imageLoaded.value = false
}

function jump(target: number): void {
  if (target === index.value) return
  index.value = target
  emit('change', target)
  imageFailed.value = false
  imageLoaded.value = false
}

function toggleInfo(): void {
  infoOpen.value = !infoOpen.value
}

function retryImage(): void {
  imageFailed.value = false
  imageLoaded.value = false
  retryNonce.value = Date.now()
}

function onKeydown(event: KeyboardEvent): void {
  if (!props.open) return
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    step(-1)
    return
  }
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    step(1)
    return
  }
  if (event.key === 'i' || event.key === 'I') {
    event.preventDefault()
    toggleInfo()
  }
}

watch(
  () => props.open,
  (value) => {
    if (value) {
      index.value = Math.min(Math.max(props.startIndex, 0), Math.max(total.value - 1, 0))
      imageFailed.value = false
      imageLoaded.value = false
      document.body.style.overflow = 'hidden'
      document.addEventListener('keydown', onKeydown)
    } else {
      document.body.style.overflow = ''
      document.removeEventListener('keydown', onKeydown)
    }
  },
  { immediate: true },
)

watch(
  () => props.startIndex,
  (value) => {
    if (!props.open) return
    index.value = value
  },
)

// 打开 / 翻页时重新拉详情（只拉当前这一张，EXIF 解析为尽力而为）。
watch(
  () => [props.open, current.value?.id] as const,
  ([open]) => {
    if (open) void loadDetail()
    else {
      detailNonce.value += 1
      detail.value = null
      detailFailed.value = false
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  if (props.open) document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="bee-lightbox">
      <div v-if="open" class="bee-lightbox" role="presentation" @click.self="close">
        <div ref="dialogRef" class="bee-lightbox__dialog" role="dialog" aria-modal="true" aria-label="照片查看器">
          <header class="bee-lightbox__toolbar">
            <span class="bee-lightbox__counter">
              {{ index + 1 }} / {{ total }}
              <template v-if="current?.title"> · {{ current.title }}</template>
            </span>
            <div class="bee-lightbox__tools">
              <button
                class="bee-lightbox__tool"
                type="button"
                title="收藏（下个版本支持）"
                aria-label="收藏"
                disabled
              >
                <BeeIcon name="heart" :size="18" />
              </button>
              <button
                class="bee-lightbox__tool"
                type="button"
                :aria-pressed="infoOpen"
                aria-label="切换信息栏"
                title="信息（I）"
                @click="toggleInfo"
              >
                <BeeIcon name="info" :size="18" />
              </button>
              <button class="bee-lightbox__tool bee-lightbox__close" type="button" aria-label="关闭" title="关闭（Esc）" @click="close">
                <BeeIcon name="close" :size="18" />
              </button>
            </div>
          </header>

          <div class="bee-lightbox__stage">
            <button
              v-if="canStep"
              class="bee-lightbox__arrow bee-lightbox__arrow--prev"
              type="button"
              aria-label="上一张"
              @click.stop="step(-1)"
            >
              <BeeIcon name="chevron-left" :size="20" />
            </button>

            <div class="bee-lightbox__image-area">
              <img
                v-if="mainUrl && !imageFailed"
                :key="`${familyEpoch}-${current?.id ?? 0}-${retryNonce}`"
                class="bee-lightbox__image"
                :class="{ 'is-loaded': imageLoaded }"
                :src="mainUrl"
                :alt="current?.title ?? '照片'"
                @load="imageLoaded = true"
                @error="imageFailed = true"
              />
              <div v-if="!imageLoaded && !imageFailed" class="bee-lightbox__spinner" aria-hidden="true" />
              <button v-if="imageFailed" class="bee-lightbox__retry" type="button" @click="retryImage">
                图片加载失败，点击重试
              </button>
            </div>

            <button
              v-if="canStep"
              class="bee-lightbox__arrow bee-lightbox__arrow--next"
              type="button"
              aria-label="下一张"
              @click.stop="step(1)"
            >
              <BeeIcon name="chevron-right" :size="20" />
            </button>

            <aside v-if="infoOpen" class="bee-lightbox__info">
              <h2 class="bee-lightbox__title">{{ current?.title ?? '未命名照片' }}</h2>
              <p v-if="current?.description" class="bee-lightbox__desc">{{ current.description }}</p>

              <dl class="bee-lightbox__meta">
                <div class="bee-lightbox__meta-row">
                  <dt>拍摄时间</dt>
                  <dd>
                    {{ takenAtText }}
                    <span v-if="showTakenAtHint" class="bee-lightbox__meta-hint">可在编辑里手动修改</span>
                  </dd>
                </div>
                <div class="bee-lightbox__meta-row">
                  <dt>上传者</dt>
                  <dd class="bee-lightbox__uploader">
                    <BeeAvatar :name="current?.uploadedByName ?? ''" :size="20" />
                    {{ current?.uploadedByName ?? '' }}
                  </dd>
                </div>
                <div class="bee-lightbox__meta-row">
                  <dt>相册</dt>
                  <dd>{{ current?.albumName ?? '未分类' }}</dd>
                </div>
              </dl>

              <div v-if="current?.tags.length" class="bee-lightbox__tags">
                <BeeTag v-for="tag in current.tags" :key="tag" variant="default" size="sm">{{ tag }}</BeeTag>
              </div>

              <!-- 拍摄信息（19 字段分组 / GPS 原文 / 三种缺失文案 / 下载入口） -->
              <ExifPanel
                :detail="detail"
                :location-name="current?.locationName ?? null"
                :parse-failed="detailFailed"
                :fallback-preview-key="current?.previewKey ?? null"
                @retry="loadDetail"
              />
            </aside>
          </div>

          <footer v-if="total > 1" class="bee-lightbox__strip">
            <button
              v-for="(photo, i) in photos"
              :key="photo.id"
              class="bee-lightbox__thumb"
              :class="{ 'is-active': i === index }"
              type="button"
              :aria-label="`查看第 ${i + 1} 张`"
              @click="jump(i)"
            >
              <img :src="fileUrl(photo.thumbKey, 'thumb')" :alt="photo.title ?? '缩略图'" loading="lazy" />
            </button>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.bee-lightbox {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-4);
  background: var(--overlay-scrim);
  backdrop-filter: blur(8px);
}

.bee-lightbox__dialog {
  display: flex;
  flex-direction: column;
  width: min(1440px, 96vw);
  height: min(920px, 92vh);
  background: var(--bg-surface);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.bee-lightbox__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  height: 56px;
  padding: 0 var(--sp-4);
  flex: none;
  background: var(--bg-elevated);
  border-bottom: 1px solid var(--border-subtle);
}

.bee-lightbox__counter {
  font-size: 13px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bee-lightbox__tools {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
}

.bee-lightbox__tool {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--r-md);
  color: var(--text-secondary);
}

.bee-lightbox__tool:hover:not(:disabled) {
  background: var(--warm-100);
  color: var(--text-primary);
}

.bee-lightbox__tool:disabled {
  color: var(--text-disabled);
  cursor: not-allowed;
}

.bee-lightbox__stage {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
}

.bee-lightbox__image-area {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-8);
  background: var(--photo-void);
}

.bee-lightbox__image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  opacity: 0;
  transition: opacity var(--dur-base) var(--ease-out);
}

.bee-lightbox__image.is-loaded {
  opacity: 1;
}

.bee-lightbox__spinner {
  position: absolute;
  width: 32px;
  height: 32px;
  border-radius: var(--r-full);
  border: 2.5px solid var(--warm-400);
  border-top-color: var(--honey-400);
  animation: bee-spin 900ms linear infinite;
}

.bee-lightbox__retry {
  padding: var(--sp-2) var(--sp-4);
  border-radius: var(--r-md);
  background: var(--honey-500);
  color: var(--text-inverse);
  font-size: var(--fs-body);
}

.bee-lightbox__arrow {
  position: absolute;
  top: 50%;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  margin-top: -24px;
  border-radius: var(--r-full);
  background: color-mix(in srgb, var(--bg-surface) 12%, transparent);
  color: var(--text-inverse);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.bee-lightbox__arrow:hover {
  background: color-mix(in srgb, var(--bg-surface) 22%, transparent);
}

.bee-lightbox__arrow--prev {
  left: var(--sp-6);
}

.bee-lightbox__arrow--next {
  right: calc(320px + var(--sp-6));
}

.bee-lightbox__info {
  width: 320px;
  flex: none;
  padding: var(--sp-6);
  overflow-y: auto;
  background: var(--bg-elevated);
  border-left: 1px solid var(--border-subtle);
}

.bee-lightbox__title {
  font-size: var(--fs-h3);
  line-height: var(--lh-h3);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-lightbox__desc {
  margin-top: var(--sp-2);
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
}

.bee-lightbox__meta {
  margin-top: var(--sp-5);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.bee-lightbox__meta-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.bee-lightbox__meta-row dt {
  width: 64px;
  flex: none;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.bee-lightbox__meta-row dd {
  font-size: 13px;
  color: var(--text-secondary);
}

.bee-lightbox__uploader {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
}

.bee-lightbox__meta-hint {
  display: block;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.bee-lightbox__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1);
  margin-top: var(--sp-5);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--border-subtle);
}

.bee-lightbox__strip {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  height: 88px;
  padding: var(--sp-3) var(--sp-2);
  padding-inline-end: var(--sp-6);
  flex: none;
  overflow-x: auto;
  /* 右端渐变收口：横向滚动时最后一张缩略图不会以「被切一半」的形态硬贴边界。
     只用 alpha 渐变，不引入任何调色板之外的硬编码色值。 */
  -webkit-mask-image: linear-gradient(to right, rgba(0, 0, 0, 1) calc(100% - 32px), rgba(0, 0, 0, 0) 100%);
  mask-image: linear-gradient(to right, rgba(0, 0, 0, 1) calc(100% - 32px), rgba(0, 0, 0, 0) 100%);
  scrollbar-width: none;
  background: var(--bg-elevated);
  border-top: 1px solid var(--border-subtle);
}

.bee-lightbox__strip::-webkit-scrollbar {
  display: none;
}

.bee-lightbox__thumb {
  flex: none;
  width: 64px;
  height: 64px;
  border-radius: var(--r-sm);
  overflow: hidden;
  opacity: 0.55;
  transition: opacity var(--dur-fast) var(--ease-out);
}

.bee-lightbox__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.bee-lightbox__thumb.is-active {
  opacity: 1;
  box-shadow: 0 0 0 2px var(--honey-500);
}

/* ---------- 过渡 ---------- */
.bee-lightbox-enter-active,
.bee-lightbox-leave-active {
  transition: opacity var(--dur-base) var(--ease-out);
}

.bee-lightbox-enter-active .bee-lightbox__image,
.bee-lightbox-leave-active .bee-lightbox__image {
  transition:
    opacity var(--dur-base) var(--ease-out),
    transform var(--dur-slow) var(--ease-out);
}

.bee-lightbox-enter-from,
.bee-lightbox-leave-to {
  opacity: 0;
}

.bee-lightbox-enter-from .bee-lightbox__image {
  transform: scale(0.96);
}

@media (max-width: 1023px) {
  .bee-lightbox {
    padding: 0;
  }

  .bee-lightbox__dialog {
    width: 100vw;
    height: 100vh;
    border-radius: 0;
  }

  .bee-lightbox__info {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    width: 100%;
    max-height: 60vh;
    border-left: 0;
    border-top: 1px solid var(--border-subtle);
    border-radius: var(--r-xl) var(--r-xl) 0 0;
  }

  .bee-lightbox__arrow--next {
    right: var(--sp-6);
  }

  .bee-lightbox__strip {
    display: none;
  }
}
</style>
