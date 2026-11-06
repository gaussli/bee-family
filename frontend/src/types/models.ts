/**
 * 领域模型：字段名、可空性与后端 dto/** 的 record 逐字段对齐（docs/07 §5）。
 * 后端 LocalDate/LocalDateTime → string（"yyyy-MM-dd" / "yyyy-MM-dd HH:mm:ss"）；BigDecimal → number。
 *
 * v1.1：家庭内角色三值、10 位家庭码、MeResp 扩展家庭列表与无家庭引导状态、
 * PhotoDetail 增加 HEIC 展示副本与 EXIF 嵌套对象。v1.0 响应里缺失的新字段一律用可选修饰，
 * 以便后端尚未升级时前端仍可正常渲染（不造 mock，只做形状归一）。
 */

/** 家庭内角色（非全局角色）：创建者 / 管理员 / 普通成员。 */
export type Role = 'owner' | 'admin' | 'member'
export type Gender = 'male' | 'female' | 'unknown'

/** 无家庭引导页三态（docs/07 §5.3.1 / docs/06 §5）。 */
export type OnboardingState = 'NEW' | 'PENDING' | 'REJECTED_OR_EXPIRED'

/** 加入申请状态机：终态永久保留。 */
export type JoinStatus = 'pending' | 'approved' | 'rejected' | 'withdrawn' | 'expired'

/** EXIF 解析结果（docs/07 §9.9）。 */
export type ParseStatus = 'ok' | 'absent' | 'stripped' | 'failed'

export interface User {
  id: number
  email: string
  nickname: string
  avatarUrl: string | null
  createdAt: string
}

export interface Family {
  id: number
  name: string
  ownerId: number
  ownerNickname: string
  memberCount: number
  memberLimit: number
  coverPhotoId: number | null
  /** 10 位去混淆字符家庭码；对所有成员可见、永久有效、仅 owner 可重置。 */
  familyCode: string
  createdAt: string
}

/** 家庭码接口返回体（GET /family/family-code、POST /family/family-code/reset）。 */
export interface FamilyCode {
  code: string
}

/** 家庭切换器 / MeResp.families[] 数据源。 */
export interface FamilyBrief {
  familyId: number
  name: string
  role: Role
  isActive: boolean
  memberCount: number
  /** 仅 owner/admin 有值，member 为 null。 */
  pendingReviewCount: number | null
}

export interface Member {
  userId: number
  nickname: string
  avatarUrl: string | null
  email: string
  role: Role
  joinedAt: string
  uploadedPhotoCount: number
}

/**
 * 待审核申请（Q9）：只回显家庭名，不含成员 / 照片 / 统计。
 * `rejectReason` 为可选扩展，后端未下发时 UI 不渲染该行。
 */
export interface PendingRequest {
  id: number
  familyName: string
  status: JoinStatus
  createdAt: string
  expiresAt: string
  attemptCount: number
  attempts24h: number
  message: string | null
  rejectReason?: string | null
}

/** GET /api/auth/me 返回体。 */
export interface MePayload {
  user: User
  activeFamilyId: number | null
  family: Family | null
  role: Role | null
  babies: Baby[]
  families: FamilyBrief[]
  onboardingState: OnboardingState
  pendingRequestCount: number
  pendingRequest: PendingRequest | null
  historyRequestCount: number
}

/** 注册 / 登录 / 切换家庭的返回体。 */
export interface TokenPayload {
  token: string
  expiresAt: string
  user: User
  activeFamilyId: number | null
  family: Family | null
  role: Role | null
  babies: Baby[]
  families: FamilyBrief[]
  onboardingState: OnboardingState
  pendingRequestCount: number
  /** v1.0 兼容字段：旧响应只带 familyId / familyName。 */
  familyId?: number | null
  familyName?: string | null
}

/** POST /api/family-code/lookup 返回体（统一 valid=false，不区分原因）。 */
export interface FamilyLookupResp {
  valid: boolean
  familyName: string | null
}

/** GET /api/join-requests/mine 返回体。 */
export interface OnboardingStateResp {
  onboardingState: OnboardingState
  pendingRequestCount: number
  pendingRequest: PendingRequest | null
  historyRequestCount: number
}

/** GET /api/family/join-requests/pending-count 返回体。 */
export interface PendingCountResp {
  count: number
}

export interface ApplicantBrief {
  userId: number
  nickname: string
  email: string
  avatarUrl: string | null
}

/** 申请行：owner 与 admin 返回体完全一致，不按 owner 过滤任何字段。 */
export interface JoinRequestResp {
  id: number
  familyId: number
  status: JoinStatus
  applicant: ApplicantBrief
  message: string | null
  rejectReason: string | null
  attemptCount: number
  attempts24h: number
  createdAt: string
  expiresAt: string
  reviewedBy: number | null
  reviewedAt: string | null
  reviewerNickname: string | null
  familyCodeSnapshot: string | null
}

