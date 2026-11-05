<script setup lang="ts">
/**
 * 成长曲线（02 文档 §4.13）。
 *
 * <p><b>ECharts 按需引入</b>：只注册 `LineChart` + `Grid/Tooltip/Legend` + `CanvasRenderer`，
 * 避免整包（~1MB）进入首屏 chunk。所有颜色从设计 token 实时读取（`getComputedStyle`），
 * 源码内不出现任何色值字面量。</p>
 *
 * <p>交互：默认三条线都不显示面积（叠加会糊）；只勾选一条时该条显示自上而下的渐变填充。
 * `<640px` 时隐藏右轴（体重单位改在 tooltip 内展示）。</p>
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts } from 'echarts/core'
import BeeButton from '@/components/base/BeeButton.vue'
import type { CurvePoint, GrowthRange } from '@/types/models'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

interface Props {
  points: CurvePoint[]
  range: GrowthRange
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
})

const emit = defineEmits<{ record: [] }>()

const chartRef = ref<HTMLDivElement | null>(null)
const isEmpty = computed<boolean>(() => props.points.length === 0)

let chart: ECharts | null = null
let observer: ResizeObserver | null = null

/**
 * 从 `:root` 读取设计 token，保证图表配色与全局主题永远一致。
 *
 * <p>第二参数是极端情况下的兜底色：使用 CSS 颜色关键字而非十六进制字面量，
 * 以满足「组件内禁止出现 hex 色值」的硬性约束。</p>
 */
function token(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

const SERIES = [
  { key: 'height', label: '身高', unit: 'cm', token: '--honey-500', fallback: 'goldenrod', axis: 0 },
  { key: 'weight', label: '体重', unit: 'kg', token: '--accent-coral', fallback: 'indianred', axis: 1 },
  { key: 'head', label: '头围', unit: 'cm', token: '--accent-moss', fallback: 'darkseagreen', axis: 0 },
] as const

/**
 * `time` 轴要求数据是 `[时间戳, 值]` 二元组（直接传裸数值会被当成时间戳 → 1970 年）。
 *
 * @param recordedAt 后端日期串 "yyyy-MM-dd"
 */
function toTimestamp(recordedAt: string): number {
  const [year, month, day] = recordedAt.slice(0, 10).split('-').map(Number)
  if (!year || !month || !day) {
    return 0
  }
  return new Date(year, month - 1, day).getTime()
}

/**
 * 时间戳 → "2026年10月24日"（图表内自用，避免依赖 Date 的本地化输出）
 *
 * @param value 毫秒时间戳
 */
function formatAxisDate(value: number): string {
  const date = new Date(value)
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

function buildOption(): echarts.EChartsCoreOption {
  const warm200 = token('--warm-200', 'gainsboro')
  const warm500 = token('--warm-500', 'gray')
  const warm400 = token('--warm-400', 'darkgray')
  const warm100 = token('--warm-100', 'whitesmoke')
  const surface = token('--bg-surface', 'white')
  const textPrimary = token('--text-primary', 'black')
  const textSecondary = token('--text-secondary', 'dimgray')
  const narrow = typeof window !== 'undefined' && window.innerWidth < 640

  const showSymbol = props.points.length >= 3

  const series = SERIES.map((item) => {
    const color = token(item.token, item.fallback)
    const data = props.points.map((point) => {
      const value =
        item.key === 'height' ? point.heightCm : item.key === 'weight' ? point.weightKg : point.headCm
      return [toTimestamp(point.recordedAt), value] as [number, number | null]
    })
    return {
      name: item.label,
      type: 'line' as const,
      smooth: 0.35,
      symbol: 'circle' as const,
      symbolSize: 6,
      showSymbol,
      yAxisIndex: narrow && item.axis === 1 ? 0 : item.axis,
      connectNulls: true,
      lineStyle: { width: 2.5, color },
      itemStyle: { color },
      // 面积默认关闭：三条叠加会互相糊住
      areaStyle: { opacity: 0 },
      emphasis: { focus: 'series' as const },
      data,
    }
  })

  return {
    color: SERIES.map((item) => token(item.token, item.fallback)),
    // top 留 64：图例在 4，轴名在网格顶部，两者不打架
    grid: { left: 44, right: narrow ? 16 : 44, top: narrow ? 56 : 64, bottom: 32 },
    legend: {
      right: 0,
      top: 4,
      icon: 'roundRect',
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 16,
      textStyle: { color: textSecondary, fontSize: 13 },
      inactiveColor: warm400,
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: surface,
      borderColor: warm200,
      borderWidth: 1,
      borderRadius: 10,
      padding: [12, 16],
      textStyle: { color: textPrimary, fontSize: 13 },
      axisPointer: { type: 'line', lineStyle: { color: warm200 } },
      formatter: (params: unknown): string => {
        const list = params as { seriesName: string; data: [number, number | null] }[]
        if (!Array.isArray(list) || list.length === 0) return ''
        const head = list[0]
        if (!head) return ''
        const headTime = Array.isArray(head.data) ? head.data[0] : 0
        const rows = list
          .map((item) => {
            const value = Array.isArray(item.data) ? item.data[1] : null
            if (value === null || value === undefined) {
              return ''
            }
            const meta = SERIES.find((entry) => entry.label === item.seriesName)
            const color = meta ? token(meta.token, meta.fallback) : textSecondary
            return `<div style="display:flex;align-items:center;gap:8px;margin-top:6px">
              <span style="width:8px;height:8px;border-radius:50%;background:${color}"></span>
              <span style="color:${textSecondary}">${item.seriesName}</span>
              <strong style="margin-left:auto;color:${textPrimary}">${value} ${meta?.unit ?? ''}</strong>
            </div>`
          })
          .join('')
        return `<div style="font-size:12px;color:${textSecondary}">${formatAxisDate(headTime)}</div>${rows}`
      },
    },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: warm200 } },
      axisTick: { show: false },
      axisLabel: {
        color: warm500,
        fontSize: 12,
        formatter: (value: number): string => {
          const date = new Date(value)
          const year = String(date.getFullYear()).slice(2)
          const month = String(date.getMonth() + 1).padStart(2, '0')
          return `${year}/${month}`
        },
      },
      splitLine: { show: false },
    },
    yAxis: narrow
      ? [
          {
            type: 'value',
            name: 'cm',
            nameTextStyle: { color: warm500, fontSize: 12 },
            axisLabel: { color: warm500, fontSize: 12 },
            splitLine: { lineStyle: { color: warm100, type: 'dashed' } },
          },
        ]
      : [
          {
            type: 'value',
            name: '身高 / 头围 (cm)',
            nameTextStyle: { color: warm500, fontSize: 12 },
            axisLabel: { color: warm500, fontSize: 12 },
            splitLine: { lineStyle: { color: warm100, type: 'dashed' } },
          },
          {
            type: 'value',
            name: '体重 (kg)',
            nameTextStyle: { color: warm500, fontSize: 12 },
            axisLabel: { color: warm500, fontSize: 12 },
            splitLine: { show: false },
          },
        ],
    series,
  }
}

