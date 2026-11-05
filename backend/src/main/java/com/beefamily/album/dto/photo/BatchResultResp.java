package com.beefamily.album.dto.photo;

/**
 * 批量操作结果。
 *
 * <p>删除接口只填 {@code deleted}、移动接口只填 {@code moved}，另一字段为 null —— 契约允许 null
 * （§11.4 全量输出 null），前端按调用的接口取对应字段。</p>
 *
 * @param deleted 删除条数
 * @param moved   移动条数
 */
public record BatchResultResp(Integer deleted, Integer moved) {
}
