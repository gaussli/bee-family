import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    layout?: 'app' | 'blank'
    title?: string
  }
}

/** 路由表：与 `docs/03-architecture.md` §9.2 逐行一致。 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false, layout: 'blank', title: '登录' },
  },
  { path: '/', redirect: '/timeline' },
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

router.beforeEach((to) => {
  const auth = useAuthStore()
  auth.restore()

  if (to.meta.requiresAuth !== false && !auth.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.token) {
    return { path: '/timeline' }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} · 蜜记` : '蜜记 · Bee Family'
})

export default router
