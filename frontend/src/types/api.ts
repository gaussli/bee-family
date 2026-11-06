/**
 * 统一响应契约（与后端 common/ApiResponse、common/PageResponse 一一对应）。
 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
  hasMore: boolean
}

/**
 * 业务错误码（docs/07 §5.2 / 附录 B 全量 35 项）。
 *
 * v1.1：废弃 40902（永久不再分配）、启用 40909、新增 11 项。
 * 前端只用少数几个做「路由 / 交互分支」，**绝不按 code 做本地文案映射**：
 * 差异化 message 一律由服务端透传并直接展示（§5.1、§9.9）。
 */
export const ErrorCode = {
  OK: 0,
  INVALID_PARAM: 40001,
  INVALID_ARGUMENT: 40002,
  UNAUTHORIZED: 40100,
  INVALID_CREDENTIALS: 40101,
  INVALID_OLD_PASSWORD: 40102,
  TOKEN_EXPIRED: 40103,
  FORBIDDEN: 40300,
  CROSS_FAMILY_ACCESS: 40301,
  OWNER_ONLY: 40302,
  NOT_RESOURCE_CREATOR: 40303,
  NO_ACTIVE_FAMILY: 40304,
  ADMIN_ONLY: 40305,
  RESOURCE_NOT_FOUND: 40401,
  JOIN_REQUEST_NOT_FOUND: 40402,
  EMAIL_ALREADY_EXISTS: 40901,
  FAMILY_MEMBER_LIMIT: 40903,
  BABY_LIMIT: 40904,
  ALBUM_NAME_DUPLICATED: 40905,
  MEMBER_NOT_REMOVABLE: 40906,
  CROSS_ALBUM_MOVE: 40907,
  CONFIRM_MISMATCH: 40908,
  FAMILY_CODE_INVALID: 40909,
  JOIN_REQUEST_ALREADY_MEMBER: 40910,
  JOIN_REQUEST_STATUS_ILLEGAL: 40911,
  JOIN_REQUEST_EXPIRED: 40912,
  UPLOAD_IN_PROGRESS: 40913,
  FILE_TOO_LARGE: 41301,
  BATCH_TOO_LARGE: 41302,
  UNSUPPORTED_MEDIA_TYPE: 41501,
  RATE_LIMITED: 42901,
  INTERNAL_ERROR: 50001,
  STORAGE_WRITE_FAILED: 50002,
  HEIC_TRANSCODE_FAILED: 50003,
  HEIC_TRANSCODER_UNAVAILABLE: 50301,
} as const

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode]

/**
 * 仅保留网络层（无响应）兜底文案。
 *
 * 严禁在此追加任何业务码 → 文案的映射：40301 等差异化 message 与
 * failures[].message 均为服务端权威文案，前端逐字展示（docs/07 §5.1、§9.9）。
 */
export const ERROR_MESSAGE: Record<number, string> = {
  [-1]: '网络异常，请检查连接',
}
