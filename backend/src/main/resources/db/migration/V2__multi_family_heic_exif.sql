-- =============================================================================
-- V2__multi_family_heic_exif.sql
-- MySQL 8.4.7 / utf8mb4_0900_ai_ci / InnoDB
-- 纪律：V1__init.sql 绝不修改（Flyway checksum）；上线前必须 mysqldump 全量备份
-- 注意：MySQL DDL 隐式提交，单条失败不会回滚已成功语句；先在副本库演练
-- =============================================================================
SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- §0 迁移前置检查（只读，运维执行前单独跑；返回 0 行才允许继续）
-- -----------------------------------------------------------------------------
-- 0.1 每个家庭必须恰好 1 个 owner（否则 uk_member_single_owner 会失败）
-- SELECT f.id, COUNT(fm.id) AS owner_cnt FROM family f
--   LEFT JOIN family_member fm ON fm.family_id=f.id AND fm.role='owner'
--  GROUP BY f.id HAVING owner_cnt <> 1;
-- 0.2 旧邀请码列不得被外键/视图/触发器/生成列引用
-- SELECT * FROM information_schema.KEY_COLUMN_USAGE
--  WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_COLUMN_NAME IN ('invite_code');
-- 0.3 photo.album_id / milestone.photo_id 的既有 FK 仍在（M-6 零改动基线）

-- -----------------------------------------------------------------------------
-- §1 family：新增家庭码（4 步）+ 立即删除旧邀请码列（Q7，无过渡期）
-- -----------------------------------------------------------------------------
ALTER TABLE `family`
  ADD COLUMN `family_code` CHAR(10) NULL
  COMMENT '10 位家庭码，去混淆字符集，全局唯一，永久有效，可重置'
  AFTER `invite_code_expires_at`;

-- 为现存家庭回填 10 位码；字符集沿用 InviteCodes.ALPHABET（实测 31 字符）
SET @FC_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
UPDATE `family` SET `family_code` = CONCAT(
  SUBSTRING(@FC_ALPHABET, FLOOR(1 + RAND()*31), 1), SUBSTRING(@FC_ALPHABET, FLOOR(1 + RAND()*31), 1),
  SUBSTRING(@FC_ALPHABET, FLOOR(1 + RAND()*31), 1), SUBSTRING(@FC_ALPHABET, FLOOR(1 + RAND()*31), 1),
  SUBSTRING(@FC_ALPHABET, FLOOR(1 + RAND()*31), 1), SUBSTRING(@FC_ALPHABET, FLOOR(1 + RAND()*31), 1),
  SUBSTRING(@FC_ALPHABET, FLOOR(1 + RAND()*31), 1), SUBSTRING(@FC_ALPHABET, FLOOR(1 + RAND()*31), 1),
  SUBSTRING(@FC_ALPHABET, FLOOR(1 + RAND()*31), 1), SUBSTRING(@FC_ALPHABET, FLOOR(1 + RAND()*31), 1))
 WHERE `family_code` IS NULL;

-- 唯一性预检：若返回行，重跑上面 UPDATE 直到 0 行
-- SELECT `family_code`, COUNT(*) c FROM `family` GROUP BY `family_code` HAVING c > 1;

ALTER TABLE `family` MODIFY COLUMN `family_code` CHAR(10) NOT NULL;
ALTER TABLE `family` ADD UNIQUE KEY `uk_family_code` (`family_code`);

-- Q7：先删索引再删列（invite_code 上有 uk_family_invite_code）
ALTER TABLE `family` DROP INDEX `uk_family_invite_code`;
ALTER TABLE `family` DROP COLUMN `invite_code`, DROP COLUMN `invite_code_expires_at`;

-- -----------------------------------------------------------------------------
-- §2 family_member：解除 1 用户 = 1 家庭 + 引入 admin + 单 owner 约束
-- -----------------------------------------------------------------------------
-- 必须先补普通索引，否则 DROP uk_member_user 会因 fk_member_user 失去支撑索引报 ERROR 1553
ALTER TABLE `family_member`
  ADD KEY `idx_member_user_joined` (`user_id`, `joined_at`, `id`);
ALTER TABLE `family_member` DROP KEY `uk_member_user`;

ALTER TABLE `family_member` DROP CHECK `ck_member_role`;
ALTER TABLE `family_member`
  ADD CONSTRAINT `ck_member_role` CHECK (`role` IN ('owner','admin','member'));

