package com.beefamily.album.dto.photo;

import java.util.List;

/**
 * 批量上传响应。
 *
 * <p>批量语义的关键：即使部分失败也返回 200 + {@code code:0}，失败明细在 {@code failures} 里逐条给出
 * code 与 message，前端据此做单张重试（PRD P0-07）。</p>
 *
 * @param batchId      批次标识（{@code u-yyyyMMdd-6hex}，仅用于日志关联）
 * @param successCount 成功张数
 * @param failureCount 失败张数
 * @param successes    成功项
 * @param failures     失败项
 */
public record PhotoUploadResp(String batchId, int successCount, int failureCount, List<PhotoResp> successes,
                              List<UploadFailure> failures) {
}
