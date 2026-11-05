import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'

/** localStorage 键名：仅 token 持久化，其余上下文由 /auth/me 拉取 */
export const TOKEN_KEY = 'bee.token'
/** 当前选中的宝宝 id（家庭页与成长页共享） */
export const ACTIVE_BABY_KEY = 'bee.activeBabyId'

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

  /** 是否为登录态失效类错误（40100/40103 等） */
  get isAuthError(): boolean {
    return this.code >= 40100 && this.code < 40200
  }
}

type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null
let redirecting = false

/**
 * 注册 401 处理器（由 main.ts 注入，避免 http → store → api → http 的循环依赖）。
 *
 * @param handler 处理函数
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  unauthorizedHandler = handler
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

// 响应拦截：拆信封 + 归一化异常
instance.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResponse<unknown>
    if (payload && typeof payload.code === 'number') {
      if (payload.code === 0) {
        return payload.data
      }
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
      return Promise.reject(new ApiError(-1, -1, '网络异常，请检查连接'))
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
