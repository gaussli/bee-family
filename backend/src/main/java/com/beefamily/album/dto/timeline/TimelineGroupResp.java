package com.beefamily.album.dto.timeline;

import java.util.List;

/**
 * 时间轴月份分组。
 *
 * <p>{@code label} 由后端生成（{@code 2026 年 10 月}），前端不拼接（§11.2）。</p>
 *
 * @param month 分组键 {@code yyyy-MM}
 * @param label 展示文案
 * @param items 组内条目（保持 SQL 全局序）
 */
public record TimelineGroupResp(String month, String label, List<TimelineItemResp> items) {
}