/** EXIF 19 字段 + 解析状态（docs/07 §5.3.4、§9.3）。GPS 原文为 UI 展示唯一数据源。 */
export interface ExifResp {
  parseStatus: ParseStatus
  hasExif: boolean
  exifStripped: boolean
  takenAtFromExif: boolean
  make: string | null
  model: string | null
  lensModel: string | null
  fNumber: string | null
  exposureTime: string | null
  iso: string | null
  focalLength: string | null
  focalLength35mm: string | null
  exposureBias: string | null
  exposureMode: string | null
  meteringMode: string | null
  whiteBalance: string | null
  flash: string | null
  orientation: number | null
  offsetTimeOriginal: string | null
  gpsLatitudeRaw: string | null
  gpsLongitudeRaw: string | null
  gpsAltitude: number | null
  originalFileName: string | null
  software: string | null
  modifyTime: string | null
  width: number | null
  height: number | null
  fileSize: number | null
}

export interface GrowthRecord {
  id: number
  babyId: number
  babyName: string
  recordedAt: string
  heightCm: number | null
  weightKg: number | null
  headCm: number | null
  note: string | null
  createdBy: number
  createdByName: string
  createdAt: string
  updatedAt: string
}

export interface Baby {
  id: number
  name: string
  gender: Gender | null
  birthday: string
  avatarUrl: string | null
  birthHeightCm: number | null
  birthWeightKg: number | null
  ageDays: number
  ageText: string
  milestoneCount: number
  latestGrowth: GrowthRecord | null
}

export interface BabyDetail extends Baby {
  latestMilestone: Milestone | null
  growthRecordCount: number
}

export interface CurvePoint {
  recordedAt: string
  ageDays: number
  heightCm: number | null
  weightKg: number | null
  headCm: number | null
}

export type GrowthRange = '3m' | '6m' | '12m' | 'all'

export interface GrowthCurve {
  babyId: number
  babyName: string
  birthday: string
  range: string
  from: string | null
  to: string | null
  points: CurvePoint[]
  latest: {
    recordedAt: string
    heightCm: number | null
    weightKg: number | null
    headCm: number | null
  } | null
  delta: {
    heightCm: number | null
    weightKg: number | null
    headCm: number | null
  }
}

export interface Milestone {
  id: number
  babyId: number | null
  babyName: string | null
  title: string
  description: string | null
  achievedAt: string
  ageText: string | null
  photoId: number | null
  photoThumbKey: string | null
  createdBy: number
  createdByName: string
  createdAt: string
}

export interface Album {
  id: number
  name: string
  description: string | null
  coverPhotoId: number | null
  coverThumbKey: string | null
  photoCount: number
  createdBy: number
  createdByName: string
  createdAt: string
  updatedAt: string
  latestTakenAt: string | null
}

export interface AlbumDetail extends Album {
  takenFrom: string | null
  takenTo: string | null
  ownerEditable: boolean
}

export interface Unclassified {
  count: number
  coverPhotoId: number | null
  coverThumbKey: string | null
}

export interface AlbumList {
  items: Album[]
  unclassified: Unclassified
}

export interface Photo {
  id: number
  albumId: number | null
  albumName: string | null
  title: string | null
  description: string | null
  thumbKey: string | null
  previewKey: string | null
  mimeType: string
  fileSize: number
  width: number | null
  height: number | null
  takenAt: string
  locationName: string | null
  tags: string[]
  createdAt: string
  uploadedBy: number
  uploadedByName: string
  /** v1.1：原始文件真实格式 jpeg/png/webp/heic。 */
  sourceFormat?: string | null
  /** v1.1：HEIC 的 1600px JPG 展示副本句柄；非 HEIC 为 null。 */
  displayKey?: string | null
}

export interface PhotoDetail extends Photo {
  storageKey: string
  latitude: number | null
  longitude: number | null
  displayMimeType?: string | null
  displayWidth?: number | null
  displayHeight?: number | null
  displayFileSize?: number | null
  /** 从原始文件字节读取，不从 1600px 展示副本读取。 */
  exif?: ExifResp | null
}

export interface UploadFailure {
  fileName: string
  code: number
  /** 服务端逐字下发的用户可见文案，前端不做按 code 的本地映射。 */
  message: string
}

export interface PhotoUploadResult {
  batchId: string
  successCount: number
  failureCount: number
  successes: Photo[]
  failures: UploadFailure[]
}

export interface Tag {
  id: number
  name: string
  photoCount: number
  lastUsedAt: string | null
}

export interface Overview {
  photoCount: number
  albumCount: number
  milestoneCount: number
  memberCount: number
  babyCount: number
  earliestBabyAgeDays: number | null
  monthPhotoCount: number
  storageBytes: number
  storageLimitBytes: number | null
}

export interface BatchResult {
  deleted: number | null
  moved: number | null
}

export interface DeleteAlbumResult {
  deletedPhotos: number
  movedPhotos: number
}
