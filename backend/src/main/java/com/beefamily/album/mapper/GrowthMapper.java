package com.beefamily.album.mapper;

import com.beefamily.album.domain.GrowthRecord;
import com.beefamily.album.dto.growth.CurvePointResp;
import com.beefamily.album.dto.growth.GrowthRecordResp;
import com.beefamily.album.support.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 成长记录实体 → DTO。
 *
 * <p>BigDecimal 直接透传，不做任何 {@code stripTrailingZeros} / {@code setScale}：
 * DB 的 {@code DECIMAL(4,2)} 已保证 9.80 这类 scale，破坏它会改变前端展示精度（§11.3）。</p>
 */
public final class GrowthMapper {

    private GrowthMapper() {
    }

    /**
     * 转成长记录响应。
     *
     * @param record        记录实体
     * @param babyName      宝宝昵称
     * @param createdByName 记录人昵称
     * @return 响应
     */
    public static GrowthRecordResp toResp(GrowthRecord record, String babyName, String createdByName) {
        if (record == null) {
            return null;
        }
        return new GrowthRecordResp(record.getId(), record.getBabyId(), babyName, record.getRecordedAt(),
                record.getHeightCm(), record.getWeightKg(), record.getHeadCm(), record.getNote(),
                record.getCreatedBy(), createdByName, record.getCreatedAt(), record.getUpdatedAt());
    }

    /**
     * 转曲线数据点（升序列表已由 repository 保证）。
     *
     * @param records  记录列表
     * @param birthday 生日（用于计算点上的日龄）
     * @return 数据点列表
     */
    public static List<CurvePointResp> toCurvePoints(List<GrowthRecord> records, LocalDate birthday) {
        List<CurvePointResp> points = new ArrayList<>(records.size());
        for (GrowthRecord record : records) {
            points.add(new CurvePointResp(record.getRecordedAt(),
                    DateTimes.ageDays(birthday, record.getRecordedAt()),
                    record.getHeightCm(), record.getWeightKg(), record.getHeadCm()));
        }
        return points;
    }

    /**
     * 计算增量（末项 − 倒数第二项）。
     *
     * @param latest  最新记录
     * @param previous 上一条记录（可为 null）
     * @return 增量三元组，任一缺失时为 null
     */
    public static GrowthDelta toDelta(GrowthRecord latest, GrowthRecord previous) {
        if (latest == null) {
            return new GrowthDelta(null, null, null);
        }
        return new GrowthDelta(
                minus(latest.getHeightCm(), previous == null ? null : previous.getHeightCm()),
                minus(latest.getWeightKg(), previous == null ? null : previous.getWeightKg()),
                minus(latest.getHeadCm(), previous == null ? null : previous.getHeadCm()));
    }

    /**
     * 两个 BigDecimal 相减，任一侧为空返回 null。
     *
     * @param current  当前值
     * @param baseline 基准值
     * @return 差值
     */
    private static BigDecimal minus(BigDecimal current, BigDecimal baseline) {
        if (current == null || baseline == null) {
            return null;
        }
        return current.subtract(baseline);
    }

    /**
     * 增量中间结果。
     *
     * @param heightCm 身高增量
     * @param weightKg 体重增量
     * @param headCm   头围增量
     */
    public record GrowthDelta(BigDecimal heightCm, BigDecimal weightKg, BigDecimal headCm) {
    }
}
