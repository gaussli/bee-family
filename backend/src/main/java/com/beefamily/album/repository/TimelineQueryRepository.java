package com.beefamily.album.repository;

import com.beefamily.album.domain.Photo;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 时间轴排序键查询（项目中唯一使用 {@code UNION ALL} 的仓储）。
 *
 * <p><b>为什么必须在这一层归并</b>：照片 / 里程碑 / 成长记录是三个独立数据源，若各自取
 * {@code page} 条再在内存合并，第 2 页起会漏数据（某月只有照片时，照片源第 25–48 条必须占据
 * 第 2 页，但内存排序会把它们的位次让给里程碑）。因此全局序只能由 MySQL 保证（ADR-05，§5.7）。</p>
 *
 * <p><b>三个排序键缺一不可</b>：{@code sort_time DESC, type_ord ASC, item_id DESC}。
 * {@code type_ord} 必须是<b>整数</b>（photo=0 / milestone=1 / growth=2）：若用 {@code item_type}
 * 字符串排序，字典序是 {@code growth < milestone < photo}，与意图相反；且未来改类型名会静默改变排序。</p>
 *
 * <p>{@code CAST(DATE AS DATETIME)} 不能省：{@code achieved_at}/{@code recorded_at} 是 DATE，
 * {@code taken_at} 是 DATETIME，显式转换后三列类型一致，Java 侧才能统一映射为 {@code LocalDateTime}
 * （DATE 得到 {@code 00:00:00}）。</p>
 */
public interface TimelineQueryRepository extends Repository<Photo, Long> {

    /** 条目类型：照片。 */
    String TYPE_PHOTO = "photo";
    /** 条目类型：里程碑。 */
    String TYPE_MILESTONE = "milestone";
    /** 条目类型：成长记录。 */
    String TYPE_GROWTH = "growth";

    /**
     * 时间轴排序键。
     *
     * @param itemType 条目类型（photo / milestone / growth）
     * @param itemId   条目主键
     * @param sortTime 排序时间
     */
    record TimelineKey(String itemType, long itemId, LocalDateTime sortTime) {
    }

    /**
     * 取排序键（多取 1 条用于 hasMore 探测）。
     *
     * @param familyId 家庭 id
     * @param offset   偏移量
     * @param limit    条数（size + 1）
     * @return 每行 (item_type, item_id, sort_time)
     */
    @Query(value = """
            SELECT item_type, item_id, sort_time FROM (
              SELECT 'photo' AS item_type, 0 AS type_ord, p.id AS item_id, p.taken_at AS sort_time
                FROM photo p
               WHERE p.family_id = :familyId
              UNION ALL
              SELECT 'milestone', 1, m.id, CAST(m.achieved_at AS DATETIME)
                FROM milestone m
               WHERE m.family_id = :familyId
              UNION ALL
              SELECT 'growth', 2, g.id, CAST(g.recorded_at AS DATETIME)
                FROM growth_record g
                JOIN baby b ON b.id = g.baby_id
               WHERE b.family_id = :familyId
            ) t
            ORDER BY t.sort_time DESC, t.type_ord ASC, t.item_id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> fetchKeyRows(@Param("familyId") Long familyId,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    /**
     * 三源条目总数（一次查询三个标量子查询，避免 3 次往返）。
     *
     * @param familyId 家庭 id
     * @return 总数
     */
    @Query(value = """
            SELECT (SELECT COUNT(*) FROM photo p WHERE p.family_id = :familyId)
                 + (SELECT COUNT(*) FROM milestone m WHERE m.family_id = :familyId)
                 + (SELECT COUNT(*) FROM growth_record g
                      JOIN baby b ON b.id = g.baby_id
                     WHERE b.family_id = :familyId) AS total
            """, nativeQuery = true)
    long countAll(@Param("familyId") Long familyId);

    /**
     * 取排序键并把行数据规范化为 {@link TimelineKey}。
     *
     * <p>JDBC 驱动对 DATE→DATETIME 的转换可能返回 {@link Timestamp} 或 {@link LocalDateTime}，
     * 这里统一兼容，避免驱动版本升级导致 {@code ClassCastException}。</p>
     *
     * @param familyId 家庭 id
     * @param offset   偏移量
     * @param limit    条数
     * @return 排序键列表
     */
    default List<TimelineKey> fetchKeys(Long familyId, int offset, int limit) {
        List<Object[]> rows = fetchKeyRows(familyId, offset, limit);
        List<TimelineKey> keys = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String itemType = String.valueOf(row[0]);
            long itemId = ((Number) row[1]).longValue();
            keys.add(new TimelineKey(itemType, itemId, toLocalDateTime(row[2])));
        }
        return keys;
    }

    /**
     * 把驱动返回的时间对象统一为 {@link LocalDateTime}。
     *
     * @param value 原始列值
     * @return 本地时间
     */
    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        if (value instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), com.beefamily.album.support.DateTimes.ZONE);
        }
        throw new IllegalStateException("不支持的时间列类型: " + (value == null ? "null" : value.getClass().getName()));
    }
}
