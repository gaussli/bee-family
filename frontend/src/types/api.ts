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
 * 业务错误码（docs §5.2 全量）。前端只用少数几个做分支，其余仅透出 message。
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
  RESOURCE_NOT_FOUND: 40401,
  EMAIL_ALREADY_EXISTS: 40901,
  INVITE_CODE_INVALID: 40902,
  FAMILY_MEMBER_LIMIT: 40903,
  BABY_LIMIT: 40904,
  ALBUM_NAME_DUPLICATED: 40905,
  MEMBER_NOT_REMOVABLE: 40906,
  CROSS_ALBUM_MOVE: 40907,
  CONFIRM_MISMATCH: 40908,
  FILE_TOO_LARGE: 41301,
  BATCH_TOO_LARGE: 41302,
  UNSUPPORTED_MEDIA_TYPE: 41501,
  INTERNAL_ERROR: 50001,
  STORAGE_WRITE_FAILED: 50002,
} as const

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode]
