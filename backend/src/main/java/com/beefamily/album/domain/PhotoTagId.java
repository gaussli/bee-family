package com.beefamily.album.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 照片-标签联合主键。
 *
 * <p>{@code @EmbeddedId} 而非自增 id：DDL 的 PK 就是 {@code (photo_id, tag_id)}，
 * 用联合主键可以让「重复打标签」由 DB 直接拒绝，service 的差分逻辑无需再加去重查询。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class PhotoTagId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "photo_id", nullable = false)
    private Long photoId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PhotoTagId that)) {
            return false;
        }
        return Objects.equals(photoId, that.photoId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(photoId, tagId);
    }
}
