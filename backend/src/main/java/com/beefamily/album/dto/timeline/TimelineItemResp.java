package com.beefamily.album.dto.timeline;

import com.beefamily.album.dto.growth.GrowthRecordResp;
import com.beefamily.album.dto.milestone.MilestoneResp;
import com.beefamily.album.dto.photo.PhotoResp;
import java.time.LocalDateTime;

/**
 * 时间轴条目（三选一：photo / milestone / growth）。
 *
 * <p>不做 null 裁剪（§11.4）：非当前类型的字段显式为 null，前端用 {@code item.type} 决定渲染分支，
 * 比「字段缺失 + 可选链」更利于类型安全。</p>
 *
 * @param type     条目类型：photo / milestone / growth
 * @param id       条目 id
 * @param sortTime 排序时间（DATE 类型补 00:00:00）
 * @param photo    照片详情（type=photo 时有值）
 * @param milestone 里程碑（type=milestone 时有值）
 * @param growth   成长记录（type=growth 时有值）
 */
public record TimelineItemResp(String type, long id, LocalDateTime sortTime, PhotoResp photo,
                               MilestoneResp milestone, GrowthRecordResp growth) {
}
