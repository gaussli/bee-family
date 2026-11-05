package com.beefamily.album.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 时间工具：时区、格式化、年龄文案、月份分组。
 *
 * <p>「今天」必须走 {@link #today()}（固定 Asia/Shanghai），禁止无参 {@code LocalDate.now()}：
 * 服务可能部署在与家人不同时区的机器上，「今天」的判定会因此漂移（docs/03-architecture.md §11.2）。</p>
 */
public final class DateTimes {

    /** 项目统一时区（UTC+8，无夏令时）。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** {@code yyyy-MM-dd HH:mm:ss}（JSON 序列化与日志统一格式）。 */
    public static final DateTimeFormatter FMT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** {@code yyyy-MM-dd}。 */
    public static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 时间轴分组键 {@code yyyy-MM}。 */
    public static final DateTimeFormatter FMT_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private DateTimes() {
        // 工具类禁止实例化
    }

    /**
     * 今天（Asia/Shanghai）。
     *
     * @return 当前日期
     */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * 当前时间（Asia/Shanghai）。
     *
     * @return 当前本地时间
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    /**
     * 已出生天数。
     *
     * @param birthday 生日
     * @return 天数，生日为空或未来返回 0
     */
    public static int ageDays(LocalDate birthday) {
        if (birthday == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(birthday, today());
        return (int) Math.max(days, 0L);
    }

    /**
     * 已出生天数（指定参照日期）。
     *
     * @param birthday 生日
     * @param refDate  参照日期
     * @return 天数
     */
    public static int ageDays(LocalDate birthday, LocalDate refDate) {
        if (birthday == null || refDate == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(birthday, refDate);
        return (int) Math.max(days, 0L);
    }

    /**
     * 年龄中文文案：{@code X岁Y个月Z天}。
     *
     * <p>规则（docs T06 关键约束①）：{@code X=0} 省略「X岁」、{@code Y=0} 省略「Y个月」，天数始终保留。
     * 例：1 岁 2 个月 4 天 → {@code 1岁2个月4天}；0 岁 0 月 12 天 → {@code 12天}。</p>
     *
     * @param birthday 生日
     * @return 文案，生日为空时返回 null
     */
    public static String ageText(LocalDate birthday) {
        if (birthday == null) {
            return null;
        }
        LocalDate ref = today();
        if (birthday.isAfter(ref)) {
            return "0天";
        }
        Period period = Period.between(birthday, ref);
        StringBuilder text = new StringBuilder();
        if (period.getYears() > 0) {
            text.append(period.getYears()).append("岁");
        }
        if (period.getMonths() > 0) {
            text.append(period.getMonths()).append("个月");
        }
        text.append(period.getDays()).append("天");
        return text.toString();
    }

    /**
     * 某个日期时点的年龄文案（里程碑用：展示「达成时」而非「今天」的年龄）。
     *
     * @param birthday 生日
     * @param refDate  参照日期（例如里程碑达成日）
     * @return 文案，任一为空时返回 null
     */
    public static String ageTextAt(LocalDate birthday, LocalDate refDate) {
        if (birthday == null || refDate == null || birthday.isAfter(refDate)) {
            return null;
        }
        Period period = Period.between(birthday, refDate);
        StringBuilder text = new StringBuilder();
        if (period.getYears() > 0) {
            text.append(period.getYears()).append("岁");
        }
        if (period.getMonths() > 0) {
            text.append(period.getMonths()).append("个月");
        }
        text.append(period.getDays()).append("天");
        return text.toString();
    }

    /**
     * 某时间点所属月份键。
     *
     * @param time 时间
     * @return {@code yyyy-MM}，time 为空返回 null
     */
    public static String monthKey(LocalDateTime time) {
        return time == null ? null : time.format(FMT_MONTH);
    }

    /**
     * 月份键转展示文案。
     *
     * <p>例：{@code 2026-10} → {@code 2026 年 10 月}。由后端生成，前端不拼接（§11.2）。</p>
     *
     * @param monthKey {@code yyyy-MM}
     * @return 展示文案
     */
    public static String monthLabel(String monthKey) {
        if (monthKey == null || monthKey.length() < 7) {
            return monthKey;
        }
        try {
            int year = Integer.parseInt(monthKey.substring(0, 4));
            int month = Integer.parseInt(monthKey.substring(5, 7));
            return year + " 年 " + month + " 月";
        } catch (NumberFormatException ex) {
            return monthKey;
        }
    }

    /**
     * Instant 转本地时间。
     *
     * @param instant 时刻
     * @return 本地时间，instant 为 null 返回 null
     */
    public static LocalDateTime fromInstant(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZONE);
    }

    /**
     * 构造 {@code yyyy}/ {@code MM} 路径片段用的日期。
     *
     * @param time 时间
     * @return 日期部分
     */
    public static LocalDate toDate(LocalDateTime time) {
        return time == null ? today() : time.toLocalDate();
    }
}
