package com.beefamily.album.dto.photo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 照片响应（列表 / 时间轴 / 上传结果通用）。
 *
 * <p>{@code tags} 是标签名集合（不是 id）：前端直接渲染 chip，修改时按「完整集合」回传（§5.3.7）。</p>
 *
 * @param id            照片 id
 * @param albumId       相册 id，null 表示未分类
 * @param albumName     相册名
 * @param title         标题
 * @param description   描述
 * @param thumbKey      480px 缩略图 key
 * @param previewKey    1600px 预览图 key
 * @param mimeType      MIME
 * @param fileSize      字节数
 * @param width         宽
 * @param height        高
 * @param takenAt       拍摄时间
 * @param locationName  地点
 * @param tags          标签名列表
 * @param createdAt     上传时间
 * @param uploadedBy    上传者 id
 * @param uploadedByName 上传者昵称
 */
public record PhotoResp(Long id, Long albumId, String albumName, String title, String description, String thumbKey,
                        String previewKey, String mimeType, long fileSize, Integer width, Integer height,
                        LocalDateTime takenAt, String locationName, List<String> tags, LocalDateTime createdAt,
                        Long uploadedBy, String uploadedByName) {
}
