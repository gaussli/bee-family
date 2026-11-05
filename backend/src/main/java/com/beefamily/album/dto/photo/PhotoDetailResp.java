package com.beefamily.album.dto.photo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 照片详情响应 = {@link PhotoResp} 全部字段 + 原图 key 与地理坐标（P1 预留）。
 *
 * <p>与前端 {@code PhotoDetail} interface 逐字段对齐（§9.6）。原图 key 只在详情返回，
 * 网格/时间轴永远用 thumbKey，避免误把 20MB 原图塞进列表（§11.7「图片尺寸」）。</p>
 *
 * @param id             照片 id
 * @param albumId        相册 id
 * @param albumName      相册名
 * @param title          标题
 * @param description    描述
 * @param thumbKey       缩略图 key
 * @param previewKey     预览图 key
 * @param mimeType       MIME
 * @param fileSize       字节数
 * @param width          宽
 * @param height         高
 * @param takenAt        拍摄时间
 * @param locationName   地点
 * @param tags           标签名列表
 * @param createdAt      上传时间
 * @param uploadedBy     上传者 id
 * @param uploadedByName 上传者昵称
 * @param storageKey     原图 key
 * @param latitude       纬度（P1 预留，本期恒为 null）
 * @param longitude      经度（P1 预留，本期恒为 null）
 */
public record PhotoDetailResp(Long id, Long albumId, String albumName, String title, String description,
                              String thumbKey, String previewKey, String mimeType, long fileSize, Integer width,
                              Integer height, LocalDateTime takenAt, String locationName, List<String> tags,
                              LocalDateTime createdAt, Long uploadedBy, String uploadedByName,
                              String storageKey, BigDecimal latitude, BigDecimal longitude) {
}
