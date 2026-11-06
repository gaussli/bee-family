<script setup lang="ts">
/**
 * 应用外壳（docs/06 §3、§4；docs/07 §10.2）。
 *
 * <p>导航互斥单一真源：`useBreakpoint().isMobile`（`matchMedia` 等价的 768 判定）。
 * ≥768 只挂载 `TopNav`，<768 只挂载精简 `TopBar` + 底部 `TabBar`，
 * 两套主导航**同一时刻只存在一套**（`v-if` 卸载而非仅 CSS 隐藏）；
 * 各组件内的媒体查询只是第二道保险。`SideNav` 与汉堡抽屉永久移除。</p>
 *
 * <p>滚动模型：窗口滚动，顶栏 `position: sticky; top: 0`；内容区仍
 * `max-width: 1200px` / `padding: 0 var(--container-pad)`，1440 下内容宽 1136px，
 * 所有栅格与 5 列照片网格判定不变。</p>
 *
 * <p>数据引导：进入受保护区域后拉一次 `/auth/me`（刷新页面内存里没有上下文）
 * 与家庭聚合数据（成员 / 宝宝 / 家庭码）；owner + admin 额外拉待审角标计数。
 * 任一失败都不阻塞页面骨架（后端 v1.0 未实现新接口时属预期）。</p>
 */
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TopNav from './TopNav.vue'
import TopBar from './TopBar.vue'
import TabBar from './TabBar.vue'
import UploadPanel from '@/components/business/UploadPanel.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useAuthStore } from '@/stores/auth'
import { useFamilyStore } from '@/stores/family'
import { useSwitchStore } from '@/stores/switch'

const route = useRoute()
const auth = useAuthStore()
const family = useFamilyStore()
const switchStore = useSwitchStore()
const { isMobile } = useBreakpoint()

const uploadOpen = ref<boolean>(false)

watch(
  () => route.fullPath,
  () => {
    // 路由变化时收起上传浮层，避免旧相册资源残留在浮层里
    uploadOpen.value = false
  },
)

function viewQueue(): void {
  uploadOpen.value = true
  switchStore.focusUploadQueue()
}

onMounted(async () => {
  if (!auth.token) return
  if (!auth.bootstrapped) {
    try {
      await auth.fetchMe()
    } catch {
      // 401 已由 axios 拦截器统一处理，这里静默
    }
  }
  // activeBabyId 按 familyId 分键（docs/07 §10.3 第 1 / 11 项）
  family.bindFamily(auth.activeFamilyId)
  try {
    await family.loadAll()
  } catch {
    // 家庭数据拉取失败不影响页面骨架，页面内各自有错误态
  }
  if (auth.canReview) {
    try {
      await family.fetchPendingReviewCount()
    } catch {
      // 待审角标接口在后端升级前可能不存在：角标不渲染即可
    }
  }
})
</script>

<template>
  <div class="bee-shell">
    <!-- 互斥：≥768 顶部导航；<768 精简顶栏 + 底部 TabBar。任一时刻只挂载一套主导航 -->
    <TopNav v-if="!isMobile" @upload="uploadOpen = true" @view-queue="viewQueue" />
    <TopBar v-else @view-queue="viewQueue" />

    <main class="bee-shell__content">
      <slot />
    </main>

    <TabBar v-if="isMobile" />

    <UploadPanel :open="uploadOpen" :album-id="null" @close="uploadOpen = false" />
  </div>
</template>

<style scoped>
.bee-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  align-items: stretch;
}

.bee-shell__content {
  flex: 1;
  width: 100%;
  max-width: var(--container-max);
  margin: 0 auto;
  padding: var(--sp-8) var(--container-pad) var(--sp-16);
}

@media (max-width: 767px) {
  .bee-shell__content {
    padding: var(--sp-4) var(--container-pad) calc(var(--tabbar-h) + var(--sp-8));
  }
}
</style>
