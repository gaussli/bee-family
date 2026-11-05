<script setup lang="ts">
/**
 * 登录 / 注册页（02 文档 §5.1）。
 *
 * <p>左右分栏：左品牌区（`--honey-50` 底 + 蜂巢装饰），右表单区（320px 居中）。
 * `<1024` 隐藏品牌区改为顶部 Logo。错误只落在字段下方，不弹 Toast。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BeeButton from '@/components/base/BeeButton.vue'
import BeeIcon from '@/components/base/BeeIcon.vue'
import BeeInput from '@/components/base/BeeInput.vue'
import HexPattern from '@/components/business/HexPattern.vue'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { ApiError } from '@/api/http'
import {
  validateEmail,
  validateInviteCode,
  validateNickname,
  validatePassword,
} from '@/utils/validate'

type Mode = 'login' | 'register'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

const mode = ref<Mode>('login')
const email = ref<string>('')
const password = ref<string>('')
const nickname = ref<string>('')
const inviteCode = ref<string>('')
const showPassword = ref<boolean>(false)
const loading = ref<boolean>(false)
const formError = ref<string>('')

const errors = ref<{ email: string; password: string; nickname: string; inviteCode: string }>({
  email: '',
  password: '',
  nickname: '',
  inviteCode: '',
})

const isRegister = computed<boolean>(() => mode.value === 'register')
const submitText = computed<string>(() => (isRegister.value ? '创建账号' : '进入蜜记'))

function switchMode(next: Mode): void {
  if (mode.value === next) return
  mode.value = next
  formError.value = ''
  errors.value = { email: '', password: '', nickname: '', inviteCode: '' }
}

function validate(): boolean {
  errors.value = {
    email: validateEmail(email.value),
    // 登录只校验非空，注册校验 6-32
    password: validatePassword(password.value, isRegister.value),
    nickname: isRegister.value ? validateNickname(nickname.value) : '',
    inviteCode: isRegister.value ? validateInviteCode(inviteCode.value) : '',
  }
  return Object.values(errors.value).every((message) => message === '')
}

async function onSubmit(): Promise<void> {
  formError.value = ''
  if (!validate()) return

  loading.value = true
  try {
    if (isRegister.value) {
      await auth.register({
        email: email.value.trim(),
        password: password.value,
        nickname: nickname.value.trim(),
        inviteCode: inviteCode.value.trim() || undefined,
      })
      toast.success('欢迎加入蜜记')
    } else {
      await auth.login(email.value.trim(), password.value)
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/timeline'
    await router.replace(redirect)
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.code === 40901) {
        errors.value.email = '该邮箱已注册，直接登录吧'
        return
      }
      if (error.code === 40101) {
        errors.value.password = '邮箱或密码不正确'
        return
      }
      if (error.code === 40904) {
        errors.value.inviteCode = '邀请码无效或已过期'
        return
      }
      formError.value = error.message
      return
    }
    formError.value = '网络异常，请检查连接后重试'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 从「接受邀请」链接进入时预填邀请码
  const code = route.query.code
  if (typeof code === 'string' && code) {
    mode.value = 'register'
    inviteCode.value = code.toUpperCase()
  }
})
</script>

<template>
  <div class="bee-login">
    <section class="bee-login__brand" aria-hidden="false">
      <div class="bee-login__brand-inner">
        <div class="bee-login__logo">
          <span class="bee-login__mark">
            <BeeIcon name="hex" :size="32" :stroke-width="1.4" />
          </span>
          <span class="bee-login__logo-text">
            <strong>蜜记</strong>
            <small>Bee Family</small>
          </span>
        </div>

        <h1 class="bee-login__slogan">
          把一家人的时光，<br />
          收成一册暖光相册。
        </h1>
        <span class="bee-login__rule" />

        <ul class="bee-login__points">
          <li>照片按时间自动成轴，翻着就是回忆</li>
          <li>身高体重头围曲线，一岁一个样都记得住</li>
          <li>家人共用一本相册，谁都能上传</li>
        </ul>
      </div>
      <HexPattern class="bee-login__pattern" :cols="7" :rows="5" :radius="26" :opacity="0.08" />
    </section>

    <section class="bee-login__panel">
      <div class="bee-login__form">
        <div class="bee-login__mobile-logo">
          <BeeIcon name="hex" :size="28" />
          <strong>蜜记</strong>
        </div>

        <div class="bee-login__tabs" role="tablist" aria-label="登录或注册">
          <button
            class="bee-login__tab"
            :class="{ 'is-active': mode === 'login' }"
            type="button"
            role="tab"
            :aria-selected="mode === 'login'"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            class="bee-login__tab"
            :class="{ 'is-active': mode === 'register' }"
            type="button"
            role="tab"
            :aria-selected="mode === 'register'"
            @click="switchMode('register')"
          >
            注册
          </button>
        </div>

        <form class="bee-login__fields" novalidate @submit.prevent="onSubmit">
          <BeeInput
            v-model="email"
            type="email"
            label="邮箱"
            placeholder="you@home.local"
            icon="user"
            :error="errors.email"
            autofocus
          />

          <BeeInput
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            label="密码"
            placeholder="至少 6 位"
            icon="settings"
            :error="errors.password"
          >
            <template #suffix>
              <button
                class="bee-login__eye"
                type="button"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <BeeIcon :name="showPassword ? 'info' : 'search'" :size="15" />
              </button>
            </template>
          </BeeInput>

          <template v-if="isRegister">
            <BeeInput
              v-model="nickname"
              label="昵称"
              placeholder="家人怎么称呼你"
              icon="baby"
              :error="errors.nickname"
              :maxlength="32"
            />
            <BeeInput
              v-model="inviteCode"
              label="邀请码（选填）"
              placeholder="8 位字母数字"
              icon="copy"
              :error="errors.inviteCode"
              :maxlength="8"
            />
          </template>

          <p v-if="formError" class="bee-login__error">{{ formError }}</p>

          <BeeButton class="bee-login__submit" type="submit" size="lg" block :loading="loading">
            {{ submitText }}
          </BeeButton>
        </form>

        <p class="bee-login__switch">
          <template v-if="isRegister">
            已经有账号了？
            <button type="button" @click="switchMode('login')">去登录 →</button>
          </template>
          <template v-else>
            还没有账号？
            <button type="button" @click="switchMode('register')">去注册 →</button>
          </template>
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.bee-login {
  display: flex;
  min-height: 100vh;
  background: var(--bg-page);
}

.bee-login__brand {
  position: relative;
  flex: 0 0 55%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-16);
  background: var(--honey-50);
  border-right: 1px solid var(--border-subtle);
  overflow: hidden;
}

.bee-login__brand-inner {
  position: relative;
  z-index: 2;
  max-width: 420px;
}

.bee-login__logo {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.bee-login__mark {
  color: var(--honey-500);
}

.bee-login__logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.bee-login__logo-text strong {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.bee-login__logo-text small {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  letter-spacing: 0.06em;
}

.bee-login__slogan {
  margin-top: var(--sp-10);
  font-family: var(--font-display);
  font-size: var(--fs-display);
  line-height: var(--lh-display);
  font-weight: 600;
  color: var(--text-primary);
}

.bee-login__rule {
  display: block;
  width: 48px;
  height: 2px;
  margin: var(--sp-6) 0;
  border-radius: var(--r-full);
  background: var(--honey-500);
}

.bee-login__points {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-secondary);
}

.bee-login__points li::before {
  content: '·';
  margin-right: var(--sp-2);
  color: var(--honey-500);
  font-weight: 700;
}

.bee-login__pattern {
  z-index: 1;
}

.bee-login__panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-8);
}

.bee-login__form {
  width: 320px;
  max-width: 100%;
}

.bee-login__mobile-logo {
  display: none;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  height: 120px;
  color: var(--honey-500);
}

.bee-login__mobile-logo strong {
  font-family: var(--font-display);
  font-size: 20px;
  color: var(--text-primary);
}

.bee-login__tabs {
  display: flex;
  gap: var(--sp-5);
  margin-bottom: var(--sp-6);
  border-bottom: 1px solid var(--border-subtle);
}

.bee-login__tab {
  position: relative;
  height: 40px;
  font-size: var(--fs-body-lg);
  font-weight: 500;
  color: var(--text-tertiary);
  transition: color var(--dur-base) var(--ease-out);
}

.bee-login__tab.is-active {
  color: var(--text-primary);
}

.bee-login__tab.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: var(--honey-500);
}

.bee-login__fields {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
}

.bee-login__eye {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--r-sm);
  color: var(--text-tertiary);
}

.bee-login__eye:hover {
  color: var(--text-primary);
}

.bee-login__error {
  font-size: var(--fs-caption);
  color: var(--color-danger);
  padding: var(--sp-2) var(--sp-3);
  background: var(--color-danger-bg);
  border-radius: var(--r-sm);
}

.bee-login__submit {
  margin-top: var(--sp-1);
}

.bee-login__switch {
  margin-top: var(--sp-5);
  text-align: center;
  font-size: var(--fs-body);
  color: var(--text-tertiary);
}

.bee-login__switch button {
  color: var(--text-brand);
  font-weight: 500;
}

.bee-login__switch button:hover {
  text-decoration: underline;
}

@media (max-width: 1023px) {
  .bee-login__brand {
    display: none;
  }

  .bee-login__mobile-logo {
    display: flex;
  }

  .bee-login__panel {
    padding: var(--sp-6);
  }
}

@media (max-width: 639px) {
  .bee-login__form {
    width: 100%;
  }

  .bee-login__submit {
    font-size: var(--fs-body-lg);
  }
}
</style>