-- 角色回填（幂等）：family.owner_id 指向的成员必须是 owner
UPDATE `family_member` fm
  JOIN `family` f ON f.`id` = fm.`family_id` AND f.`owner_id` = fm.`user_id`
   SET fm.`role` = 'owner'
 WHERE fm.`role` <> 'owner';

-- 每家庭至多 1 个 owner：生成列 + 唯一键（终态行 owner_flag=NULL，唯一键允许多个 NULL）
ALTER TABLE `family_member`
  ADD COLUMN `owner_flag` TINYINT
    GENERATED ALWAYS AS (CASE WHEN `role`='owner' THEN 1 ELSE NULL END) STORED,
  ADD UNIQUE KEY `uk_member_single_owner` (`family_id`, `owner_flag`);

-- -----------------------------------------------------------------------------
-- §3 user：服务端权威的当前家庭（Q8）
--   ★ 刻意不加 FK：active_family_id → family.id 会与 family.owner_id → user.id 成环，
--     与 03 文档 §4.3 cover_photo_id 同型，一致性由应用层同事务维护。
-- -----------------------------------------------------------------------------
ALTER TABLE `user`
  ADD COLUMN `active_family_id` BIGINT NULL
    COMMENT '服务端权威当前家庭；NULL=无可用家庭；无 FK，应用层维护'
    AFTER `token_valid_after`,
  ADD KEY `idx_user_active_family` (`active_family_id`);

UPDATE `user` u
   SET u.`active_family_id` = (
     SELECT fm.`family_id` FROM `family_member` fm
      WHERE fm.`user_id` = u.`id`
      ORDER BY fm.`joined_at` ASC, fm.`id` ASC LIMIT 1)
 WHERE u.`active_family_id` IS NULL;

