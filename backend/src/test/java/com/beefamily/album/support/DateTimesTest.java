package com.beefamily.album.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DateTimes} 单元测试。
 *
 * <p>覆盖任务点 7：{@code 2025-08-18 → 2026-11-05} 的天数与「X岁Y个月Z天」文案。
 * 断言使用带 {@code refDate} 的重载，避免测试结果随「今天」漂移（这一点本身就是 §11.2 的设计意图）。</p>
 */
class DateTimesTest {

    /** 任务书指定的生日。 */
    private static final LocalDate BIRTHDAY = LocalDate.of(2025, 8, 18);

    /** 任务书指定的参照日。 */
    private static final LocalDate REF = LocalDate.of(2026, 11, 5);

    @Test
    @DisplayName("2025-08-18 到 2026-11-05 共 444 天")
    void ageDaysBetweenFixedDates() {
        assertThat(DateTimes.ageDays(BIRTHDAY, REF)).isEqualTo(444);
    }

    @Test
    @DisplayName("同一区间年龄文案为「1岁2个月18天」")
    void ageTextAtFixedDate() {
        assertThat(DateTimes.ageTextAt(BIRTHDAY, REF)).isEqualTo("1岁2个月18天");
    }

    @Test
    @DisplayName("不满 1 岁省略「X岁」；整岁当天仍输出「0天」")
    void ageTextOmitsZeroParts() {
        assertThat(DateTimes.ageTextAt(LocalDate.of(2026, 7, 20), REF)).isEqualTo("3个月16天");
        assertThat(DateTimes.ageTextAt(LocalDate.of(2025, 11, 5), REF)).isEqualTo("1岁0天");
    }

    @Test
    @DisplayName("生日晚于参照日 → ageTextAt 返回 null，ageDays 返回 0（不抛异常）")
    void futureBirthdayIsSafe() {
        assertThat(DateTimes.ageTextAt(LocalDate.of(2026, 12, 1), REF)).isNull();
        assertThat(DateTimes.ageDays(LocalDate.of(2026, 12, 1), REF)).isZero();
        assertThat(DateTimes.ageDays(null, REF)).isZero();
        assertThat(DateTimes.ageDays(BIRTHDAY, null)).isZero();
        assertThat(DateTimes.ageText(null)).isNull();
    }

    @Test
    @DisplayName("ageText 基于「今天」且格式为 X岁Y个月Z天 的合法组合")
    void ageTextUsesToday() {
        assertThat(DateTimes.ageText(DateTimes.today())).isEqualTo("0天");
        assertThat(DateTimes.ageText(BIRTHDAY)).matches("(\\d+岁)?(\\d+个月)?\\d+天");
    }

    @Test
    @DisplayName("月份键与展示文案：2026-10 → 2026 年 10 月")
    void monthKeyAndLabel() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 5, 13, 4, 5);
        assertThat(DateTimes.monthKey(time)).isEqualTo("2026-10");
        assertThat(DateTimes.monthLabel("2026-10")).isEqualTo("2026 年 10 月");
        assertThat(DateTimes.monthKey(null)).isNull();
    }

    @Test
    @DisplayName("非法月份键原样返回（不抛异常，避免一条脏数据打挂整个时间轴）")
    void monthLabelIsDefensive() {
        assertThat(DateTimes.monthLabel(null)).isNull();
        assertThat(DateTimes.monthLabel("2026")).isEqualTo("2026");
        assertThat(DateTimes.monthLabel("abcd-ef")).isEqualTo("abcd-ef");
    }

    @Test
    @DisplayName("时间格式：yyyy-MM-dd HH:mm:ss / yyyy-MM-dd")
    void formatters() {
        LocalDateTime time = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        assertThat(time.format(DateTimes.FMT_DATE_TIME)).isEqualTo("2026-01-02 03:04:05");
        assertThat(time.format(DateTimes.FMT_DATE)).isEqualTo("2026-01-02");
    }

    @Test
    @DisplayName("Instant 按 Asia/Shanghai 转换（UTC+8）")
    void fromInstantUsesShanghai() {
        // 2026-01-01T00:00:00Z == 2026-01-01 08:00:00 +08:00
        Instant instant = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(DateTimes.fromInstant(instant)).isEqualTo(LocalDateTime.of(2026, 1, 1, 8, 0));
        assertThat(DateTimes.fromInstant(null)).isNull();
    }

    @Test
    @DisplayName("toDate: null 回退到今天，其余取日期部分（路径片段用）")
    void toDate() {
        assertThat(DateTimes.toDate(LocalDateTime.of(2026, 3, 4, 5, 6))).isEqualTo(LocalDate.of(2026, 3, 4));
        assertThat(DateTimes.toDate(null)).isEqualTo(DateTimes.today());
    }
}
