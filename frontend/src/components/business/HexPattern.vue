<script setup lang="ts">
/**
 * 蜂巢底纹（装饰用）。
 *
 * <p>纯 SVG 平铺，用于登录页品牌区、空状态与相册封面占位。`opacity` 由调用方控制，
 * 默认 8%（02 文档 §5.1「蜂巢装饰 8%」）。</p>
 */
import { computed } from 'vue'

interface Props {
  /** 列数 */
  cols?: number
  /** 行数 */
  rows?: number
  /** 单个六边形外接圆半径 */
  radius?: number
  opacity?: number
  color?: string
}

const props = withDefaults(defineProps<Props>(), {
  cols: 6,
  rows: 4,
  radius: 10,
  opacity: 0.08,
  color: 'var(--honey-500)',
})

/** 生成错峰排列的六边形中心点（奇数行右移半个间距）。 */
const cells = computed<{ x: number; y: number }[]>(() => {
  const r = props.radius
  const stepX = r * 1.732
  const stepY = r * 1.5
  const result: { x: number; y: number }[] = []
  for (let row = 0; row < props.rows; row += 1) {
    for (let col = 0; col < props.cols; col += 1) {
      result.push({
        x: r + col * stepX + (row % 2 === 0 ? 0 : stepX / 2),
        y: r + row * stepY,
      })
    }
  }
  return result
})

const viewWidth = computed<number>(() => Math.ceil(props.radius * (1.732 * props.cols + 0.866)))
const viewHeight = computed<number>(() => Math.ceil(props.radius * (1.5 * props.rows + 0.5)))

function points(cell: { x: number; y: number }): string {
  const r = props.radius
  const inner = r * 0.86
  const corners = [
    [0, -r],
    [inner * 0.866, -r / 2],
    [inner * 0.866, r / 2],
    [0, r],
    [-inner * 0.866, r / 2],
    [-inner * 0.866, -r / 2],
  ]
  return corners.map(([dx, dy]) => `${cell.x + dx},${cell.y + dy}`).join(' ')
}
</script>

<template>
  <svg
    class="bee-hex-pattern"
    :viewBox="`0 0 ${viewWidth} ${viewHeight}`"
    :style="{ opacity }"
    aria-hidden="true"
    preserveAspectRatio="xMidYMid slice"
  >
    <polygon
      v-for="(cell, index) in cells"
      :key="index"
      :points="points(cell)"
      fill="none"
      :stroke="color"
      stroke-width="1"
    />
  </svg>
</template>

<style scoped>
.bee-hex-pattern {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}
</style>