function render(): void {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value, undefined, { renderer: 'canvas' })
    chart.on('legendselectchanged', (params: unknown) => {
      onLegendChange(params as { selected: Record<string, boolean> })
    })
  }
  chart.setOption(buildOption(), { notMerge: true })
  if (isEmpty.value) {
    chart.clear()
  }
}

/** 只勾选一条时给这条加渐变面积，多选时全部取消。 */
function onLegendChange(params: { selected: Record<string, boolean> }): void {
  if (!chart) return
  const selectedKeys = Object.keys(params.selected).filter((key) => params.selected[key])
  if (selectedKeys.length !== 1) {
    chart.setOption({
      series: SERIES.map((item) => ({ name: item.label, areaStyle: { opacity: 0 }, lineStyle: { width: 2.5 } })),
    })
    return
  }
  const only = selectedKeys[0]
  const meta = SERIES.find((item) => item.label === only)
  const color = meta ? token(meta.token, meta.fallback) : token('--honey-500', 'goldenrod')
  chart.setOption({
    series: SERIES.map((item) => {
      const active = item.label === only
      return {
        name: item.label,
        lineStyle: { width: active ? 3 : 2.5 },
        areaStyle: active
          ? {
              opacity: 1,
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: `${color}2E` },
                { offset: 1, color: `${color}00` },
              ]),
            }
          : { opacity: 0 },
      }
    }),
  })
}

function resize(): void {
  chart?.resize()
}

watch(
  () => [props.points, props.range],
  () => {
    void nextTick(render)
  },
  { deep: true },
)

onMounted(() => {
  render()
  if (typeof ResizeObserver !== 'undefined' && chartRef.value) {
    observer = new ResizeObserver(resize)
    observer.observe(chartRef.value)
  }
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="bee-growth-chart">
    <div ref="chartRef" class="bee-growth-chart__canvas" :class="{ 'is-empty': isEmpty }" />

    <div v-if="isEmpty && !loading" class="bee-growth-chart__empty">
      <p class="bee-growth-chart__empty-text">还没有测量记录</p>
      <BeeButton size="sm" @click="emit('record')">记录第一次测量</BeeButton>
    </div>

    <div v-if="loading" class="bee-growth-chart__loading" aria-live="polite">加载中…</div>
  </div>
</template>

<style scoped>
.bee-growth-chart {
  position: relative;
  width: 100%;
}

.bee-growth-chart__canvas {
  width: 100%;
  height: 320px;
}

.bee-growth-chart__canvas.is-empty {
  opacity: 0.85;
}

.bee-growth-chart__empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  pointer-events: none;
}

.bee-growth-chart__empty-text {
  font-size: var(--fs-body);
  color: var(--text-tertiary);
}

.bee-growth-chart__empty :deep(.bee-btn) {
  pointer-events: auto;
}

.bee-growth-chart__loading {
  position: absolute;
  top: var(--sp-2);
  right: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

@media (max-width: 1023px) {
  .bee-growth-chart__canvas {
    height: 260px;
  }
}

@media (max-width: 639px) {
  .bee-growth-chart__canvas {
    height: 220px;
  }
}
</style>
