-- =============================================================================
-- 蜜记 · Bee Family —— 初始表结构
-- DB: MySQL 8.4.7   charset: utf8mb4   collation: utf8mb4_0900_ai_ci   engine: InnoDB
-- 由 Flyway 执行，禁止手工修改本文件；后续变更请新增 V2__xxx.sql
--
-- 与 docs/03-architecture.md §4.4 的差异（唯一一处，属产品/架构已拍板决策）：
--   user 表新增 token_valid_after DATETIME NULL —— 落地 §12 R-3 定稿结论，
--   JWT 校验时 iat < token_valid_after 即判为失效（登出 / 改密码 / 被移除成员时写入 now()）。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. user
-- -----------------------------------------------------------------------------
CREATE TABLE `user` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `email`             VARCHAR(120) NOT NULL                COMMENT '登录邮箱，全小写存储',
  `password_hash`     VARCHAR(100) NOT NULL                COMMENT 'bcrypt cost=10，60 字符',
  `nickname`          VARCHAR(32)  NOT NULL                COMMENT '显示名',
  `avatar_url`        VARCHAR(255)     NULL                COMMENT '头像相对 key 或外链',
  `token_valid_after` DATETIME         NULL                COMMENT '该时间点之前签发的 token 一律失效（R-3）',
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户';

