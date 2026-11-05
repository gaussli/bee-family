package com.beefamily.album.mapper;

import com.beefamily.album.domain.Album;
import com.beefamily.album.dto.album.AlbumDetailResp;
import com.beefamily.album.dto.album.AlbumResp;
import java.time.LocalDateTime;

/**
 * 相册实体 → DTO。
 *
 * <p>{@code coverThumbKey} / {@code latestTakenAt} 由 service 解析后传入：
 * mapper 不查库（§3.2），这样「封面兜底到最新照片」的规则只有一处（AlbumService），不会出现两份。</p>
 */
public final class AlbumMapper {

    private AlbumMapper() {
    }

    /**
     * 转相册响应。
     *
     * @param album         相册实体
     * @param coverThumbKey 封面缩略图 key
     * @param createdByName 创建者昵称
     * @param latestTakenAt 相册内最新拍摄时间
     * @return 响应
     */
    public static AlbumResp toResp(Album album, String coverThumbKey, String createdByName,
                                   LocalDateTime latestTakenAt) {
        if (album == null) {
            return null;
        }
        return new AlbumResp(album.getId(), album.getName(), album.getDescription(), album.getCoverPhotoId(),
                coverThumbKey, album.getPhotoCount() == null ? 0 : album.getPhotoCount(), album.getCreatedBy(),
                createdByName, album.getCreatedAt(), album.getUpdatedAt(), latestTakenAt);
    }

    /**
     * 转相册详情响应。
     *
     * @param album         相册实体
     * @param coverThumbKey 封面缩略图 key
     * @param createdByName 创建者昵称
     * @param latestTakenAt 最新拍摄时间
     * @param takenFrom     最早拍摄时间
     * @param takenTo       最晚拍摄时间
     * @param ownerEditable 当前用户是否可编辑
     * @return 详情响应
     */
    public static AlbumDetailResp toDetailResp(Album album, String coverThumbKey, String createdByName,
                                               LocalDateTime latestTakenAt, LocalDateTime takenFrom,
                                               LocalDateTime takenTo, boolean ownerEditable) {
        if (album == null) {
            return null;
        }
        return new AlbumDetailResp(album.getId(), album.getName(), album.getDescription(), album.getCoverPhotoId(),
                coverThumbKey, album.getPhotoCount() == null ? 0 : album.getPhotoCount(), album.getCreatedBy(),
                createdByName, album.getCreatedAt(), album.getUpdatedAt(), latestTakenAt, takenFrom, takenTo,
                ownerEditable);
    }
}
