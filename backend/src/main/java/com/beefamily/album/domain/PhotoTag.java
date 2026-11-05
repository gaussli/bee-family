package com.beefamily.album.domain;

import com.beefamily.album.support.DateTimes;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 照片-标签关联。
 *
 * <p>联合主键见 {@link PhotoTagId}。{@code createdAt} 用于「最近使用标签」排序（PRD P0-11 初始态）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "photo_tag")
public class PhotoTag {

    @EmbeddedId
    private PhotoTagId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = DateTimes.now();
        }
    }
}
