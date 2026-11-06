import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setNoActiveFamilyHandler, setUnauthorizedHandler } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import './styles/tokens.css'
import './styles/reset.css'
import './styles/base.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

/**
 * 401 统一处理：清本地凭证并跳登录。
 *
 * <p>通过依赖注入而非直接 import，避免 `http → store → api → http` 的循环依赖。
 * 登出 / 改密码 / 移除成员都会让旧 token 全部失效（`token_valid_after` 机制），
 * 因此这里不能只清 token，必须走 `clearLocal()` 连带清掉所有家庭的 activeBabyId 分键。</p>
 */
setUnauthorizedHandler(() => {
  const auth = useAuthStore(pinia)
  auth.clearLocal()
  const current = router.currentRoute.value
  if (current.name === 'login') return
  void router.replace({ name: 'login', query: { redirect: current.fullPath } })
})

/**
 * 40304 NO_ACTIVE_FAMILY 统一处理：只收敛路由到 /onboarding。
 *
 * 不清 token（40304 不在 isAuthError 区间）、不弹 danger Toast（docs/07 §6.3）。
 */
setNoActiveFamilyHandler(() => {
  const current = router.currentRoute.value
  if (current.name === 'onboarding' || current.path === '/settings/account') return
  void router.replace({ name: 'onboarding' })
})

app.mount('#app')
