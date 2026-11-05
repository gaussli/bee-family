import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from '@/api/http'
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
 * 因此这里不能只清 token，必须走 `clearLocal()` 连带清掉 `activeBabyId`。</p>
 */
setUnauthorizedHandler(() => {
  const auth = useAuthStore(pinia)
  auth.clearLocal()
  const current = router.currentRoute.value
  if (current.name === 'login') return
  void router.replace({ name: 'login', query: { redirect: current.fullPath } })
})

app.mount('#app')