-- -----------------------------------------------------------------------------
-- 2. family
--    cover_photo_id 刻意不加外键（见架构文档 §4.3，方案 B），避免与 photo.family_id 成环
-- -----------------------------------------------------------------------------
CREATE TABLE `family` (
  `id`                     BIGINT      NOT NULL AUTO_INCREMENT,
  `name`                   VARCHAR(40) NOT NULL COMMENT '家庭名，1-40 字',
  `owner_id`               BIGINT      NOT NULL COMMENT '创建者 user.id',
  `invite_code`            CHAR(8)         NULL COMMENT '8 位邀请码，唯一；NULL=未生成',
  `invite_code_expires_at` DATETIME        NULL COMMENT '过期时间，now()+24h',
  `cover_photo_id`         BIGINT          NULL COMMENT '家庭封面（无 FK，应用层维护）',
  `created_at`             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_invite_code` (`invite_code`),
  KEY `idx_family_owner` (`owner_id`),
  KEY `idx_family_cover_photo` (`cover_photo_id`),
  CONSTRAINT `fk_family_owner` FOREIGN KEY (`owner_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='家庭空间';

-- -----------------------------------------------------------------------------
-- 3. family_member
--    uk_member_user 在 DB 层强制「1 用户 = 1 家庭」，同时是每请求解析 familyId 的索引
-- -----------------------------------------------------------------------------
CREATE TABLE `family_member` (
  `id`        BIGINT      NOT NULL AUTO_INCREMENT,
  `family_id` BIGINT      NOT NULL,
  `user_id`   BIGINT      NOT NULL,
  `role`      VARCHAR(10) NOT NULL COMMENT 'owner / member',
  `joined_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_family_user` (`family_id`, `user_id`),
  UNIQUE KEY `uk_member_user` (`user_id`),
  KEY `idx_member_family_role` (`family_id`, `role`),
  CONSTRAINT `fk_member_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_member_user`   FOREIGN KEY (`user_id`)   REFERENCES `user` (`id`)   ON DELETE CASCADE,
  CONSTRAINT `ck_member_role` CHECK (`role` IN ('owner', 'member'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='家庭成员';

-- -----------------------------------------------------------------------------
-- 4. baby
-- -----------------------------------------------------------------------------
CREATE TABLE `baby` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `family_id`       BIGINT       NOT NULL,
  `name`            VARCHAR(32)  NOT NULL COMMENT '昵称',
  `gender`          VARCHAR(8)       NULL COMMENT 'male / female / unknown',
  `birthday`        DATE         NOT NULL COMMENT '不得晚于今天（应用层校验）',
  `avatar_url`      VARCHAR(255)     NULL,
  `birth_height_cm` DECIMAL(5,1)     NULL COMMENT '出生身高 cm',
  `birth_weight_kg` DECIMAL(4,2)     NULL COMMENT '出生体重 kg',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_baby_family` (`family_id`, `id`),
  CONSTRAINT `fk_baby_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_baby_gender` CHECK (`gender` IS NULL OR `gender` IN ('male', 'female', 'unknown')),
  CONSTRAINT `ck_baby_birth_height` CHECK (`birth_height_cm` IS NULL OR `birth_height_cm` BETWEEN 20.0 AND 80.0),
  CONSTRAINT `ck_baby_birth_weight` CHECK (`birth_weight_kg` IS NULL OR `birth_weight_kg` BETWEEN 0.50 AND 10.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宝宝档案';

-- -----------------------------------------------------------------------------
-- 5. growth_record
--    时间轴按 family 过滤通过 JOIN baby 完成（单家庭 ≤3 个宝宝，JOIN 成本可忽略），
--    故不冗余 family_id（与 milestone 不同：milestone.baby_id 可为 NULL，必须冗余）
-- -----------------------------------------------------------------------------
CREATE TABLE `growth_record` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `baby_id`     BIGINT       NOT NULL,
  `recorded_at` DATE         NOT NULL COMMENT '测量日期',
  `height_cm`   DECIMAL(5,1)     NULL COMMENT '身高 30.0-150.0',
  `weight_kg`   DECIMAL(4,2)     NULL COMMENT '体重 1.00-60.00',
  `head_cm`     DECIMAL(4,1)     NULL COMMENT '头围 20.0-70.0',
  `note`        VARCHAR(200)     NULL,
  `created_by`  BIGINT       NOT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_growth_baby_date` (`baby_id`, `recorded_at`),
  KEY `idx_growth_baby_date_id` (`baby_id`, `recorded_at`, `id`),
  KEY `idx_growth_created_by` (`created_by`),
  CONSTRAINT `fk_growth_baby`       FOREIGN KEY (`baby_id`)    REFERENCES `baby` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_growth_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_growth_height` CHECK (`height_cm` IS NULL OR `height_cm` BETWEEN 30.0 AND 150.0),
  CONSTRAINT `ck_growth_weight` CHECK (`weight_kg` IS NULL OR `weight_kg` BETWEEN 1.00 AND 60.00),
  CONSTRAINT `ck_growth_head`   CHECK (`head_cm`   IS NULL OR `head_cm`   BETWEEN 20.0 AND 70.0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成长测量记录';

-- -----------------------------------------------------------------------------
-- 6. album
--    cover_photo_id 无 FK（见 §4.3）；photo_count 为冗余计数，由 service 在同事务内维护
-- -----------------------------------------------------------------------------
CREATE TABLE `album` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `family_id`      BIGINT       NOT NULL,
  `name`           VARCHAR(40)  NOT NULL COMMENT '1-40 字，家庭内唯一',
  `description`    VARCHAR(300)     NULL,
  `cover_photo_id` BIGINT           NULL COMMENT 'NULL = 取相册内最新照片',
  `photo_count`    INT          NOT NULL DEFAULT 0 COMMENT '冗余计数',
  `created_by`     BIGINT       NOT NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_album_family_name` (`family_id`, `name`),
  KEY `idx_album_family_updated` (`family_id`, `updated_at`),
  KEY `idx_album_cover_photo` (`cover_photo_id`),
  CONSTRAINT `fk_album_family`     FOREIGN KEY (`family_id`)  REFERENCES `family` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_album_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)   ON DELETE RESTRICT,
  CONSTRAINT `ck_album_photo_count` CHECK (`photo_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='相册';

-- -----------------------------------------------------------------------------
-- 7. photo
--    album_id 的 ON DELETE SET NULL 是「照片移到未分类」的数据库级兜底
-- -----------------------------------------------------------------------------
CREATE TABLE `photo` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `family_id`     BIGINT       NOT NULL COMMENT '权限过滤第一条件',
  `album_id`      BIGINT           NULL COMMENT 'NULL = 未分类',
  `title`         VARCHAR(80)      NULL,
  `description`   VARCHAR(500)     NULL,
  `storage_key`   VARCHAR(255) NOT NULL COMMENT '原图相对路径，如 1/2026/11/{uuid}.jpg',
  `thumb_key`     VARCHAR(255)     NULL COMMENT '480px 缩略图',
  `preview_key`   VARCHAR(255)     NULL COMMENT '1600px 预览图',
  `mime_type`     VARCHAR(40)  NOT NULL COMMENT 'image/jpeg | image/png | image/webp',
  `file_size`     BIGINT       NOT NULL COMMENT '字节，<= 20971520',
  `width`         INT              NULL,
  `height`        INT              NULL,
  `taken_at`      DATETIME     NOT NULL COMMENT 'EXIF 优先，缺失取上传时间',
  `location_name` VARCHAR(100)     NULL,
  `latitude`      DECIMAL(9,6)     NULL COMMENT 'P1 地图预留',
  `longitude`     DECIMAL(9,6)     NULL COMMENT 'P1 地图预留',
  `content_hash`  CHAR(64)         NULL COMMENT 'P2 去重预留',
  `media_type`    VARCHAR(10)  NOT NULL DEFAULT 'photo' COMMENT 'P2 视频预留',
  `uploaded_by`   BIGINT       NOT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_photo_family_taken`   (`family_id`, `taken_at`, `id`),
  KEY `idx_photo_family_created` (`family_id`, `created_at`, `id`),
  KEY `idx_photo_family_album`   (`family_id`, `album_id`, `taken_at`, `id`),
  KEY `idx_photo_album_taken`    (`album_id`, `taken_at`, `id`),
  KEY `idx_photo_uploaded_by`    (`uploaded_by`),
  CONSTRAINT `fk_photo_family`    FOREIGN KEY (`family_id`)   REFERENCES `family` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_photo_album`     FOREIGN KEY (`album_id`)    REFERENCES `album` (`id`)  ON DELETE SET NULL,
  CONSTRAINT `fk_photo_uploaded`  FOREIGN KEY (`uploaded_by`) REFERENCES `user` (`id`)   ON DELETE RESTRICT,
  CONSTRAINT `ck_photo_media_type` CHECK (`media_type` IN ('photo', 'video')),
  CONSTRAINT `ck_photo_file_size`  CHECK (`file_size` > 0 AND `file_size` <= 20971520)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='照片';

-- -----------------------------------------------------------------------------
-- 8. milestone
--    family_id 冗余：baby_id 可空（家庭级里程碑），时间轴与搜索必须能只按 family 过滤
-- -----------------------------------------------------------------------------
CREATE TABLE `milestone` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `baby_id`     BIGINT           NULL COMMENT 'NULL = 家庭级里程碑',
  `family_id`   BIGINT       NOT NULL COMMENT '冗余，权限过滤与时间轴必需',
  `title`       VARCHAR(60)  NOT NULL,
  `description` VARCHAR(500)     NULL,
  `achieved_at` DATE         NOT NULL,
  `photo_id`    BIGINT           NULL COMMENT '关联配图',
  `created_by`  BIGINT       NOT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_milestone_family_achieved` (`family_id`, `achieved_at`, `id`),
  KEY `idx_milestone_baby_achieved`   (`baby_id`, `achieved_at`),
  KEY `idx_milestone_photo`           (`photo_id`),
  CONSTRAINT `fk_milestone_family`     FOREIGN KEY (`family_id`)  REFERENCES `family` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_milestone_baby`       FOREIGN KEY (`baby_id`)    REFERENCES `baby` (`id`)   ON DELETE SET NULL,
  CONSTRAINT `fk_milestone_photo`      FOREIGN KEY (`photo_id`)   REFERENCES `photo` (`id`)  ON DELETE SET NULL,
  CONSTRAINT `fk_milestone_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)   ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='里程碑';

-- -----------------------------------------------------------------------------
-- 9. tag
-- -----------------------------------------------------------------------------
CREATE TABLE `tag` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `family_id`  BIGINT      NOT NULL,
  `name`       VARCHAR(20) NOT NULL COMMENT '家庭内唯一，复用',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_family_name` (`family_id`, `name`),
  CONSTRAINT `fk_tag_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签';

-- -----------------------------------------------------------------------------
-- 10. photo_tag
-- -----------------------------------------------------------------------------
CREATE TABLE `photo_tag` (
  `photo_id`   BIGINT   NOT NULL,
  `tag_id`     BIGINT   NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`photo_id`, `tag_id`),
  KEY `idx_photo_tag_tag` (`tag_id`, `photo_id`),
  CONSTRAINT `fk_pt_photo` FOREIGN KEY (`photo_id`) REFERENCES `photo` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pt_tag`   FOREIGN KEY (`tag_id`)   REFERENCES `tag` (`id`)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='照片-标签关联';
