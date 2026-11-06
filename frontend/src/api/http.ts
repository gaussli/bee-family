import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ERROR_MESSAGE, type ApiResponse } from '@/types/api'

/** localStorage 键名：仅 token 持久化，其余上下文由 /auth/me 拉取 */
export const TOKEN_KEY = 'bee.token'
/**
 * 当前选中的宝宝 id 的 localStorage 键前缀。
 *
 * v1.1 按 familyId 分键（`bee.activeBabyId.{familyId}`），切换家庭时禁止沿用旧值
 * （docs/07 §10.3 第 1 / 11 项）。保留该常量作为前缀与 v1.0 兼容键名。
 */
export const ACTIVE_BABY_KEY = 'bee.activeBabyId'

/**
 * 按家庭生成 activeBabyId 存储键；无家庭时回落到 v1.0 单键。
 *
 * @param familyId 当前激活家庭 id
 */
export function activeBabyStorageKey(familyId: number | null): string {
  return familyId === null ? ACTIVE_BABY_KEY : `${ACTIVE_BABY_KEY}.${familyId}`
}

/** 清理所有家庭相关的 activeBabyId 键（登出 / 改密码时调用，不误清 token）。 */
export function clearActiveBabyKeys(): void {
  try {
    const keys: string[] = []
    for (let index = 0; index < localStorage.length; index += 1) {
      const key = localStorage.key(index)
      if (key && (key === ACTIVE_BABY_KEY || key.startsWith(`${ACTIVE_BABY_KEY}.`))) {
        keys.push(key)
      }
    }
    for (const key of keys) localStorage.removeItem(key)
  } catch {
    // 隐私模式下 localStorage 不可写
  }
}

/**
 * 统一业务异常。
 *
 * <p>两种来源：① 后端返回 HTTP 200 但 code!==0；② 后端返回 4xx/5xx。
 * `status` 为 -1 表示网络层失败（无响应）。</p>
 */
export class ApiError extends Error {
  status: number
  code: number
  traceId: string | undefined

  constructor(status: number, code: number, message: string, traceId?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.traceId = traceId
  }

  /** 是否为登录态失效类错误（40100/40103 等）；40304/40305 不在此区间，不会误清 token。 */
  get isAuthError(): boolean {
    return this.code >= 40100 && this.code < 40200
  }
}

type Handler = () => void

let unauthorizedHandler: Handler | null = null
let noActiveFamilyHandler: Handler | null = null
let redirecting = false

/**
 * 注册 401 处理器（由 main.ts 注入，避免 http → store → api → http 的循环依赖）。
 *
 * @param handler 处理函数
 */
export function setUnauthorizedHandler(handler: Handler): void {
  unauthorizedHandler = handler
}

/**
 * 注册 40304 NO_ACTIVE_FAMILY 处理器（由 main.ts 注入）。
 *
 * 无家庭态是业务层概念：只做路由收敛到 `/onboarding`，**不清 token、不弹 danger Toast**
 * （docs/07 §6.3、§10.1）。
 *
 * @param handler 处理函数
 */
export function setNoActiveFamilyHandler(handler: Handler): void {
  noActiveFamilyHandler = handler
}

/** 读取本地 token（fileUrl 与请求拦截器共用，唯一读 localStorage 的地方） */
export function readToken(): string {
  try {
    return localStorage.getItem(TOKEN_KEY) ?? ''
  } catch {
    return ''
  }
}

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 30000,
})

// 请求拦截：注入 Bearer；FormData 交给浏览器补 boundary
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = readToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  if (config.data instanceof FormData) {
    config.headers.delete('Content-Type')
  }
  return config
})

/** 40301 差异化 message、failures[].message 等一律透传，不在此做任何本地文案映射。 */
function dispatchContextError(code: number): void {
  if (code === 40304 && noActiveFamilyHandler) {
    noActiveFamilyHandler()
  }
}

// 响应拦截：拆信封 + 归一化异常
instance.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResponse<unknown>
    if (payload && typeof payload.code === 'number') {
      if (payload.code === 0) {
        return payload.data
      }
      dispatchContextError(payload.code)
      throw new ApiError(response.status, payload.code, payload.message, payload.traceId)
    }
    return response.data
  },
  (error: unknown) => {
    const err = error as {
      response?: { status: number; data?: ApiResponse<unknown> }
      message?: string
    }
    if (!err.response) {
      return Promise.reject(new ApiError(-1, -1, ERROR_MESSAGE[-1]))
    }
    const body = err.response.data
    const apiError = new ApiError(
      err.response.status,
      body?.code ?? err.response.status,
      body?.message ?? '服务异常，请稍后重试',
      body?.traceId,
    )
    if (apiError.isAuthError && !redirecting) {
      redirecting = true
      unauthorizedHandler?.()
      // 只跳一次：500ms 后允许下一次（避免并发请求重复跳转）
      window.setTimeout(() => {
        redirecting = false
      }, 500)
    } else if (apiError.code === 40304) {
      dispatchContextError(apiError.code)
    }
    return Promise.reject(apiError)
  },
)

/**
 * 类型安全的 http 门面：返回值直接是后端 `data` 字段。
 */
export const http = {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.get(url, config) as unknown as Promise<T>
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(url, data, config) as unknown as Promise<T>
  },
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.patch(url, data, config) as unknown as Promise<T>
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.put(url, data, config) as unknown as Promise<T>
  },
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.delete(url, config) as unknown as Promise<T>
  },
}