-- -----------------------------------------------------------------------------
-- §4 family_join_request：申请状态机 + 24h 计数索引
--   idx_jr_attempt_window 服务「同一 applicant 在同一 family 近 24h 提交次数」，
--   该查询 owner 与 admin 均可发起，不按 owner 过滤（C-3）。
-- -----------------------------------------------------------------------------
CREATE TABLE `family_join_request` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
  `family_id`             BIGINT       NOT NULL,
  `family_code_snapshot`  CHAR(10)     NOT NULL COMMENT '提交时使用的码，仅审计追溯，不参与校验',
  `applicant_user_id`     BIGINT       NOT NULL,
  `status`                VARCHAR(12)  NOT NULL COMMENT 'pending/approved/rejected/withdrawn/expired',
  `message`               VARCHAR(50)      NULL,
  `reject_reason`         VARCHAR(100)     NULL,
  `attempt_count`         INT          NOT NULL DEFAULT 1 COMMENT '该用户对该家庭累计第几次申请',
  `reviewed_by`           BIGINT           NULL COMMENT '审批人 user id（owner 或 admin）',
  `reviewed_at`           DATETIME         NULL,
  `pending_flag`          TINYINT      GENERATED ALWAYS AS
                            (CASE WHEN `status`='pending' THEN 1 ELSE NULL END) STORED,
  `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at`            DATETIME     NOT NULL COMMENT '= created_at + 7 天，仅 pending 有意义',
  `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jr_one_pending` (`family_id`, `applicant_user_id`, `pending_flag`),
  KEY `idx_jr_family_status_created` (`family_id`, `status`, `created_at`),
  KEY `idx_jr_attempt_window` (`family_id`, `applicant_user_id`, `created_at`),
  KEY `idx_jr_applicant_created` (`applicant_user_id`, `created_at`),
  CONSTRAINT `fk_jr_family`    FOREIGN KEY (`family_id`)         REFERENCES `family` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_jr_applicant` FOREIGN KEY (`applicant_user_id`) REFERENCES `user`   (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_jr_status`    CHECK (`status` IN ('pending','approved','rejected','withdrawn','expired')),
  CONSTRAINT `ck_jr_attempt`   CHECK (`attempt_count` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='加入家庭申请';

-- -----------------------------------------------------------------------------
-- §5 photo_exif（EXIF 字段 + GPS 原文）与 photo 展示副本字段
--   GPS 数值真源仍在 photo.latitude/longitude（DECIMAL(9,6)，P1 地图复用）；
--   photo_exif 只存「原文字符串」与海拔，不重复存数值，避免两处漂移。
-- -----------------------------------------------------------------------------
CREATE TABLE `photo_exif` (
  `photo_id`              BIGINT       NOT NULL,
  `has_exif`              TINYINT(1)   NOT NULL DEFAULT 0,
  `exif_stripped`         TINYINT(1)   NOT NULL DEFAULT 0,
  `parse_status`          VARCHAR(12)  NOT NULL COMMENT 'ok/absent/stripped/failed',
  `taken_at_from_exif`    TINYINT(1)   NOT NULL DEFAULT 0,
  `make`                  VARCHAR(64)      NULL,
  `model`                 VARCHAR(64)      NULL,
  `lens_model`            VARCHAR(128)     NULL,
  `f_number`              VARCHAR(32)      NULL,
  `exposure_time`         VARCHAR(32)      NULL,
  `iso`                   VARCHAR(32)      NULL,
  `focal_length`          VARCHAR(32)      NULL,
  `focal_length_35mm`     VARCHAR(32)      NULL,
  `exposure_bias`         VARCHAR(32)      NULL,
  `exposure_mode`         VARCHAR(32)      NULL,
  `metering_mode`         VARCHAR(32)      NULL,
  `white_balance`         VARCHAR(32)      NULL,
  `flash`                 VARCHAR(32)      NULL,
  `orientation`           SMALLINT         NULL COMMENT '仅审计；不参与旋转计算',
  `offset_time_original`  VARCHAR(8)       NULL,
  `gps_latitude_raw`      VARCHAR(32)      NULL COMMENT '十进制度原文，不四舍五入不补零',
  `gps_longitude_raw`     VARCHAR(32)      NULL,
  `gps_altitude`          DECIMAL(9,2)     NULL,
  `original_file_name`    VARCHAR(255)     NULL,
  `software`              VARCHAR(64)      NULL,
  `modify_time`           DATETIME         NULL,
  `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`photo_id`),
  CONSTRAINT `fk_exif_photo` FOREIGN KEY (`photo_id`) REFERENCES `photo` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_exif_parse_status` CHECK (`parse_status` IN ('ok','absent','stripped','failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='照片 EXIF 拍摄参数';

-- photo：区分 original 与 display(1600px) 两副本；三档文件数不变，不新增第四档
ALTER TABLE `photo`
  ADD COLUMN `source_format`     VARCHAR(10)  NOT NULL DEFAULT 'jpeg'
      COMMENT '原始文件真实格式 jpeg/png/webp/heic' AFTER `mime_type`,
  ADD COLUMN `display_key`       VARCHAR(255)     NULL
      COMMENT '展示副本(1600px JPG)相对 key；仅 HEIC 有值' AFTER `preview_key`,
  ADD COLUMN `display_mime_type` VARCHAR(40)      NULL COMMENT '展示副本 MIME；HEIC 固定 image/jpeg',
  ADD COLUMN `display_width`     INT              NULL,
  ADD COLUMN `display_height`    INT              NULL,
  ADD COLUMN `display_file_size` BIGINT           NULL,
  MODIFY COLUMN `mime_type` VARCHAR(40) NOT NULL
      COMMENT '原始文件 MIME：image/jpeg | image/png | image/webp | image/heic',
  ADD CONSTRAINT `ck_photo_source_format`
      CHECK (`source_format` IN ('jpeg','png','webp','heic'));

-- 历史 56 张照片回填（不解析 EXIF，不回填坐标；INC-P2-01 本期不做）
UPDATE `photo` SET `source_format` = CASE `mime_type`
    WHEN 'image/png'  THEN 'png'
    WHEN 'image/webp' THEN 'webp'
    WHEN 'image/heic' THEN 'heic'
    ELSE 'jpeg' END;

-- -----------------------------------------------------------------------------
-- §6 迁移后自检（只读，应全部符合预期）
-- -----------------------------------------------------------------------------
-- SELECT COUNT(*) FROM `photo`;                             -- 56（零改动）
-- SELECT COUNT(*) FROM `photo` WHERE `source_format`='jpeg'; -- 56
-- SELECT `id`,`family_code` FROM `family`;                 -- 1 行、10 位、非空
-- SELECT `id`,`active_family_id` FROM `user`;              -- 1 行、指向 family 1
-- SELECT COUNT(*) FROM `family_join_request`;              -- 0（空表）
-- SELECT COUNT(*) FROM `photo_exif`;                       -- 0（空表）
-- photo.family_id / album.family_id / 磁盘目录 / 文件名：零改动
