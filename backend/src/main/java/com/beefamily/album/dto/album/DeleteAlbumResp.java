package com.beefamily.album.dto.album;

/**
 * 删除相册结果。
 *
 * <p>{@code photoStrategy=move} 时 {@code movedPhotos} &gt; 0、{@code deletedPhotos} = 0；反之亦然。
 * 两个字段都保留（而非一个 {@code affected}）是为了让文案能准确区分「移到未分类」与「一并删除」。</p>
 *
 * @param deletedPhotos 被一并删除的照片数
 * @param movedPhotos   被移到未分类的照片数
 */
public record DeleteAlbumResp(int deletedPhotos, int movedPhotos) {
}
