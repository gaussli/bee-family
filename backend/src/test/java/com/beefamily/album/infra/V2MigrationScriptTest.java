package com.beefamily.album.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * V2 迁移脚本（docs/07 附录 A 定稿 SQL）的结构验收。
 *
 * <p>演练在独立库上做，这里用「逐字断言关键 DDL 片段」守住脚本不被误改：
 * 12 表齐备、Q7 删列/删索引、多对多解除、无 FK 环、EXIF / display 字段、24h 计数索引。</p>
 */
class V2MigrationScriptTest {

    private static String sql;

    @BeforeAll
    static void loadScript() throws IOException {
        try (InputStream in = V2MigrationScriptTest.class
                .getResourceAsStream("/db/migration/V2__multi_family_heic_exif.sql")) {
            assertThat(in).as("V2 迁移脚本必须在 classpath 上").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest(name = "V2 必须包含：{0}")
    @ValueSource(strings = {
            "CREATE TABLE `family_join_request`",
            "CREATE TABLE `photo_exif`",
            "ADD COLUMN `family_code` CHAR(10) NULL",
            "MODIFY COLUMN `family_code` CHAR(10) NOT NULL",
            "ADD UNIQUE KEY `uk_family_code` (`family_code`)",
            "ALTER TABLE `family` DROP INDEX `uk_family_invite_code`;",
            "DROP COLUMN `invite_code`, DROP COLUMN `invite_code_expires_at`;",
            "ADD KEY `idx_member_user_joined` (`user_id`, `joined_at`, `id`);",
            "ALTER TABLE `family_member` DROP KEY `uk_member_user`;",
            "ADD CONSTRAINT `ck_member_role` CHECK (`role` IN ('owner','admin','member'));",
            "ADD COLUMN `owner_flag` TINYINT",
            "ADD UNIQUE KEY `uk_member_single_owner` (`family_id`, `owner_flag`);",
            "ADD COLUMN `active_family_id` BIGINT NULL",
            "ADD KEY `idx_user_active_family` (`active_family_id`);",
            "KEY `idx_jr_attempt_window` (`family_id`, `applicant_user_id`, `created_at`),",
            "UNIQUE KEY `uk_jr_one_pending`",
            "CONSTRAINT `fk_jr_family`    FOREIGN KEY",
            "CONSTRAINT `fk_jr_applicant` FOREIGN KEY",
            "CONSTRAINT `ck_jr_status`    CHECK",
            "CONSTRAINT `ck_jr_attempt`   CHECK",
            "ADD COLUMN `source_format`     VARCHAR(10)  NOT NULL DEFAULT 'jpeg'",
            "ADD COLUMN `display_key`       VARCHAR(255)     NULL",
            "ADD COLUMN `display_mime_type` VARCHAR(40)      NULL",
            "ADD COLUMN `display_width`     INT              NULL,",
            "ADD COLUMN `display_height`    INT              NULL,",
            "ADD COLUMN `display_file_size` BIGINT           NULL,",
            "ADD CONSTRAINT `ck_photo_source_format`",
            "CONSTRAINT `fk_exif_photo` FOREIGN KEY (`photo_id`) REFERENCES `photo` (`id`) ON DELETE CASCADE,",
            "CONSTRAINT `ck_exif_parse_status` CHECK"
    })
    @DisplayName("Q7 删列 / 多家庭 / 家庭码 / 申请 / EXIF / display 字段的关键 DDL 齐备")
    void containsRequiredDdl(String fragment) {
        assertThat(sql).contains(fragment);
    }

    @ParameterizedTest(name = "photo_exif 必须包含字段：{0}")
    @ValueSource(strings = {
            "`has_exif`", "`exif_stripped`", "`parse_status`", "`taken_at_from_exif`",
            "`make`", "`model`", "`lens_model`", "`f_number`", "`exposure_time`", "`iso`",
            "`focal_length`", "`focal_length_35mm`", "`exposure_bias`", "`exposure_mode`",
            "`metering_mode`", "`white_balance`", "`flash`", "`orientation`",
            "`offset_time_original`", "`gps_latitude_raw`", "`gps_longitude_raw`",
            "`gps_altitude`", "`original_file_name`", "`software`", "`modify_time`"
    })
    @DisplayName("photo_exif 的 EXIF / GPS 原文 / 海拔字段齐备（§4.5）")
    void containsExifColumns(String column) {
        assertThat(sql).contains(column);
    }

    @Test
    @DisplayName("user.active_family_id 刻意不加外键，避免与 family.owner_id 成环（§4.4）")
    void noForeignKeyRingOnActiveFamilyId() {
        assertThat(sql).doesNotContain("FOREIGN KEY (`active_family_id`)");
        assertThat(sql).contains("刻意不加 FK");
    }

    @Test
    @DisplayName("DDL 顺序：先补普通索引再 DROP uk_member_user，避开 ERROR 1553")
    void indexBeforeDropUniqueKey() {
        int addIndex = sql.indexOf("ADD KEY `idx_member_user_joined`");
        int dropKey = sql.indexOf("DROP KEY `uk_member_user`");
        assertThat(addIndex).isPositive();
        assertThat(dropKey).isGreaterThan(addIndex);
    }

    @Test
    @DisplayName("family_code 先回填再收紧 NOT NULL + UNIQUE（M-2 顺序）")
    void familyCodeBackfillBeforeConstraint() {
        int backfill = sql.indexOf("UPDATE `family` SET `family_code`");
        int notNull = sql.indexOf("MODIFY COLUMN `family_code` CHAR(10) NOT NULL");
        int unique = sql.indexOf("ADD UNIQUE KEY `uk_family_code`");
        assertThat(backfill).isPositive();
        assertThat(notNull).isGreaterThan(backfill);
        assertThat(unique).isGreaterThan(notNull);
    }

    @Test
    @DisplayName("Q7 先删索引再删列，且两列同批 DROP")
    void dropIndexBeforeColumns() {
        int dropIndex = sql.indexOf("DROP INDEX `uk_family_invite_code`");
        int dropColumn = sql.indexOf("DROP COLUMN `invite_code`, DROP COLUMN `invite_code_expires_at`");
        assertThat(dropIndex).isPositive();
        assertThat(dropColumn).isGreaterThan(dropIndex);
    }

    @Test
    @DisplayName("脚本不出现全站文案禁用措辞与 Nokia 样本")
    void noBannedWordingOrThirdPartySamples() {
        assertThat(sql).doesNotContain("兼容性最佳").doesNotContain("仅 macOS").doesNotContain("nokia");
    }

    @Test
    @DisplayName("V1 基线脚本保持 10 张业务表，V2 只新增 2 张 → 共 12 表")
    void twelveTablesInTotal() throws IOException {
        String v1;
        try (InputStream in = V2MigrationScriptTest.class
                .getResourceAsStream("/db/migration/V1__init.sql")) {
            assertThat(in).as("V1 迁移脚本必须在 classpath 上").isNotNull();
            v1 = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(countOccurrences(v1, "CREATE TABLE")).isEqualTo(10);
        assertThat(countOccurrences(sql, "CREATE TABLE")).isEqualTo(2);
    }

    /**
     * 统计子串出现次数。
     *
     * @param text   文本
     * @param needle 子串
     * @return 次数
     */
    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }
}
