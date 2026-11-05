<script setup lang="ts">
/**
 * 应用外壳：侧栏（≥768）+ 顶栏 + 内容区 + 移动端 TabBar + 全局上传浮层。
 *
 * <p>滚动模型：窗口滚动，顶栏 `position: sticky; top: 0`。这样时间轴的月份分组标题
 * 用 `top: var(--topbar-h)` 就能精确吸附在顶栏下方（见 02 文档 §5.2）。</p>
 *
 * <p>数据引导：进入受保护区域后拉一次 `/auth/me`（刷新页面时内存里没有用户）
 * 和家庭聚合数据（成员、宝宝、邀请码），失败不阻塞页面渲染。</p>
 */
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import SideNav from './SideNav.vue'
import TopBar from './TopBar.vue'
import TabBar from './TabBar.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import UploadPanel from '@/components/business/UploadPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { useFamilyStore } from '@/stores/family'

const route = useRoute()
const auth = useAuthStore()
const family = useFamilyStore()

const navOpen = ref<boolean>(false)
const uploadOpen = ref<boolean>(false)

watch(
  () => route.fullPath,
  () => {
    navOpen.value = false
  },
)

onMounted(async () => {
  if (!auth.token) return
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      // 401 已由 axios 拦截器统一处理，这里静默
    }
  }
  try {
    await family.loadAll()
  } catch {
    // 家庭数据拉取失败不影响页面骨架，页面内各自有错误态
  }
})
</script>

<template>
  <div class="bee-shell">
    <SideNav />

    <div class="bee-shell__main">
      <TopBar @upload="uploadOpen = true" @open-nav="navOpen = true" />
      <main class="bee-shell__content">
        <slot />
      </main>
    </div>

    <TabBar />

    <!-- 移动端侧栏改为左侧抽屉（280px，--dur-slow 滑入） -->
    <Transition name="bee-nav-drawer">
      <div v-if="navOpen" class="bee-nav-drawer" role="presentation" @click.self="navOpen = false">
        <aside class="bee-nav-drawer__panel" role="dialog" aria-modal="true" aria-label="导航">
          <header class="bee-nav-drawer__head">
            <RouterLink to="/timeline" class="bee-nav-drawer__logo" @click="navOpen = false">
              <BeeIcon name="hex" :size="22" />
              <strong>蜜记</strong>
            </RouterLink>
            <button class="bee-nav-drawer__close" type="button" aria-label="关闭导航" @click="navOpen = false">
              <BeeIcon name="close" :size="18" />
            </button>
          </header>

          <nav class="bee-nav-drawer__nav">
            <RouterLink class="bee-nav-drawer__item" to="/timeline">
              <BeeIcon name="grid" :size="18" />时间轴
            </RouterLink>
            <RouterLink class="bee-nav-drawer__item" to="/albums">
              <BeeIcon name="camera" :size="18" />相册
            </RouterLink>
            <RouterLink class="bee-nav-drawer__item" to="/growth">
              <BeeIcon name="baby" :size="18" />成长记录
            </RouterLink>
            <RouterLink class="bee-nav-drawer__item" to="/family">
              <BeeIcon name="user" :size="18" />家庭成员
            </RouterLink>
            <RouterLink class="bee-nav-drawer__item" to="/settings">
              <BeeIcon name="settings" :size="18" />设置
            </RouterLink>
          </nav>
        </aside>
      </div>
    </Transition>

    <UploadPanel :open="uploadOpen" :album-id="null" @close="uploadOpen = false" />
  </div>
</template>

<style scoped>
.bee-shell {
  display: flex;
  min-height: 100vh;
  align-items: flex-start;
}

.bee-shell__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.bee-shell__content {
  flex: 1;
  width: 100%;
  max-width: var(--container-max);
  margin: 0 auto;
  padding: var(--sp-8) var(--container-pad) var(--sp-16);
}

/* ---------- 移动端抽屉 ---------- */
.bee-nav-drawer {
  position: fixed;
  inset: 0;
  z-index: 920;
  background: var(--overlay-scrim);
  display: flex;
}

.bee-nav-drawer__panel {
  display: flex;
  flex-direction: column;
  width: 280px;
  height: 100%;
  background: var(--bg-surface);
  box-shadow: var(--shadow-lg);
}

.bee-nav-drawer__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--topbar-h);
  padding: 0 var(--sp-4);
  border-bottom: 1px solid var(--border-subtle);
}

.bee-nav-drawer__logo {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  color: var(--honey-500);
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 600;
}

.bee-nav-drawer__logo strong {
  color: var(--text-primary);
}

.bee-nav-drawer__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--r-md);
  color: var(--text-tertiary);
}

.bee-nav-drawer__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--sp-3);
}

.bee-nav-drawer__item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  height: 44px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-md);
  font-size: var(--fs-body-lg);
  color: var(--text-secondary);
}

.bee-nav-drawer__item.router-link-active {
  background: var(--honey-100);
  color: var(--text-brand);
  font-weight: 600;
}

.bee-nav-drawer-enter-active,
.bee-nav-drawer-leave-active {
  transition: opacity var(--dur-base) var(--ease-out);
}

.bee-nav-drawer-enter-active .bee-nav-drawer__panel,
.bee-nav-drawer-leave-active .bee-nav-drawer__panel {
  transition: transform var(--dur-slow) var(--ease-out);
}

.bee-nav-drawer-enter-from,
.bee-nav-drawer-leave-to {
  opacity: 0;
}

.bee-nav-drawer-enter-from .bee-nav-drawer__panel,
.bee-nav-drawer-leave-to .bee-nav-drawer__panel {
  transform: translateX(-100%);
}

@media (max-width: 767px) {
  .bee-shell__content {
    padding: var(--sp-4) var(--container-pad) calc(var(--tabbar-h) + var(--sp-8));
  }
}
</style>
