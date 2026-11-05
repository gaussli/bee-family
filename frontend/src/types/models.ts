/**
 * 领域模型：字段名、可空性与后端 dto/** 的 record 逐字段对齐（docs §9.6）。
 * 后端 LocalDate/LocalDateTime → string（"yyyy-MM-dd" / "yyyy-MM-dd HH:mm:ss"）；BigDecimal → number。
 */

export type Role = 'owner' | 'member'
export type Gender = 'male' | 'female' | 'unknown'

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
  /** 仅 owner 可见，member 为 null */
  inviteCode: string | null
  inviteCodeExpiresAt: string | null
  createdAt: string
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

export interface InviteCode {
  code: string
  expiresAt: string
  expired: boolean
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
}

export interface PhotoDetail extends Photo {
  storageKey: string
  latitude: number | null
  longitude: number | null
}

export interface UploadFailure {
  fileName: string
  code: number
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

export interface TokenPayload {
  token: string
  expiresAt: string
  user: User
  familyId: number | null
  familyName: string | null
  role: Role | null
  babies: Baby[]
}

export interface MePayload {
  user: User
  family: Family | null
  role: Role | null
  babies: Baby[]
}

export interface BatchResult {
  deleted: number | null
  moved: number | null
}

export interface DeleteAlbumResult {
  deletedPhotos: number
  movedPhotos: number
}
