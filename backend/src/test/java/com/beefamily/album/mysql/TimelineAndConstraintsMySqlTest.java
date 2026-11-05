package com.beefamily.album.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 必须连真实 MySQL 的用例：原生 {@code UNION ALL} 时间轴排序 / 分页、唯一约束、外键 ON DELETE 行为。
 *
 * <p><b>默认不执行</b>（{@code pom.xml} 里 surefire 配了 {@code <excludedGroups>mysql</excludedGroups>}），
 * 因此 {@code mvn test} 可以在没有数据库的机器上跑通。显式运行：</p>
 *
 * <pre>
 * cd backend
 * DB_URL='jdbc:mysql://localhost:3306/bee_family?useUnicode=true&amp;characterEncoding=utf8&amp;serverTimezone=Asia/Shanghai&amp;allowPublicKeyRetrieval=true&amp;useSSL=false' \
 * DB_USERNAME=root DB_PASSWORD=&lt;你的口令&gt; \
 * mvn test -Dgroups=mysql -DexcludedGroups=
 * </pre>
 *
 * <p>数据隔离：所有行都挂在本次运行新建的 {@code family} 下，{@link #tearDown()} 只删这些行，
 * 绝不触碰库里既有数据。</p>
 */
@Tag("mysql")
class TimelineAndConstraintsMySqlTest {

    /** 迁移脚本里的时间轴排序键 SQL（与 {@code TimelineQueryRepository#fetchKeyRows} 逐字一致）。 */
    private static final String KEYS_SQL = """
            SELECT item_type, item_id, sort_time FROM (
              SELECT 'photo' AS item_type, 0 AS type_ord, p.id AS item_id, p.taken_at AS sort_time
                FROM photo p
               WHERE p.family_id = ?
              UNION ALL
              SELECT 'milestone', 1, m.id, CAST(m.achieved_at AS DATETIME)
                FROM milestone m
               WHERE m.family_id = ?
              UNION ALL
              SELECT 'growth', 2, g.id, CAST(g.recorded_at AS DATETIME)
                FROM growth_record g
                JOIN baby b ON b.id = g.baby_id
               WHERE b.family_id = ?
            ) t
            ORDER BY t.sort_time DESC, t.type_ord ASC, t.item_id DESC
            LIMIT ? OFFSET ?
            """;

    /** 三源总数 SQL（与 {@code countAll} 一致）。 */
    private static final String COUNT_SQL = """
            SELECT (SELECT COUNT(*) FROM photo p WHERE p.family_id = ?)
                 + (SELECT COUNT(*) FROM milestone m WHERE m.family_id = ?)
                 + (SELECT COUNT(*) FROM growth_record g
                      JOIN baby b ON b.id = g.baby_id
                     WHERE b.family_id = ?) AS total
            """;

    private Connection connection;

    private long userId;

    private long familyId;

    private long babyId;

    private long albumId;

    private final List<Long> photoIds = new ArrayList<>();

    /**
     * 打开连接并建一套隔离夹具。
     *
     * @throws SQLException 数据库不可用
     */
    @BeforeEach
    void setUp() throws SQLException {
        String url = env("DB_URL",
                "jdbc:mysql://localhost:3306/bee_family?useUnicode=true&characterEncoding=utf8"
                        + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false");
        String user = env("DB_USERNAME", "root");
        String password = env("DB_PASSWORD", "");
        try {
            connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException ex) {
            assumeTrue(false, "跳过：无法连接 MySQL（" + ex.getMessage() + "）");
        }
        connection.setAutoCommit(true);
        createFixture();
    }

    /**
     * 清理本次夹具并关闭连接。
     *
     * @throws SQLException 清理失败
     */
    @AfterEach
    void tearDown() throws SQLException {
        if (connection == null) {
            return;
        }
        try {
            // 先删家庭（级联 family_member / baby / album / photo / milestone / tag / growth_record）
            execute("delete from family where id = ?", familyId);
            execute("delete from `user` where id = ?", userId);
        } finally {
            connection.close();
        }
    }

    /**
     * 读取环境变量。
     *
     * @param key          变量名
     * @param defaultValue 默认值
     * @return 值
     */
    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * 建立隔离夹具：1 用户 / 1 家庭 / 1 宝宝 / 1 相册 / 40 照片 / 10 里程碑 / 10 成长记录。
     *
     * @throws SQLException 写入失败
     */
    private void createFixture() throws SQLException {
        String suffix = String.valueOf(System.nanoTime());
        userId = insert("insert into `user` (email, password_hash, nickname) values (?, ?, ?)",
                "qa-" + suffix + "@home.local", "$2a$10$qatestqatestqatestqatestqatestqatestqatestqatestqatestqa", "QA");
        familyId = insert("insert into family (name, owner_id) values (?, ?)", "QA家族-" + suffix, userId);
        execute("insert into family_member (family_id, user_id, role) values (?, ?, 'owner')", familyId, userId);
        babyId = insert("insert into baby (family_id, name, gender, birthday) values (?, ?, 'female', ?)",
                familyId, "QA宝宝", LocalDate.of(2025, 8, 18));
        albumId = insert("insert into album (family_id, name, photo_count, created_by) values (?, ?, 0, ?)",
                familyId, "QA相册", userId);

        // 40 张照片：拍摄时间跨 5 个月，其中 2 张同秒、1 张与里程碑/成长记录同一天 00:00:00
        for (int i = 0; i < 40; i++) {
            LocalDateTime takenAt;
            if (i == 10 || i == 11) {
                takenAt = LocalDateTime.of(2026, 5, 11, 10, 0, 0);
            } else if (i == 12) {
                takenAt = LocalDateTime.of(2026, 5, 10, 0, 0, 0);
            } else {
                takenAt = LocalDateTime.of(2026, 1, 1, 10, 0, 0).plusDays(i * 3L);
            }
            long photoId = insert("insert into photo (family_id, album_id, storage_key, thumb_key, mime_type,"
                            + " file_size, taken_at, uploaded_by) values (?, ?, ?, ?, 'image/jpeg', 1024, ?, ?)",
                    familyId, albumId, familyId + "/qa/" + suffix + "-" + i + ".jpg",
                    familyId + "/qa/" + suffix + "-" + i + "-thumb.jpg", Timestamp.valueOf(takenAt), userId);
            photoIds.add(photoId);
        }

        // 10 个里程碑：第 0 个与 id=photos[12] 的照片同为 2026-05-10
        for (int i = 0; i < 10; i++) {
            LocalDate achievedAt = i == 0 ? LocalDate.of(2026, 5, 10) : LocalDate.of(2026, 2, 1).plusDays(i * 7L);
            execute("insert into milestone (baby_id, family_id, title, achieved_at, created_by)"
                    + " values (?, ?, ?, ?, ?)", babyId, familyId, "QA里程碑" + i, achievedAt, userId);
        }

        // 10 条成长记录：第 0 条与上面同一天（DATE 转 DATETIME 后 00:00:00，构成跨类型同秒三元组）
        for (int i = 0; i < 10; i++) {
            LocalDate recordedAt = i == 0 ? LocalDate.of(2026, 5, 10) : LocalDate.of(2026, 3, 1).plusDays(i * 5L);
            execute("insert into growth_record (baby_id, recorded_at, height_cm, created_by) values (?, ?, ?, ?)",
                    babyId, recordedAt, 60.0 + i, userId);
        }
    }

    /**
     * 插入并返回自增主键。
     *
     * @param sql    语句
     * @param params 参数
     * @return 主键
     * @throws SQLException 写入失败
     */
    private long insert(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, params);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /**
     * 执行写语句。
     *
     * @param sql    语句
     * @param params 参数
     * @throws SQLException 执行失败
     */
    private void execute(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        }
    }

    /**
     * 绑定参数。
     *
     * @param statement 语句
     * @param params    参数
     * @throws SQLException 绑定失败
     */
    private void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    /**
     * 取一页排序键。
     *
     * @param offset 偏移
     * @param limit  条数
     * @return 每项 "type#id@sortTime"
     * @throws SQLException 查询失败
     */
    private List<String> fetchKeys(int offset, int limit) throws SQLException {
        List<String> keys = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(KEYS_SQL)) {
            statement.setLong(1, familyId);
            statement.setLong(2, familyId);
            statement.setLong(3, familyId);
            statement.setInt(4, limit);
            statement.setInt(5, offset);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    keys.add(rows.getString(1) + "#" + rows.getLong(2) + "@"
                            + rows.getTimestamp(3).toLocalDateTime());
                }
            }
        }
        return keys;
    }

    /**
     * 三源总数。
     *
     * @return 总数
     * @throws SQLException 查询失败
     */
    private long countAll() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COUNT_SQL)) {
            statement.setLong(1, familyId);
            statement.setLong(2, familyId);
            statement.setLong(3, familyId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    @Test
    @DisplayName("同秒三级 tie-break：photo(0) → milestone(1) → growth(2)，同类型按 item_id DESC")
    void typeOrdBreaksTies() throws SQLException {
        List<String> all = fetchKeys(0, 1000);

        int photoIndex = indexOfType(all, "photo#" + photoIds.get(12) + "@2026-05-10T00:00");
        int milestoneIndex = indexOfType(all, "milestone#");
        int growthIndex = indexOfType(all, "growth#");

        assertThat(photoIndex).isGreaterThanOrEqualTo(0);
        assertThat(milestoneIndex).isGreaterThan(photoIndex);
        assertThat(growthIndex).isGreaterThan(milestoneIndex);

        // 同类型同秒：id 大的在前
        int laterId = indexOfType(all, "photo#" + photoIds.get(11) + "@2026-05-11T10:00");
        int earlierId = indexOfType(all, "photo#" + photoIds.get(10) + "@2026-05-11T10:00");
        assertThat(laterId).isLessThan(earlierId);
    }

    @Test
    @DisplayName("分页：逐页拼接 == 全量序，无重复、无遗漏，总数 == countAll()")
    void pagingIsPartitionOfGlobalOrder() throws SQLException {
        List<String> full = fetchKeys(0, 1000);
        assertThat(full).hasSize(60);
        assertThat(countAll()).isEqualTo(60L);

        List<String> paged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int offset = 0; offset < 60; offset += 24) {
            List<String> page = fetchKeys(offset, 24);
            assertThat(page.size()).isLessThanOrEqualTo(24);
            for (String key : page) {
                assertThat(seen.add(key)).as("翻页出现重复项: %s", key).isTrue();
            }
            paged.addAll(page);
        }

        assertThat(paged).as("逐页拼接必须与全局序完全一致（顺序漂移会导致前端重复/漏项）").containsExactlyElementsOf(full);
    }

    @Test
    @DisplayName("最大页边界：offset 越过总数返回空集（前端据此停止翻页）")
    void pageBeyondEndIsEmpty() throws SQLException {
        assertThat(fetchKeys(60, 24)).isEmpty();
        assertThat(fetchKeys(0, 24)).hasSize(24);
    }

    @Test
    @DisplayName("唯一约束 uk_growth_baby_date：同宝宝同日期第二条记录被数据库拒绝")
    void growthUniqueKeyEnforced() {
        assertThatThrownBy(() -> execute("insert into growth_record (baby_id, recorded_at, height_cm, created_by)"
                + " values (?, ?, ?, ?)", babyId, LocalDate.of(2026, 5, 10), 70.0, userId))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_growth_baby_date");
    }

    @Test
    @DisplayName("外键 ON DELETE SET NULL：删相册后照片变未分类，而不是被删")
    void deletingAlbumKeepsPhotos() throws SQLException {
        execute("delete from album where id = ?", albumId);

        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from photo where family_id = ? and album_id is null")) {
            statement.setLong(1, familyId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                assertThat(rows.getLong(1)).isEqualTo(40L);
            }
        }
        albumId = -1;
    }

    @Test
    @DisplayName("外键 ON DELETE CASCADE：删家庭后照片 / 里程碑 / 成长记录全部清除，不留孤儿行")
    void deletingFamilyCascades() throws SQLException {
        execute("delete from family where id = ?", familyId);

        assertThat(countRows("select count(*) from photo where family_id = " + familyId)).isZero();
        assertThat(countRows("select count(*) from milestone where family_id = " + familyId)).isZero();
        assertThat(countRows("select count(*) from growth_record where baby_id = " + babyId)).isZero();
        assertThat(countRows("select count(*) from album where family_id = " + familyId)).isZero();

        familyId = -1;
        babyId = -1;
        albumId = -1;
    }

    /**
     * 统计一句 count 查询。
     *
     * @param sql SQL（仅测试内部拼接的常量）
     * @return 计数
     * @throws SQLException 查询失败
     */
    private long countRows(String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    /**
     * 找到首个匹配前缀的索引。
     *
     * @param keys   列表
     * @param prefix 前缀
     * @return 下标，找不到返回 -1
     */
    private static int indexOfType(List<String> keys, String prefix) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }
}
