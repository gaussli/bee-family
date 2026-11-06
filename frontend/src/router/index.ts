import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    layout?: 'app' | 'blank'
    /** 允许无家庭态访问（Q9 白名单：/onboarding、账号设置、登出）。 */
    allowNoFamily?: boolean
    title?: string
  }
}

/**
 * 路由表：与 `docs/07-architecture-incremental.md` §10.1 一致。
 *
 * v1.1 新增 `/onboarding`（blank 布局三态引导）与 `/settings/account`
 * （blank 布局、仅账号子页），主界面路由保持原样。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false, layout: 'blank', title: '登录' },
  },
  { path: '/', redirect: '/timeline' },
  {
    path: '/onboarding',
    name: 'onboarding',
    component: () => import('@/views/OnboardingView.vue'),
    meta: { requiresAuth: true, layout: 'blank', allowNoFamily: true, title: '加入家庭' },
  },
  {
    path: '/timeline',
    name: 'timeline',
    component: () => import('@/views/TimelineView.vue'),
    meta: { requiresAuth: true, layout: 'app', title: '时间轴' },
  },
  {
    path: '/albums',
    name: 'albums',
    component: () => import('@/views/AlbumsView.vue'),
    meta: { requiresAuth: true, layout: 'app', title: '相册' },
  },
  {
    path: '/albums/:id',
    name: 'album-detail',
    component: () => import('@/views/AlbumDetailView.vue'),
    props: true,
    meta: { requiresAuth: true, layout: 'app', title: '相册详情' },
  },
  {
    path: '/growth',
    name: 'growth',
    component: () => import('@/views/GrowthView.vue'),
    meta: { requiresAuth: true, layout: 'app', title: '成长记录' },
  },
  {
    path: '/baby/:id',
    name: 'baby',
    component: () => import('@/views/BabyView.vue'),
    props: true,
    meta: { requiresAuth: true, layout: 'app', title: '宝宝档案' },
  },
  {
    path: '/family',
    name: 'family',
    component: () => import('@/views/FamilyView.vue'),
    meta: { requiresAuth: true, layout: 'app', title: '家庭成员' },
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('@/views/SearchView.vue'),
    meta: { requiresAuth: true, layout: 'app', title: '搜索' },
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/views/SettingsView.vue'),
    meta: { requiresAuth: true, layout: 'app', title: '设置' },
  },
  {
    path: '/settings/account',
    name: 'settings-account',
    component: () => import('@/views/SettingsView.vue'),
    props: { accountOnly: true },
    meta: { requiresAuth: true, layout: 'blank', allowNoFamily: true, title: '账号设置' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { requiresAuth: false, layout: 'blank', title: '页面不存在' },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (_to, _from, savedPosition) => savedPosition ?? { top: 0 },
})

/**
 * Q9 账号白名单：无家庭时仅引导页、账号设置与登出可达，不得进主界面。
 * 与 docs/07 §8.2 服务端白名单一致；服务端 40304 仍是权威兜底。
 */
function isNoFamilyAllowed(path: string, query: Record<string, unknown>): boolean {
  if (path === '/onboarding' || path === '/settings/account') return true
  // 「账号设置」深链形态：/settings?tab=account，设置页会自动收敛为仅账号子页。
  if (path === '/settings' && query.tab === 'account') return true
  return false
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 1. 恢复 token（沿用）
  auth.restore()

  // 2. 未登录访问受保护路由（沿用）
  if (to.meta.requiresAuth !== false && !auth.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 3. 已登录访问登录页（沿用）
  if (to.name === 'login' && auth.token) {
    return { path: '/timeline' }
  }

  if (!auth.token) return true

  // 4. 新增：刷新页面后内存里没有家庭上下文，先拉一次 /auth/me。
  //    失败时不置 bootstrapped，noFamily 保持 false，避免网络抖动误拦截。
  if (!auth.bootstrapped) {
    try {
      await auth.fetchMe()
    } catch {
      // 401 已由 axios 拦截器统一处理，这里静默
    }
  }

  // 5. 新增：无家庭时只允许账号白名单，其余任何路由一律重定向 /onboarding
  //    （不弹 danger Toast；服务端 40304 兜底同样走这里）。
  if (auth.noFamily && !isNoFamilyAllowed(to.path, to.query as Record<string, unknown>)) {
    return { name: 'onboarding' }
  }

  // 5.1 账号设置深链收敛为 blank 布局：无家庭态下不渲染主界面导航壳（Q9 / 06 §5.1）。
  if (auth.noFamily && to.name === 'settings') {
    return { name: 'settings-account' }
  }

  // 6. 有家庭的用户同样可进 /onboarding：切换器底部「创建家庭 / 加入家庭」
  //    对应状态 A 的两个表单（docs/06 §7.1），因此这里**不做**反向重定向。
  //    切换家庭成功后由 resetAllForFamily 强制 replace('/timeline')，不新增 /switch 路由。
  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} · 蜜记` : '蜜记 · Bee Family'
})

export default router
