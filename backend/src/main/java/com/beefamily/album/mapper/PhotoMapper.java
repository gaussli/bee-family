package com.beefamily.album.mapper;

import com.beefamily.album.domain.Photo;
import com.beefamily.album.dto.photo.PhotoDetailResp;
import com.beefamily.album.dto.photo.PhotoResp;
import java.util.List;

/**
 * 照片实体 → DTO。
 *
 * <p>标签与相册名由调用方批量查询后传入：批量是为了避免每张照片 1 次标签查询、1 次相册查询的 N+1（T12 关键约束④）。</p>
 */
public final class PhotoMapper {

    private PhotoMapper() {
    }

    /**
     * 转照片响应。
     *
     * @param photo          照片实体
     * @param albumName      相册名（可为 null）
     * @param tags           标签名列表
     * @param uploadedByName 上传者昵称
     * @return 响应
     */
    public static PhotoResp toResp(Photo photo, String albumName, List<String> tags, String uploadedByName) {
        if (photo == null) {
            return null;
        }
        return new PhotoResp(photo.getId(), photo.getAlbumId(), albumName, photo.getTitle(), photo.getDescription(),
                photo.getThumbKey(), photo.getPreviewKey(), photo.getMimeType(),
                photo.getFileSize() == null ? 0L : photo.getFileSize(), photo.getWidth(), photo.getHeight(),
                photo.getTakenAt(), photo.getLocationName(), tags == null ? List.of() : tags,
                photo.getCreatedAt(), photo.getUploadedBy(), uploadedByName);
    }

    /**
     * 转照片详情响应。
     *
     * @param photo          照片实体
     * @param albumName      相册名
     * @param tags           标签名列表
     * @param uploadedByName 上传者昵称
     * @return 详情响应
     */
    public static PhotoDetailResp toDetailResp(Photo photo, String albumName, List<String> tags,
                                               String uploadedByName) {
        if (photo == null) {
            return null;
        }
        return new PhotoDetailResp(photo.getId(), photo.getAlbumId(), albumName, photo.getTitle(),
                photo.getDescription(), photo.getThumbKey(), photo.getPreviewKey(), photo.getMimeType(),
                photo.getFileSize() == null ? 0L : photo.getFileSize(), photo.getWidth(), photo.getHeight(),
                photo.getTakenAt(), photo.getLocationName(), tags == null ? List.of() : tags,
                photo.getCreatedAt(), photo.getUploadedBy(), uploadedByName, photo.getStorageKey(),
                photo.getLatitude(), photo.getLongitude());
    }
}
